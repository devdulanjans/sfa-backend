package com.sfa.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;

    private static final java.util.Set<String> UNSCOPED_ROLES =
            java.util.Set.of("SUPER_ADMIN", "PLATFORM_OWNER");

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {
        try {
            String token = extractToken(request);
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                String username = jwtTokenProvider.getUsernameFromToken(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                var auth = new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(auth);

                // The token's "tid" claim always wins when present — that's how SUPER_ADMIN
                // "enters" a channel (AuthService.switchTenant allows it into any channel,
                // not just ones it's a member of). Only fall back to role-based unscoped
                // when there's no tid at all, i.e. SUPER_ADMIN/PLATFORM_OWNER's normal
                // platform-wide session.
                UUID tenantId = jwtTokenProvider.getTenantIdFromToken(token);
                String roleName = userDetails instanceof UserDetailsImpl impl ? impl.getRoleName() : null;
                if (tenantId != null) {
                    TenantContext.setTenant(tenantId);
                } else if (roleName != null && UNSCOPED_ROLES.contains(roleName)) {
                    TenantContext.setUnscoped();
                } else {
                    TenantContext.setTenant(null);
                }
            }
        } catch (Exception ex) {
            log.debug("JWT filter error: {}", ex.getMessage());
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    private String extractToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
