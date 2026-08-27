package com.sfa.security;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.hibernate.Session;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;
import java.util.UUID;

/**
 * Binds the active channel from TenantContext into the Hibernate "tenantFilter"
 * for the current request, so every @Filter-annotated entity's queries are
 * restricted to that tenant without touching repository/service code.
 *
 * Runs as a HandlerInterceptor (after JwtAuthenticationFilter, a servlet Filter,
 * has already populated TenantContext and the security context) and relies on
 * Spring Boot's default open-in-view EntityManager being bound to the request.
 * Registered with an explicit order in WebConfig — it MUST run after Spring
 * Boot's OpenEntityManagerInViewInterceptor, or unwrap(Session.class) resolves
 * a throwaway session and enableFilter() silently does nothing.
 */
@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

    private static final String FILTER_NAME = "tenantFilter";

    /** Authenticated endpoints a pending (not-yet-selected-a-channel) user must still reach. */
    private static final Set<String> TENANT_SELECTION_EXEMPT = Set.of(
            "/api/auth/switch-tenant",
            "/api/auth/logout",
            "/api/tenants/my"
    );

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            return true; // unauthenticated request — nothing to scope, Spring Security will 401 if needed
        }

        if (TenantContext.isUnscoped()) {
            return true; // SUPER_ADMIN / PLATFORM_OWNER — sees every channel
        }

        UUID tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            if (isExempt(request.getRequestURI())) {
                return true;
            }
            response.setStatus(428); // Precondition Required
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write(
                    "{\"error\":\"TenantSelectionRequired\",\"message\":\"Select a channel before continuing.\"}");
            return false;
        }

        Session session = entityManager.unwrap(Session.class);
        session.enableFilter(FILTER_NAME).setParameter("tenantId", tenantId);
        return true;
    }

    private boolean isExempt(String requestUri) {
        return TENANT_SELECTION_EXEMPT.stream().anyMatch(requestUri::endsWith);
    }
}
