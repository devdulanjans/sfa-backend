package com.sfa.service;

import com.sfa.dto.user.AdminResetPasswordRequest;
import com.sfa.dto.user.ChangePasswordRequest;
import com.sfa.dto.user.CreateUserRequest;
import com.sfa.dto.user.UpdateUserRequest;
import com.sfa.dto.user.UserDto;
import com.sfa.entity.Customer;
import com.sfa.entity.CustomerGroup;
import com.sfa.entity.Distributor;
import com.sfa.entity.PasswordHistory;
import com.sfa.entity.Role;
import com.sfa.entity.Tenant;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerGroupRepository;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.DistributorRepository;
import com.sfa.repository.PasswordHistoryRepository;
import com.sfa.repository.RoleRepository;
import com.sfa.repository.TenantRepository;
import com.sfa.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {

    // 'platformowner' and 'superadmin' are recovery accounts, not day-to-day admin
    // users — kept out of the normal Users list/edit flow so a client's own admins
    // never see or touch them, but never deleted from the database. Visible only
    // through getRecoveryAccounts() when the correct RECOVERY_ACCESS_KEY is supplied.
    private static final Set<String> HIDDEN_USERNAMES = Set.of("platformowner", "superadmin");

    // Roles an ADMIN (channel-scoped administrator) can never assign, change into, or
    // manage another account as — only SUPER_ADMIN grants channel access or creates peers.
    private static final Set<String> ELEVATED_ROLES = Set.of(Role.SUPER_ADMIN, Role.PLATFORM_OWNER, Role.ADMIN);

    private final UserRepository         userRepository;
    private final RoleRepository         roleRepository;
    private final DistributorRepository  distributorRepository;
    private final CustomerRepository     customerRepository;
    private final CustomerGroupRepository customerGroupRepository;
    private final TenantRepository       tenantRepository;
    private final PasswordEncoder        passwordEncoder;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final AuditLogService        auditLogService;

    // How many previous passwords (including the one being replaced) a new password
    // is checked against — same depth for a self-change and an admin's reset.
    private static final int PASSWORD_HISTORY_DEPTH = 5;

    @Value("${app.recovery.key:}")
    private String recoveryKey;

    @Transactional(readOnly = true)
    public Page<UserDto> list(UUID distributorId, UUID actingUserId, Pageable pageable) {
        User actor = loadActor(actingUserId);
        if (isChannelScopedAdmin(actor)) {
            // An ADMIN only ever sees users it shares a channel with — never the whole platform.
            Set<UUID> actorTenantIds = actorTenantIds(actor);
            if (actorTenantIds.isEmpty()) {
                return Page.empty(pageable);
            }
            return userRepository.findByAnyTenantIdInExcludingUsernames(actorTenantIds, HIDDEN_USERNAMES, pageable)
                    .map(UserDto::from);
        }
        if (distributorId != null) {
            return userRepository.findByDistributorIdExcludingUsernames(distributorId, HIDDEN_USERNAMES, pageable)
                    .map(UserDto::from);
        }
        return userRepository.findByUsernameNotIn(HIDDEN_USERNAMES, pageable).map(UserDto::from);
    }

    // Recovery accounts are never listed on the Users page. Support/ops can view
    // (read-only) their current status here by supplying RECOVERY_ACCESS_KEY —
    // never editable through this or any other endpoint.
    @Transactional(readOnly = true)
    public List<UserDto> getRecoveryAccounts(String key) {
        if (recoveryKey.isBlank() || !recoveryKey.equals(key)) {
            throw new BusinessException("Invalid recovery key");
        }
        return userRepository.findByUsernameIn(HIDDEN_USERNAMES).stream().map(UserDto::from).toList();
    }

    @Transactional(readOnly = true)
    public UserDto getById(UUID id) {
        return UserDto.from(findOrThrow(id));
    }

    @Transactional(readOnly = true)
    public Optional<UserDto> findByCustomerId(UUID customerId) {
        return userRepository.findByCustomerId(customerId).map(UserDto::from);
    }

    @Transactional
    public UserDto create(CreateUserRequest req, UUID actingUserId) {
        if (userRepository.existsByUsername(req.username())) {
            throw new BusinessException("Username already taken: " + req.username());
        }
        if (userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email already registered: " + req.email());
        }
        Role role = roleRepository.findById(req.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", req.roleId()));
        rejectPlatformOwnerAssignment(role);

        User actor = loadActor(actingUserId);
        rejectElevatedRoleForAdminActor(actor, role);
        rejectOutOfScopeTenantsForAdminActor(actor, req.tenantIds());

        User user = new User();
        user.setUsername(req.username());
        user.setEmail(req.email());
        user.setFullName(req.fullName());
        user.setPasswordHash(passwordEncoder.encode(req.password()));
        user.setRole(role);
        user.setStatus(User.UserStatus.ACTIVE);

        // For CUSTOMER role: link to the specific customer they represent
        if (req.customerId() != null) {
            Customer customer = customerRepository.findById(req.customerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", req.customerId()));
            user.setCustomer(customer);
        }

        if (req.distributorIds() != null && !req.distributorIds().isEmpty()) {
            List<Distributor> distributors = distributorRepository.findAllById(req.distributorIds());
            user.getDistributors().addAll(distributors);
        }

        // Customer assignment (SALES_REP only)
        // Empty = access ALL customers; non-empty = access only the listed customers
        if (req.customerIds() != null && !req.customerIds().isEmpty()) {
            List<Customer> customers = customerRepository.findAllById(req.customerIds());
            user.getAssignedCustomers().addAll(customers);
        }
        if (req.customerGroupIds() != null && !req.customerGroupIds().isEmpty()) {
            List<CustomerGroup> groups = customerGroupRepository.findAllById(req.customerGroupIds());
            user.getCustomerGroups().addAll(groups);
        }

        applyTenants(user, req.tenantIds(), actor);

        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto update(UUID id, UpdateUserRequest req, UUID actingUserId) {
        User user = findOrThrow(id);
        rejectHiddenAccountModification(user);

        User actor = loadActor(actingUserId);
        rejectOutOfScopeTarget(actor, user);

        if (!user.getEmail().equalsIgnoreCase(req.email()) && userRepository.existsByEmail(req.email())) {
            throw new BusinessException("Email already registered: " + req.email());
        }

        user.setFullName(req.fullName());
        user.setEmail(req.email());

        Role role = roleRepository.findById(req.roleId())
                .orElseThrow(() -> new ResourceNotFoundException("Role", req.roleId()));
        rejectPlatformOwnerAssignment(role);
        rejectElevatedRoleForAdminActor(actor, role);
        rejectOutOfScopeTenantsForAdminActor(actor, req.tenantIds());
        user.setRole(role);

        user.getDistributors().clear();
        if (req.distributorIds() != null && !req.distributorIds().isEmpty()) {
            user.getDistributors().addAll(distributorRepository.findAllById(req.distributorIds()));
        }

        user.getAssignedCustomers().clear();
        if (req.customerIds() != null && !req.customerIds().isEmpty()) {
            user.getAssignedCustomers().addAll(customerRepository.findAllById(req.customerIds()));
        }

        user.getCustomerGroups().clear();
        if (req.customerGroupIds() != null && !req.customerGroupIds().isEmpty()) {
            user.getCustomerGroups().addAll(customerGroupRepository.findAllById(req.customerGroupIds()));
        }

        applyTenants(user, req.tenantIds(), actor);

        return UserDto.from(userRepository.save(user));
    }

    /** Self-service — the caller changes their OWN password. Requires the current password;
     *  never bypassable, even for a Super Admin acting on their own account (an admin
     *  resetting someone ELSE's password is a completely separate flow, below). */
    @Transactional
    public void changeOwnPassword(UUID userId, ChangePasswordRequest req) {
        User user = findOrThrow(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new BusinessException("Current password is incorrect.");
        }
        applyNewPassword(user, req.newPassword(), false);
        auditLogService.log(userId, "CHANGE_PASSWORD", "User", userId, null, "[own password changed]");
    }

    /** An admin/super-admin sets a NEW password for someone else's account — no current
     *  password needed (the admin doesn't and shouldn't know it). Subject to the exact same
     *  channel/elevated-role boundary as every other admin-on-another-user action. Deliberately
     *  refuses to act on the caller's own account — that must always go through
     *  changeOwnPassword, so an admin can never skip its own current-password check by
     *  routing through this endpoint instead. */
    @Transactional
    public void resetPassword(UUID targetUserId, AdminResetPasswordRequest req, UUID actingUserId) {
        if (targetUserId.equals(actingUserId)) {
            throw new BusinessException("Use \"Change Password\" in your account menu to change your own password.");
        }
        User user = findOrThrow(targetUserId);
        rejectHiddenAccountModification(user);
        User actor = loadActor(actingUserId);
        rejectOutOfScopeTarget(actor, user);

        boolean forceChange = req.forceChangeOnNextLogin() == null || req.forceChangeOnNextLogin();
        applyNewPassword(user, req.newPassword(), forceChange);
        auditLogService.log(actingUserId, "RESET_PASSWORD", "User", targetUserId, null, "[password reset by admin]");
    }

    /** Shared by both password flows above — history check, then record the OLD hash
     *  (never the new one) before overwriting it. */
    private void applyNewPassword(User user, String rawNewPassword, boolean forceChangeOnNextLogin) {
        assertNotRecentlyUsed(user, rawNewPassword);
        passwordHistoryRepository.save(PasswordHistory.builder()
                .user(user)
                .passwordHash(user.getPasswordHash())
                .build());
        user.setPasswordHash(passwordEncoder.encode(rawNewPassword));
        user.setPasswordChangedAt(Instant.now());
        user.setMustChangePassword(forceChangeOnNextLogin);
        userRepository.save(user);
    }

    /** Refuses both the current password re-submitted as "new", and any of the last
     *  PASSWORD_HISTORY_DEPTH passwords this account has used before. */
    private void assertNotRecentlyUsed(User user, String rawNewPassword) {
        if (passwordEncoder.matches(rawNewPassword, user.getPasswordHash())) {
            throw new BusinessException("New password must be different from your current password.");
        }
        List<PasswordHistory> recent = passwordHistoryRepository.findByUser_IdOrderByCreatedAtDesc(
                user.getId(), PageRequest.of(0, PASSWORD_HISTORY_DEPTH));
        boolean reused = recent.stream().anyMatch(h -> passwordEncoder.matches(rawNewPassword, h.getPasswordHash()));
        if (reused) {
            throw new BusinessException("You can't reuse one of your last " + PASSWORD_HISTORY_DEPTH + " passwords.");
        }
    }

    /** Full-replace, mirroring how distributors/customers/customerGroups are set from this
     *  form. Keeps the flagged default channel if it's still in the new set; otherwise falls
     *  back to the first of the new set, or null if the user has no channels at all.
     *
     *  When the acting admin is itself channel-scoped and leaves the channel picker empty,
     *  defaults to the admin's OWN channel(s) rather than leaving the user with none —
     *  an Admin can only ever grant channels it belongs to anyway (rejectOutOfScopeTenantsForAdminActor),
     *  so this just makes "every user an Admin manages belongs to a channel" a guarantee
     *  instead of something that depends on the admin remembering to check a box. A
     *  Super Admin explicitly clearing a user's channels is left alone — that's a
     *  deliberate platform-level action, not an oversight. */
    private void applyTenants(User user, List<UUID> tenantIds, User actor) {
        user.getTenants().clear();
        if (tenantIds != null && !tenantIds.isEmpty()) {
            user.getTenants().addAll(tenantRepository.findAllById(tenantIds));
        } else if (isChannelScopedAdmin(actor)) {
            user.getTenants().addAll(tenantRepository.findByUserId(actor.getId()));
        }
        if (user.getDefaultTenant() == null || !user.getTenants().contains(user.getDefaultTenant())) {
            user.setDefaultTenant(user.getTenants().stream().findFirst().orElse(null));
        }
    }

    @Transactional
    public UserDto toggleStatus(UUID id, UUID actingUserId) {
        User user = findOrThrow(id);
        rejectHiddenAccountModification(user);
        rejectOutOfScopeTarget(loadActor(actingUserId), user);
        user.setStatus(user.getStatus() == User.UserStatus.ACTIVE
                ? User.UserStatus.INACTIVE
                : User.UserStatus.ACTIVE);
        return UserDto.from(userRepository.save(user));
    }

    @Transactional
    public UserDto changeRole(UUID id, UUID roleId, UUID actingUserId) {
        User user = findOrThrow(id);
        rejectHiddenAccountModification(user);
        User actor = loadActor(actingUserId);
        rejectOutOfScopeTarget(actor, user);
        Role role = roleRepository.findById(roleId)
                .orElseThrow(() -> new ResourceNotFoundException("Role", roleId));
        rejectPlatformOwnerAssignment(role);
        rejectElevatedRoleForAdminActor(actor, role);
        user.setRole(role);
        return UserDto.from(userRepository.save(user));
    }

    // PLATFORM_OWNER sits above SUPER_ADMIN for this install's own license screen and
    // must never be assignable through the normal Users API — even a hand-crafted
    // request supplying its roleId directly is rejected here, not just hidden in the UI.
    private void rejectPlatformOwnerAssignment(Role role) {
        if (Role.PLATFORM_OWNER.equals(role.getName())) {
            throw new BusinessException("The PLATFORM_OWNER role cannot be assigned through this endpoint.");
        }
    }

    // Same reasoning as the list() exclusion above — 'platformowner' and 'superadmin'
    // are recovery accounts. Blocking it here too means a hand-crafted request against
    // the id (not just hiding the row in the UI) still can't touch them.
    private void rejectHiddenAccountModification(User user) {
        if (HIDDEN_USERNAMES.contains(user.getUsername())) {
            throw new BusinessException("This account is a recovery account and cannot be modified here.");
        }
    }

    private User findOrThrow(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("User", id));
    }

    // ── ADMIN (channel-scoped administrator) restrictions ───────────────────────────
    // SUPER_ADMIN calls these with actingUserId=null (loadActor returns null, every
    // check below short-circuits) — so none of this changes SUPER_ADMIN's behavior.

    private User loadActor(UUID actingUserId) {
        return actingUserId != null ? userRepository.findById(actingUserId).orElse(null) : null;
    }

    private boolean isChannelScopedAdmin(User actor) {
        return actor != null && Role.ADMIN.equals(actor.getRole().getName());
    }

    private Set<UUID> actorTenantIds(User actor) {
        return tenantRepository.findByUserId(actor.getId()).stream().map(Tenant::getId).collect(Collectors.toSet());
    }

    // An Admin can create/promote only "operational" roles (Sales Manager, Sales Rep,
    // Finance User, Cashier, Customer, ...) — never another Admin, Super Admin, or
    // Platform Owner. Only Super Admin grants that level of access.
    private void rejectElevatedRoleForAdminActor(User actor, Role targetRole) {
        if (isChannelScopedAdmin(actor) && ELEVATED_ROLES.contains(targetRole.getName())) {
            throw new BusinessException("Admins can't assign the " + targetRole.getName() + " role — only Super Admin can.");
        }
    }

    // An Admin can only ever hand out channels it's a member of itself — it can't grant
    // access to a channel it doesn't manage, even one that exists on the platform.
    private void rejectOutOfScopeTenantsForAdminActor(User actor, List<UUID> requestedTenantIds) {
        if (!isChannelScopedAdmin(actor) || requestedTenantIds == null || requestedTenantIds.isEmpty()) {
            return;
        }
        Set<UUID> allowed = actorTenantIds(actor);
        boolean requestsOutOfScope = requestedTenantIds.stream().anyMatch(id -> !allowed.contains(id));
        if (requestsOutOfScope) {
            throw new BusinessException("You can only assign channels you yourself belong to.");
        }
    }

    // An Admin can only manage users it shares a channel with, and never an elevated
    // account (another Admin, Super Admin, or Platform Owner) even if they happen to
    // share a channel.
    private void rejectOutOfScopeTarget(User actor, User target) {
        if (!isChannelScopedAdmin(actor)) {
            return;
        }
        if (ELEVATED_ROLES.contains(target.getRole().getName())) {
            throw new BusinessException("You don't have permission to manage this user.");
        }
        Set<UUID> actorTenants = actorTenantIds(actor);
        Set<UUID> targetTenants = tenantRepository.findByUserId(target.getId())
                .stream().map(Tenant::getId).collect(Collectors.toSet());
        if (Collections.disjoint(actorTenants, targetTenants)) {
            throw new BusinessException("You don't manage this user's channel.");
        }
    }
}
