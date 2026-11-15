package com.sfa.service;

import com.sfa.dto.auth.LoginRequest;
import com.sfa.dto.auth.LoginResponse;
import com.sfa.dto.auth.RefreshRequest;
import com.sfa.dto.distributor.DistributorDto;
import com.sfa.dto.membership.MembershipDto;
import com.sfa.dto.tenant.TenantDto;
import com.sfa.entity.Membership;
import com.sfa.entity.Role;
import com.sfa.entity.Tenant;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.repository.DistributorRepository;
import com.sfa.repository.TenantRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.AppScope;
import com.sfa.security.JwtTokenProvider;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sign-in. One account can hold many workspaces — a role in a business (V119 memberships) —
 * and a session runs as exactly one of them: the token carries it as "mid" (plus its channel
 * as "tid"), and its role, customer link and permissions apply. SUPER_ADMIN/PLATFORM_OWNER
 * sit above every business and have no membership.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final Set<String> UNSCOPED_ROLES = Set.of(Role.SUPER_ADMIN, Role.PLATFORM_OWNER);

    private final AuthenticationManager authManager;
    private final JwtTokenProvider      jwtProvider;
    private final UserRepository        userRepo;
    private final DistributorRepository distributorRepo;
    private final TenantRepository      tenantRepo;
    private final UserPermissionService userPermissionService;
    private final LicenseService        licenseService;
    private final CustomerChannelService customerChannelService;
    private final MembershipService     membershipService;

    /**
     * Each app acts only in its own role (AppScope), whatever else the account can do:
     * the mobile app as a sales rep (refused if the account isn't one), the website/customer app
     * as a buyer (or a plain account with no privileges), the admin panel only with roles a
     * business granted (or as a platform account). Older clients send no app and keep the
     * previous behaviour.
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        String identifier = loginIdentifier(request.username());
        // Check inactive status BEFORE authenticate() so Spring Security's filter
        // cannot intercept the exception — BusinessException goes through @RestControllerAdvice cleanly.
        userRepo.findByUsernameOrEmail(identifier).ifPresent(u -> {
            if (u.getStatus() == User.UserStatus.INACTIVE) {
                throw new BusinessException("Account is inactive");
            }
        });

        authManager.authenticate(new UsernamePasswordAuthenticationToken(identifier, request.password()));

        User user = userRepo.findByUsernameOrEmail(identifier)
                .orElseThrow(() -> new BusinessException("User not found"));
        String app = AppScope.normalize(request.app());

        if (actsAsPlatform(user, app)) {
            return issue(user, null, null, app);
        }
        Membership chosen = choose(user, app, request.membershipId(), null);
        if (chosen == null && AppScope.MOBILE.equals(app)) {
            throw new BusinessException("This app is for sales reps. Your account isn't a sales rep in any business yet "
                    + "— ask the business to invite you as a sales rep.");
        }
        if (chosen == null && AppScope.ADMIN.equals(app)) {
            throw new BusinessException("Your account has no access to the business panel. To buy products, use the "
                    + "AVLIS website; invitations from businesses appear on your account page there.");
        }
        return issue(user, chosen, null, app);
    }

    @Transactional
    public LoginResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();
        if (!jwtProvider.validateRefreshToken(token)) {
            throw new BusinessException("Invalid or expired refresh token");
        }
        User user = userRepo.findByUsername(jwtProvider.getUsernameFromToken(token))
                .orElseThrow(() -> new BusinessException("User not found"));

        // Same app, same workspace (or, for SUPER_ADMIN, the channel it had entered).
        String app = jwtProvider.getAppFromToken(token);
        UUID previousTenantId = jwtProvider.getTenantIdFromToken(token);
        UUID previousMembershipId = jwtProvider.getMembershipIdFromToken(token);
        if (actsAsPlatform(user, app)) {
            return issue(user, null, previousTenantId, app);
        }
        Membership chosen = choose(user, app, previousMembershipId, previousTenantId);
        if (chosen == null && (AppScope.MOBILE.equals(app) || AppScope.ADMIN.equals(app))) {
            // The role this app needs was removed since sign-in.
            throw new BusinessException("Your access for this app was removed. Sign in again.");
        }
        return issue(user, chosen, null, app);
    }

    /**
     * Changes the channel the session operates in. SUPER_ADMIN (admin panel) may enter ANY
     * channel, or pass null to return to the platform view. Everyone else moves to one of their
     * workspaces in that channel that their app allows — the same role as now if possible.
     */
    @Transactional
    public LoginResponse switchTenant(UUID tenantId, String currentAccessToken, UUID userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new BusinessException("User not found"));
        String app = jwtProvider.getAppFromToken(currentAccessToken);
        if (Role.SUPER_ADMIN.equals(user.getRole().getName()) && actsAsPlatform(user, app)) {
            if (tenantId != null && tenantRepo.findById(tenantId).isEmpty()) {
                throw new BusinessException("Channel not found.");
            }
            jwtProvider.blacklistToken(currentAccessToken);
            return issue(user, null, tenantId, app);
        }
        if (actsAsPlatform(user, app)) {
            throw new BusinessException("This role isn't scoped to a single channel.");
        }
        if (tenantId == null) {
            throw new BusinessException("A channel is required.");
        }
        UUID currentMid = jwtProvider.getMembershipIdFromToken(currentAccessToken);
        List<Membership> allowed = allowedFor(user, app);
        String currentRole = allowed.stream().filter(m -> m.getId().equals(currentMid))
                .map(m -> m.getRole().getName()).findFirst().orElse(null);
        List<Membership> here = allowed.stream().filter(m -> m.getTenant().getId().equals(tenantId)).toList();
        Membership target = here.stream().filter(m -> m.getRole().getName().equals(currentRole)).findFirst()
                .or(() -> mostRecent(here))
                .orElseThrow(() -> new BusinessException("You don't have access to that channel."));
        jwtProvider.blacklistToken(currentAccessToken);
        return issue(user, target, null, app);
    }

    /** Switches to another workspace of the same account — only one the session's app allows. */
    @Transactional
    public LoginResponse switchMembership(UUID membershipId, String currentAccessToken, UUID userId) {
        User user = userRepo.findById(userId).orElseThrow(() -> new BusinessException("User not found"));
        String app = jwtProvider.getAppFromToken(currentAccessToken);
        Membership target = allowedFor(user, app).stream()
                .filter(m -> m.getId().equals(membershipId)).findFirst()
                .orElseThrow(() -> new BusinessException("That workspace isn't available in this app."));
        jwtProvider.blacklistToken(currentAccessToken);
        return issue(user, target, null, app);
    }

    public void logout(String accessToken, String username) {
        UUID membershipId = null;
        try {
            membershipId = jwtProvider.getMembershipIdFromToken(accessToken);
        } catch (RuntimeException ignored) {
            // an unreadable token still gets blacklisted below
        }
        jwtProvider.blacklistToken(accessToken);
        jwtProvider.invalidateRefreshToken(username, membershipId);
    }

    // ── Choosing a workspace ────────────────────────────────────────────────────

    /** Usable workspaces whose role this app may act as (every workspace for older clients). */
    private List<Membership> allowedFor(User user, String app) {
        return membershipService.usableFor(user.getId()).stream()
                .filter(m -> AppScope.allows(app, m.getRole().getName())).toList();
    }

    /**
     * Among workspaces the app allows: the requested one; else the session's previous one; else
     * the only one or the most recently used. Older clients (no app) with several workspaces
     * get their default channel's, else none — they then pick a channel.
     */
    private Membership choose(User user, String app, UUID requestedId, UUID preferredTenantId) {
        List<Membership> candidates = allowedFor(user, app);
        if (requestedId != null) {
            Optional<Membership> req = candidates.stream().filter(m -> m.getId().equals(requestedId)).findFirst();
            if (req.isPresent()) return req.get();
        }
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);
        if (preferredTenantId != null) {
            List<Membership> inTenant = candidates.stream()
                    .filter(m -> m.getTenant().getId().equals(preferredTenantId)).toList();
            if (!inTenant.isEmpty()) return mostRecent(inTenant).orElseThrow();
        }
        if (app != null) return mostRecent(candidates).orElseThrow();
        // Older customer-app builds don't say which app they are; a customer-only account was
        // never left without a channel, so keep it that way.
        if (candidates.stream().allMatch(m -> Role.CUSTOMER.equals(m.getRole().getName()))) {
            return mostRecent(candidates).orElseThrow();
        }
        // Legacy client: behave as before — default channel if set, otherwise no active channel.
        Tenant def = user.getDefaultTenant();
        return def == null ? null : mostRecent(candidates.stream()
                .filter(m -> m.getTenant().getId().equals(def.getId())).toList()).orElse(null);
    }

    private static Optional<Membership> mostRecent(List<Membership> list) {
        return list.stream().max(Comparator.comparing(
                (Membership m) -> m.getLastUsedAt() != null ? m.getLastUsedAt() : Instant.EPOCH));
    }

    /** A platform account acts as such only in the admin panel (or an older client). */
    private static boolean actsAsPlatform(User user, String app) {
        return isPlatform(user) && (app == null || AppScope.ADMIN.equals(app));
    }

    // ── Issuing the session ─────────────────────────────────────────────────────

    /**
     * @param membership     the workspace to act as — null for platform accounts, or a website
     *                       visitor's plain account (no business privileges)
     * @param platformTenant SUPER_ADMIN only: a channel it has entered (null = platform view)
     * @param app            the app (AppScope) — carried in the tokens and enforced per request
     */
    private LoginResponse issue(User user, Membership membership, UUID platformTenant, String app) {
        boolean platform = actsAsPlatform(user, app);
        // An app session not acting as any workspace has NO business role — never the account's
        // legacy users.role_id (which may be an owner/admin role from another business).
        UserDetailsImpl details = membership != null || platform || app == null
                ? new UserDetailsImpl(user, membership)
                : new UserDetailsImpl(user, null, AppScope.NO_ROLE);
        Authentication auth = new UsernamePasswordAuthenticationToken(details, null, details.getAuthorities());

        UUID tenantId = membership != null ? membership.getTenant().getId()
                : platform && platformTenant != null && tenantRepo.existsById(platformTenant) ? platformTenant : null;
        UUID membershipId = membership != null ? membership.getId() : null;
        if (membershipId != null) membershipService.markUsed(membershipId);

        String role = details.getRoleName();
        UUID customerId = details.getLinkedCustomerId();
        // Only the workspaces this app can switch to — the rep app never lists owner or customer roles.
        List<MembershipDto> workspaces = platform ? List.of() : allowedFor(user, app).stream()
                .map(MembershipDto::of).toList();

        List<TenantDto> tenants;
        if (platform) {
            tenants = tenantRepo.findAll().stream().map(TenantDto::from).toList();
        } else if (Role.CUSTOMER.equals(role)) {
            // Customer portal: reads span every channel the customer orders from.
            tenants = customerChannelService.channelsFor(user.getId(), customerId).stream().map(TenantDto::from).toList();
        } else {
            Map<UUID, TenantDto> distinct = new LinkedHashMap<>();
            allowedFor(user, app).forEach(m -> distinct.putIfAbsent(m.getTenant().getId(), TenantDto.from(m.getTenant())));
            tenants = List.copyOf(distinct.values());
        }
        TenantDto activeTenant = tenantId == null ? null
                : tenantRepo.findById(tenantId).map(TenantDto::from).orElse(null);

        List<String> permissions;
        if (platform && Role.SUPER_ADMIN.equals(user.getRole().getName())) {
            permissions = List.of("*"); // wildcard — frontend treats this as "all permissions"
        } else if (membershipId == null && app != null) {
            permissions = List.of();    // no workspace in this app → no business permissions
        } else {
            permissions = userPermissionService.getSessionPermissionKeys(user.getId(), membershipId);
        }
        List<DistributorDto> distributors = AppScope.NO_ROLE.equals(role) ? List.of()
                : distributorRepo.findByUserId(user.getId()).stream().map(DistributorDto::from).toList();

        return new LoginResponse(
                jwtProvider.generateAccessToken(auth, tenantId, membershipId, app),
                jwtProvider.generateRefreshToken(user.getUsername(), tenantId, membershipId, app),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                role,
                customerId,
                distributors,
                permissions,
                licenseService.isSfaEnabled(),
                licenseService.isPosEnabled(),
                licenseService.isFinanceEnabled(),
                licenseService.isMultiTenantEnabled(),
                tenants,
                activeTenant,
                Boolean.TRUE.equals(user.getMustChangePassword()),
                workspaces,
                membershipId,
                isPlatform(user) ? 0 : membershipService.invitationsFor(user.getId()).size());
    }

    private static boolean isPlatform(User user) {
        return UNSCOPED_ROLES.contains(user.getRole().getName());
    }

    /** Lets people sign in with their phone number in any common format (stored as 0XXXXXXXXX). */
    private String loginIdentifier(String raw) {
        String v = raw.trim();
        if (v.contains("@") || userRepo.findByUsername(v).isPresent()) return v;
        String phone = ShopService.normalizePhone(v);
        return phone != null && userRepo.existsByPhone(phone) ? phone : v;
    }
}
