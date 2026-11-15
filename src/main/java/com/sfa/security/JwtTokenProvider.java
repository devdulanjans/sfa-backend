package com.sfa.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class JwtTokenProvider {

    private final SecretKey key;
    private final long accessTokenExpiry;
    private final long refreshTokenExpiry;
    private final StringRedisTemplate redis;

    // In-memory fallback used when Redis is unavailable (dev only)
    private final Map<String, String> localBlacklist    = new ConcurrentHashMap<>();
    private final Map<String, String> localRefreshStore = new ConcurrentHashMap<>();

    private static final String BLACKLIST_PREFIX = "jwt:blacklist:";
    private static final String REFRESH_PREFIX   = "jwt:refresh:";

    public JwtTokenProvider(
            @Value("${app.jwt.secret}") String secret,
            @Value("${app.jwt.access-token-expiry-ms}") long accessTokenExpiry,
            @Value("${app.jwt.refresh-token-expiry-ms}") long refreshTokenExpiry,
            StringRedisTemplate redis) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpiry  = accessTokenExpiry;
        this.refreshTokenExpiry = refreshTokenExpiry;
        this.redis = redis;
    }

    /**
     * @param tenantId the active channel for this session; null means either
     *                 unscoped (SUPER_ADMIN/PLATFORM_OWNER) or "not yet chosen"
     *                 for a multi-channel user — TenantContext tells them apart
     *                 via the role, not the token.
     */
    public String generateAccessToken(Authentication auth, UUID tenantId) {
        return generateAccessToken(auth, tenantId, null);
    }

    /** @param membershipId the membership this session acts as (V119), carried as "mid". */
    public String generateAccessToken(Authentication auth, UUID tenantId, UUID membershipId) {
        return generateAccessToken(auth, tenantId, membershipId, null);
    }

    /** @param app the app this session belongs to (AppScope) — limits which roles it may act as. */
    public String generateAccessToken(Authentication auth, UUID tenantId, UUID membershipId, String app) {
        UserDetails user = (UserDetails) auth.getPrincipal();
        return buildToken(user.getUsername(), "ACCESS", accessTokenExpiry, tenantId, membershipId, app);
    }

    /** Carries the same "tid" claim as the access token so a later /refresh can
     *  restore whichever channel the user was actively working in — without it,
     *  refreshing would silently reset a manually-switched session back to default. */
    public String generateRefreshToken(String username, UUID tenantId) {
        return generateRefreshToken(username, tenantId, null);
    }

    public String generateRefreshToken(String username, UUID tenantId, UUID membershipId) {
        return generateRefreshToken(username, tenantId, membershipId, null);
    }

    public String generateRefreshToken(String username, UUID tenantId, UUID membershipId, String app) {
        String token = buildToken(username, "REFRESH", refreshTokenExpiry, tenantId, membershipId, app);
        String key = refreshKey(username, membershipId);
        try {
            redis.opsForValue().set(key, token, refreshTokenExpiry, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Redis unavailable, storing refresh token in memory: {}", e.getMessage());
            localRefreshStore.put(key, token);
        }
        return token;
    }

    /**
     * One refresh token per account PER WORKSPACE (V119): the same person signed into the rep
     * app and the customer app must not sign each other out. Sessions without a workspace
     * (platform accounts, legacy) keep the original per-account key.
     */
    private static String refreshKey(String username, UUID membershipId) {
        return membershipId == null ? REFRESH_PREFIX + username : REFRESH_PREFIX + username + ":" + membershipId;
    }

    private String buildToken(String subject, String tokenType, long expiry, UUID tenantId, UUID membershipId,
                              String app) {
        Date now = new Date();
        var builder = Jwts.builder()
                .subject(subject)
                .claim("type", tokenType)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiry));
        if (tenantId != null) {
            builder.claim("tid", tenantId.toString());
        }
        if (membershipId != null) {
            builder.claim("mid", membershipId.toString());
        }
        if (app != null) {
            builder.claim("app", app);
        }
        return builder.signWith(key).compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /** The active channel claim ("tid") on an access token — null if unscoped or unset. */
    public UUID getTenantIdFromToken(String token) {
        String tid = parseClaims(token).get("tid", String.class);
        return tid != null ? UUID.fromString(tid) : null;
    }

    /** The membership claim ("mid") - null for platform accounts and tokens issued before V119. */
    public UUID getMembershipIdFromToken(String token) {
        String mid = parseClaims(token).get("mid", String.class);
        return mid != null ? UUID.fromString(mid) : null;
    }

    /** The app claim (AppScope) — null for tokens from older app builds. */
    public String getAppFromToken(String token) {
        return AppScope.normalize(parseClaims(token).get("app", String.class));
    }

    public boolean validateToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (isBlacklisted(claims.getId())) return false;
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }

    public boolean validateRefreshToken(String token) {
        try {
            Claims claims = parseClaims(token);
            if (!"REFRESH".equals(claims.get("type", String.class))) return false;
            if (isBlacklisted(claims.getId())) return false;
            String username = claims.getSubject();
            String mid = claims.get("mid", String.class);
            String key = refreshKey(username, mid != null ? UUID.fromString(mid) : null);
            String stored = null;
            try {
                stored = redis.opsForValue().get(key);
            } catch (Exception e) {
                stored = localRefreshStore.get(key);
            }
            return token.equals(stored);
        } catch (JwtException | IllegalArgumentException e) {
            log.debug("Invalid refresh token: {}", e.getMessage());
            return false;
        }
    }

    public void blacklistToken(String token) {
        try {
            Claims claims = parseClaims(token);
            long remaining = claims.getExpiration().getTime() - System.currentTimeMillis();
            if (remaining > 0) {
                try {
                    redis.opsForValue().set(BLACKLIST_PREFIX + claims.getId(), "1", remaining, TimeUnit.MILLISECONDS);
                } catch (Exception redisEx) {
                    log.warn("Redis unavailable, blacklisting token in memory: {}", redisEx.getMessage());
                    localBlacklist.put(BLACKLIST_PREFIX + claims.getId(), "1");
                }
            }
        } catch (JwtException e) {
            log.warn("Failed to blacklist token: {}", e.getMessage());
        }
    }

    public void invalidateRefreshToken(String username) {
        invalidateRefreshToken(username, null);
    }

    /** Signs out one workspace session (logout from one app leaves the others signed in). */
    public void invalidateRefreshToken(String username, UUID membershipId) {
        String key = refreshKey(username, membershipId);
        try {
            redis.delete(key);
        } catch (Exception e) {
            log.warn("Redis unavailable, removing refresh token from memory: {}", e.getMessage());
            localRefreshStore.remove(key);
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }

    private boolean isBlacklisted(String jti) {
        try {
            return Boolean.TRUE.equals(redis.hasKey(BLACKLIST_PREFIX + jti));
        } catch (Exception e) {
            return localBlacklist.containsKey(BLACKLIST_PREFIX + jti);
        }
    }
}
