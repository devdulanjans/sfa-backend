package com.sfa.service;

import com.sfa.dto.membership.MembershipDto;
import com.sfa.entity.Customer;
import com.sfa.entity.Membership;
import com.sfa.entity.Role;
import com.sfa.entity.Tenant;
import com.sfa.entity.User;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.MembershipRepository;
import com.sfa.repository.RoleRepository;
import com.sfa.repository.TenantRepository;
import com.sfa.repository.UserRepository;
import com.sfa.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * One account, many roles (V119). A membership is a role in one business; a person may hold
 * several, in different businesses or the same one. Businesses add an EXISTING account by
 * invitation — the person must accept before getting any access.
 *
 * <p>Older code still reads {@code users.role_id}, {@code users.customer_id} and
 * {@code user_tenants}; {@link #syncAccountFields} keeps those mirroring the memberships.
 */
@Service
@RequiredArgsConstructor
public class MembershipService {

    /** Roles only the platform (SUPER_ADMIN) can grant — never through an invitation by a business. */
    private static final Set<String> NOT_INVITABLE = Set.of(Role.SUPER_ADMIN, Role.PLATFORM_OWNER, "ACCOUNT");

    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final TenantRepository tenantRepository;
    private final CustomerRepository customerRepository;
    private final AuditLogService auditLog;

    // ── A person's own workspaces ───────────────────────────────────────────────

    /** Memberships a session can run as: accepted, and the business is enabled. */
    @Transactional(readOnly = true)
    public List<Membership> usableFor(UUID userId) {
        return membershipRepository.findByUserIdAndStatusIn(userId, EnumSet.of(Membership.Status.ACTIVE)).stream()
                .filter(Membership::isUsable).toList();
    }

    @Transactional(readOnly = true)
    public List<MembershipDto> workspacesFor(UUID userId) {
        return usableFor(userId).stream().map(MembershipDto::of).toList();
    }

    @Transactional(readOnly = true)
    public List<MembershipDto> invitationsFor(UUID userId) {
        return membershipRepository.findByUserIdAndStatusIn(userId, EnumSet.of(Membership.Status.INVITED)).stream()
                .filter(m -> m.getTenant().getStatus() == Tenant.TenantStatus.ACTIVE)
                .map(MembershipDto::of).toList();
    }

    @Transactional
    public MembershipDto respond(UUID userId, UUID membershipId, boolean accept) {
        Membership m = membershipRepository.findById(membershipId)
                .filter(x -> x.getUser().getId().equals(userId))
                .orElseThrow(() -> new ResourceNotFoundException("Invitation", membershipId));
        if (m.getStatus() != Membership.Status.INVITED) {
            throw new BusinessException("This invitation was already answered.");
        }
        m.setStatus(accept ? Membership.Status.ACTIVE : Membership.Status.DECLINED);
        m.setRespondedAt(Instant.now());
        membershipRepository.save(m);
        syncAccountFields(m.getUser());
        auditLog.log(userId, accept ? "ACCEPT" : "DECLINE", "MEMBERSHIP", m.getId(), null,
                Map.of("tenant", m.getTenant().getCode(), "role", m.getRole().getName()));
        return MembershipDto.of(m);
    }

    @Transactional
    public void markUsed(UUID membershipId) {
        membershipRepository.findById(membershipId).ifPresent(m -> {
            m.setLastUsedAt(Instant.now());
            membershipRepository.save(m);
        });
    }

    // ── A business managing its people ──────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<MembershipDto> membersOf(UUID tenantId) {
        return membershipRepository.findByTenantIdAndStatusIn(tenantId,
                        EnumSet.of(Membership.Status.ACTIVE, Membership.Status.INVITED)).stream()
                .map(m -> MembershipDto.of(m, true)).toList();
    }

    /**
     * Invites an existing account (found by email or phone) to a role in a business. No access
     * until the person accepts.
     *
     * @param actorIsSuperAdmin only SUPER_ADMIN may grant the ADMIN (owner) role
     */
    @Transactional
    public MembershipDto invite(UUID actorId, boolean actorIsSuperAdmin, UUID tenantId, String emailOrPhone,
                                String roleName, UUID customerId) {
        User user = findAccount(emailOrPhone).orElseThrow(() -> new BusinessException(
                "No account uses " + emailOrPhone.trim() + ". Create the user instead, or ask them to sign up first."));
        return inviteUser(actorId, actorIsSuperAdmin, tenantId, user, roleName, customerId);
    }

    @Transactional
    public MembershipDto inviteUser(UUID actorId, boolean actorIsSuperAdmin, UUID tenantId, User user,
                                    String roleName, UUID customerId) {
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow(() -> new ResourceNotFoundException("Tenant", tenantId));
        Role role = roleRepository.findByName(roleName.trim().toUpperCase(Locale.ROOT))
                .orElseThrow(() -> new BusinessException("Unknown role: " + roleName));
        if (NOT_INVITABLE.contains(role.getName())) {
            throw new BusinessException("The " + role.getName() + " role can't be given by invitation.");
        }
        if (Role.ADMIN.equals(role.getName()) && !actorIsSuperAdmin) {
            throw new BusinessException("Only the platform administrator can add another owner (ADMIN).");
        }
        if (user.getId().equals(actorId) && !actorIsSuperAdmin) {
            throw new BusinessException("You can't invite yourself.");
        }
        Customer customer = null;
        if (Role.CUSTOMER.equals(role.getName())) {
            if (customerId == null) throw new BusinessException("Choose which of your customers this person orders for.");
            customer = customerRepository.findById(customerId)
                    .filter(c -> c.getChannelIds().contains(tenantId))
                    .orElseThrow(() -> new BusinessException("That customer isn't in this business."));
        }

        Membership m = membershipRepository.findOne(user.getId(), tenantId, role.getId()).orElse(null);
        if (m != null && m.getStatus() == Membership.Status.ACTIVE) {
            throw new BusinessException((user.getFullName() != null ? user.getFullName() : user.getUsername())
                    + " is already " + article(role.getName()) + " here.");
        }
        if (m != null && m.getStatus() == Membership.Status.INVITED) {
            throw new BusinessException("An invitation for this role is already waiting for them to accept.");
        }
        if (m == null) {
            m = Membership.builder().user(user).tenant(tenant).role(role).build();
        }
        m.setStatus(Membership.Status.INVITED);
        m.setCustomer(customer);
        m.setInvitedBy(actorId);
        m.setInvitedAt(Instant.now());
        m.setRespondedAt(null);
        Membership saved = membershipRepository.save(m);
        auditLog.log(actorId, "INVITE", "MEMBERSHIP", saved.getId(), null,
                Map.of("tenant", tenant.getCode(), "role", role.getName(), "user", user.getUsername()));
        return MembershipDto.of(saved, true);
    }

    /** Removes a role from a person in a business (their other workspaces are untouched). */
    @Transactional
    public void revoke(UUID actorId, boolean actorIsSuperAdmin, UUID membershipId) {
        Membership m = membershipRepository.findById(membershipId)
                .orElseThrow(() -> new ResourceNotFoundException("Membership", membershipId));
        if (!actorIsSuperAdmin && !m.getTenant().getId().equals(TenantContext.getTenantId())) {
            throw new ResourceNotFoundException("Membership", membershipId);
        }
        if (Role.ADMIN.equals(m.getRole().getName()) && !actorIsSuperAdmin) {
            throw new BusinessException("Only the platform administrator can remove an owner.");
        }
        if (m.getUser().getId().equals(actorId)) {
            throw new BusinessException("You can't remove your own role.");
        }
        m.setStatus(Membership.Status.REVOKED);
        membershipRepository.save(m);
        syncAccountFields(m.getUser());
        auditLog.log(actorId, "REVOKE", "MEMBERSHIP", m.getId(), null,
                Map.of("tenant", m.getTenant().getCode(), "role", m.getRole().getName()));
    }

    // ── Bridge for the classic Users page (one role per account per channel) ────

    /**
     * After the Users page created or edited an account (setting users.role_id / user_tenants
     * the classic way), make the memberships match — for the channels the editor manages only.
     * A role change on the Users page edits that channel's single membership; a person holding
     * several roles in one channel keeps the others.
     */
    @Transactional
    public void reconcileFromAccount(User user, Set<UUID> scopeTenantIds) {
        if (Set.of(Role.SUPER_ADMIN, Role.PLATFORM_OWNER).contains(user.getRole().getName())) return;
        Set<UUID> wanted = user.getTenants().stream().map(Tenant::getId).collect(Collectors.toSet());
        Role role = user.getRole();
        for (UUID tenantId : scopeTenantIds) {
            List<Membership> here = membershipRepository.findByUserIdAndTenantId(user.getId(), tenantId);
            List<Membership> active = here.stream().filter(m -> m.getStatus() == Membership.Status.ACTIVE).toList();
            if (wanted.contains(tenantId)) {
                Membership target = active.stream().filter(m -> m.getRole().getId().equals(role.getId())).findFirst()
                        .orElse(active.size() == 1 ? active.get(0)
                                : here.stream().filter(m -> m.getRole().getId().equals(role.getId())).findFirst().orElse(null));
                if (target == null) {
                    target = Membership.builder().user(user).role(role)
                            .tenant(tenantRepository.getReferenceById(tenantId)).build();
                }
                target.setRole(role);
                target.setStatus(Membership.Status.ACTIVE);
                target.setCustomer(user.getCustomer());
                target.setLoginDistributor(user.getLoginDistributor());
                membershipRepository.save(target);
            } else {
                active.forEach(m -> { m.setStatus(Membership.Status.REVOKED); membershipRepository.save(m); });
            }
        }
    }

    /**
     * Mirrors memberships onto the account row for older code: channels = every business with
     * an ACTIVE membership; role/customer = the most recently used one. Platform roles untouched.
     */
    @Transactional
    public void syncAccountFields(User user) {
        if (Set.of(Role.SUPER_ADMIN, Role.PLATFORM_OWNER).contains(user.getRole().getName())) return;
        List<Membership> active = membershipRepository.findByUserIdAndStatusIn(user.getId(),
                EnumSet.of(Membership.Status.ACTIVE));
        user.getTenants().clear();
        active.forEach(m -> user.getTenants().add(m.getTenant()));
        if (user.getDefaultTenant() != null && !user.getTenants().contains(user.getDefaultTenant())) {
            user.setDefaultTenant(null);
        }
        Optional<Membership> primary = active.stream().max(Comparator.comparing(
                (Membership m) -> m.getLastUsedAt() != null ? m.getLastUsedAt() : Instant.EPOCH));
        if (primary.isPresent()) {
            user.setRole(primary.get().getRole());
            active.stream().filter(m -> m.getCustomer() != null).findFirst()
                    .ifPresent(m -> user.setCustomer(m.getCustomer()));
        } else {
            roleRepository.findByName("ACCOUNT").ifPresent(user::setRole);
        }
        userRepository.save(user);
    }

    /**
     * Does {@code user} hold {@code roleName} in this business? Used where the code asks about
     * SOMEONE ELSE (e.g. "is the person a route is assigned to a sales rep here?"). With no
     * business in context, falls back to the account's own role.
     */
    @Transactional(readOnly = true)
    public boolean holdsRole(User user, UUID tenantId, String roleName) {
        if (tenantId == null) return roleName.equals(user.getRole().getName());
        return membershipRepository.hasActiveRole(user.getId(), tenantId, roleName);
    }

    /**
     * Gives an account a role straight away, without an invitation — only for cases where the
     * person asked for it themselves (e.g. the super admin approving their own channel request).
     */
    @Transactional
    public Membership grantActive(User user, Tenant tenant, String roleName) {
        Role role = roleRepository.findByName(roleName).orElseThrow(() -> new BusinessException("Unknown role: " + roleName));
        Membership m = membershipRepository.findOne(user.getId(), tenant.getId(), role.getId())
                .orElseGet(() -> Membership.builder().user(user).tenant(tenant).role(role).build());
        m.setStatus(Membership.Status.ACTIVE);
        m.setRespondedAt(Instant.now());
        Membership saved = membershipRepository.save(m);
        syncAccountFields(user);
        return saved;
    }

    /**
     * People holding {@code roleName} in the business(es) the caller is working in — e.g. the
     * sales reps offered in route-assignment and report pickers. Never another business's people;
     * SUPER_ADMIN's platform view gets everyone with that role.
     */
    @Transactional(readOnly = true)
    public List<User> peopleWithRoleInScope(String roleName) {
        List<User> people = TenantContext.isUnscoped()
                ? userRepository.findActiveByMembershipRoleAnyChannel(roleName, User.UserStatus.ACTIVE)
                : TenantContext.getVisibleTenantIds().isEmpty() ? List.of()
                : userRepository.findActiveByMembershipRole(roleName, User.UserStatus.ACTIVE,
                        TenantContext.getVisibleTenantIds());
        return people.stream()
                .sorted(Comparator.comparing((User u) -> u.getFullName() != null ? u.getFullName() : u.getUsername(),
                        String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** An account by email (case-insensitive) or phone (any common Sri Lankan format). */
    @Transactional(readOnly = true)
    public Optional<User> findAccount(String emailOrPhone) {
        if (emailOrPhone == null || emailOrPhone.isBlank()) return Optional.empty();
        String v = emailOrPhone.trim();
        if (v.contains("@")) return userRepository.findByEmailIgnoreCase(v);
        String phone = ShopService.normalizePhone(v);
        return phone != null ? userRepository.findByPhone(phone) : Optional.empty();
    }

    private static String article(String role) {
        String label = role.toLowerCase(Locale.ROOT).replace('_', ' ');
        return ("aeiou".indexOf(label.charAt(0)) >= 0 ? "an " : "a ") + label;
    }
}
