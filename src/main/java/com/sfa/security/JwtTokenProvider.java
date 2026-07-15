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

    public String generateAccessToken(Authentication auth) {
        UserDetails user = (UserDetails) auth.getPrincipal();
        return buildToken(user.getUsername(), "ACCESS", accessTokenExpiry);
    }

    public String generateRefreshToken(String username) {
        String token = buildToken(username, "REFRESH", refreshTokenExpiry);
        try {
            redis.opsForValue().set(REFRESH_PREFIX + username, token, refreshTokenExpiry, TimeUnit.MILLISECONDS);
        } catch (Exception e) {
            log.warn("Redis unavailable, storing refresh token in memory: {}", e.getMessage());
            localRefreshStore.put(REFRESH_PREFIX + username, token);
        }
        return token;
    }

    private String buildToken(String subject, String tokenType, long expiry) {
        Date now = new Date();
        return Jwts.builder()
                .subject(subject)
                .claim("type", tokenType)
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + expiry))
                .signWith(key)
                .compact();
    }

    public String getUsernameFromToken(String token) {
        return parseClaims(token).getSubject();
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
            String stored = null;
            try {
                stored = redis.opsForValue().get(REFRESH_PREFIX + username);
            } catch (Exception e) {
                stored = localRefreshStore.get(REFRESH_PREFIX + username);
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
        try {
            redis.delete(REFRESH_PREFIX + username);
        } catch (Exception e) {
            log.warn("Redis unavailable, removing refresh token from memory: {}", e.getMessage());
            localRefreshStore.remove(REFRESH_PREFIX + username);
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
