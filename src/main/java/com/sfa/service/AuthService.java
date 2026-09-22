package com.sfa.service;

import com.sfa.dto.auth.LoginRequest;
import com.sfa.dto.auth.LoginResponse;
import com.sfa.dto.auth.RefreshRequest;
import com.sfa.dto.distributor.DistributorDto;
import com.sfa.dto.tenant.TenantDto;
import com.sfa.entity.Role;
import com.sfa.entity.Tenant;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.repository.DistributorRepository;
import com.sfa.repository.TenantRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.JwtTokenProvider;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    public LoginResponse login(LoginRequest request) {
        // Check inactive status BEFORE authenticate() so Spring Security's filter
        // cannot intercept the exception — BusinessException goes through @RestControllerAdvice cleanly.
        userRepo.findByUsername(request.username()).ifPresent(u -> {
            if (u.getStatus() == User.UserStatus.INACTIVE) {
                throw new BusinessException("Account is inactive");
            }
        });

        Authentication auth = authManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));

        User user = userRepo.findByUsername(request.username())
                .orElseThrow(() -> new BusinessException("User not found"));

        TenantResolution tr = resolveTenants(user, null);
        String accessToken  = jwtProvider.generateAccessToken(auth, tr.activeTenantId());
        String refreshToken = jwtProvider.generateRefreshToken(request.username(), tr.activeTenantId());

        List<DistributorDto> distributors = distributorRepo.findByUserId(user.getId())
                .stream().map(DistributorDto::from).toList();
        List<String> permissions = resolvePermissions(user);
        UUID customerId = user.getCustomer() != null ? user.getCustomer().getId() : null;

        return new LoginResponse(
                accessToken,
                refreshToken,
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                customerId,
                distributors,
                permissions,
                licenseService.isSfaEnabled(),
                licenseService.isPosEnabled(),
                licenseService.isFinanceEnabled(),
                licenseService.isMultiTenantEnabled(),
                tr.tenants(),
                tr.activeTenant(),
                Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    public LoginResponse refresh(RefreshRequest request) {
        String token = request.refreshToken();
        if (!jwtProvider.validateRefreshToken(token)) {
            throw new BusinessException("Invalid or expired refresh token");
        }
        String username = jwtProvider.getUsernameFromToken(token);
        User user = userRepo.findByUsername(username)
                .orElseThrow(() -> new BusinessException("User not found"));

        // Preserve whichever channel was active before the access token expired —
        // otherwise a refresh would silently reset a manually-switched session to default.
        UUID previousTenantId = jwtProvider.getTenantIdFromToken(token);
        TenantResolution tr = resolveTenants(user, previousTenantId);

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        String newAccessToken  = jwtProvider.generateAccessToken(auth, tr.activeTenantId());
        String newRefreshToken = jwtProvider.generateRefreshToken(username, tr.activeTenantId());

        List<DistributorDto> distributors = distributorRepo.findByUserId(user.getId())
                .stream().map(DistributorDto::from).toList();
        List<String> permissions = resolvePermissions(user);
        UUID customerId = user.getCustomer() != null ? user.getCustomer().getId() : null;

        return new LoginResponse(
                newAccessToken,
                newRefreshToken,
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getRole().getName(),
                customerId,
                distributors,
                permissions,
                licenseService.isSfaEnabled(),
                licenseService.isPosEnabled(),
                licenseService.isFinanceEnabled(),
                licenseService.isMultiTenantEnabled(),
                tr.tenants(),
                tr.activeTenant(),
                Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    /**
     * Selects or changes which channel the current session operates in.
     * SUPER_ADMIN is special-cased: it may enter ANY channel (not just ones it's a
     * member of, since it isn't a member of any by design) to see that channel's data
     * exactly as its own users do, and may pass tenantId=null to exit back to the
     * normal unscoped platform view. Every other role must always land on a channel
     * it actually belongs to.
     */
    public LoginResponse switchTenant(UUID tenantId, String currentAccessToken, UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new BusinessException("User not found"));
        String roleName = user.getRole().getName();

        Tenant target;
        List<TenantDto> tenantsForResponse;

        if (Role.SUPER_ADMIN.equals(roleName)) {
            target = tenantId != null
                    ? tenantRepo.findById(tenantId).orElseThrow(() -> new BusinessException("Channel not found."))
                    : null;
            tenantsForResponse = tenantRepo.findAll().stream().map(TenantDto::from).toList();
        } else if (UNSCOPED_ROLES.contains(roleName)) {
            throw new BusinessException("This role isn't scoped to a single channel.");
        } else {
            if (tenantId == null) {
                throw new BusinessException("A channel is required.");
            }
            List<Tenant> userTenants = tenantRepo.findByUserId(user.getId());
            target = userTenants.stream()
                    .filter(t -> t.getId().equals(tenantId))
                    .findFirst()
                    .orElseThrow(() -> new BusinessException("You don't have access to that channel."));
            tenantsForResponse = userTenants.stream().map(TenantDto::from).toList();
        }

        jwtProvider.blacklistToken(currentAccessToken);

        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());
        UUID targetId = target != null ? target.getId() : null;
        String newAccessToken  = jwtProvider.generateAccessToken(auth, targetId);
        String newRefreshToken = jwtProvider.generateRefreshToken(user.getUsername(), targetId);

        List<DistributorDto> distributors = distributorRepo.findByUserId(user.getId())
                .stream().map(DistributorDto::from).toList();
        List<String> permissions = resolvePermissions(user);
        UUID customerId = user.getCustomer() != null ? user.getCustomer().getId() : null;

        return new LoginResponse(
                newAccessToken,
                newRefreshToken,
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                roleName,
                customerId,
                distributors,
                permissions,
                licenseService.isSfaEnabled(),
                licenseService.isPosEnabled(),
                licenseService.isFinanceEnabled(),
                licenseService.isMultiTenantEnabled(),
                tenantsForResponse,
                target != null ? TenantDto.from(target) : null,
                Boolean.TRUE.equals(user.getMustChangePassword()));
    }

    /**
     * SUPER_ADMIN always gets a sentinel list so the frontend knows to skip all checks.
     * Other users get their explicitly granted permissions from the DB.
     */
    private List<String> resolvePermissions(User user) {
        if (Role.SUPER_ADMIN.equals(user.getRole().getName())) {
            return List.of("*"); // wildcard — frontend treats this as "all permissions"
        }
        return userPermissionService.getUserPermissionKeys(user.getId());
    }

    public void logout(String accessToken, String username) {
        jwtProvider.blacklistToken(accessToken);
        jwtProvider.invalidateRefreshToken(username);
    }

    /**
     * Works out which channel a session should be scoped to.
     * SUPER_ADMIN/PLATFORM_OWNER: unscoped by default (every channel listed, none active) —
     * UNLESS {@code preferredTenantId} names a channel SUPER_ADMIN had previously entered
     * (via switchTenant) and that channel still exists, in which case a token refresh keeps
     * it there instead of silently kicking it back to the platform view.
     * Single-channel users: that channel, always.
     * Multi-channel users: prefer {@code preferredTenantId} (a channel they were
     * already using, e.g. across a token refresh) if it's still a valid membership,
     * else their flagged default, else no active channel — the client must call
     * /api/auth/switch-tenant before other endpoints will serve data.
     */
    private TenantResolution resolveTenants(User user, UUID preferredTenantId) {
        if (UNSCOPED_ROLES.contains(user.getRole().getName())) {
            List<TenantDto> all = tenantRepo.findAll().stream().map(TenantDto::from).toList();
            if (preferredTenantId != null) {
                Tenant entered = tenantRepo.findById(preferredTenantId).orElse(null);
                if (entered != null) {
                    return new TenantResolution(entered.getId(), TenantDto.from(entered), all);
                }
            }
            return new TenantResolution(null, null, all);
        }

        List<Tenant> userTenants = tenantRepo.findByUserId(user.getId());
        if (userTenants.isEmpty()) {
            throw new BusinessException("No channel assigned to this account. Contact your administrator.");
        }
        List<TenantDto> dtos = userTenants.stream().map(TenantDto::from).toList();

        Tenant resolved;
        if (userTenants.size() == 1) {
            resolved = userTenants.get(0);
        } else {
            resolved = userTenants.stream().filter(t -> t.getId().equals(preferredTenantId)).findFirst()
                    .orElse(userTenants.contains(user.getDefaultTenant()) ? user.getDefaultTenant() : null);
        }
        return new TenantResolution(
                resolved != null ? resolved.getId() : null,
                resolved != null ? TenantDto.from(resolved) : null,
                dtos);
    }

    private record TenantResolution(UUID activeTenantId, TenantDto activeTenant, List<TenantDto> tenants) {}
}
