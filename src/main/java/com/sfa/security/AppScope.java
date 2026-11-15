package com.sfa.security;

import com.sfa.entity.Role;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Which roles each app may act as (V119, one account / many roles). A session signed in from an
 * app carries it in the token ("app" claim) and can only ever act as these roles — enforced on
 * every request, not just at sign-in:
 * <ul>
 *   <li>MOBILE — the sales rep app: SALES_REP only;</li>
 *   <li>CUSTOMER — the public website and customer app: a buyer (CUSTOMER), or a plain account
 *       with no business privileges;</li>
 *   <li>ADMIN — the business panel: roles a business granted, plus the platform accounts.</li>
 * </ul>
 * Tokens without an app claim come from older app builds and keep the previous behaviour.
 */
public final class AppScope {

    private AppScope() {}

    public static final String MOBILE = "MOBILE";
    public static final String CUSTOMER = "CUSTOMER";
    public static final String ADMIN = "ADMIN";

    /** Role of a signed-in account that isn't acting as any business role. Grants nothing. */
    public static final String NO_ROLE = "ACCOUNT";

    private static final Map<String, Set<String>> ROLES = Map.of(
            MOBILE,   Set.of(Role.SALES_REP),
            CUSTOMER, Set.of(Role.CUSTOMER),
            ADMIN,    Set.of(Role.ADMIN, Role.SALES_MANAGER, Role.FINANCE_USER, "CASHIER", Role.DISTRIBUTOR));

    /** Normalised app name, or null when unknown / not given (older clients). */
    public static String normalize(String app) {
        if (app == null || app.isBlank()) return null;
        String a = app.trim().toUpperCase(Locale.ROOT);
        return ROLES.containsKey(a) ? a : null;
    }

    /** May a session from {@code app} act as {@code roleName}? Always true for older clients (no app). */
    public static boolean allows(String app, String roleName) {
        return app == null || ROLES.get(app).contains(roleName);
    }

    public static Set<String> rolesFor(String app) {
        return app == null ? null : ROLES.get(app);
    }
}
