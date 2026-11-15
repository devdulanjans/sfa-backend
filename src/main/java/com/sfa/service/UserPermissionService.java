package com.sfa.service;

import com.sfa.entity.Membership;
import com.sfa.entity.Permission;
import com.sfa.entity.UserPermission;
import com.sfa.repository.MembershipRepository;
import com.sfa.repository.PermissionRepository;
import com.sfa.repository.SystemModuleRepository;
import com.sfa.repository.UserPermissionRepository;
import com.sfa.security.TenantContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Feature permissions. Since V119 they belong to a membership (a role in one channel), so the
 * same person can have full rights as an owner and limited rights as a rep. Account-level rows
 * (no membership) remain the fallback for sessions without a membership.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserPermissionService {

    private final UserPermissionRepository userPermissionRepo;
    private final PermissionRepository     permissionRepo;
    private final SystemModuleRepository   moduleRepo;
    private final MembershipRepository     membershipRepo;

    @Transactional(readOnly = true)
    public List<Permission> getAllDefinedPermissions() {
        return permissionRepo.findAllByOrderByCategoryAscLabelAsc();
    }

    /** Account-level grants (sessions without a membership). */
    @Transactional(readOnly = true)
    public List<String> getUserPermissionKeys(UUID userId) {
        return userPermissionRepo.findPermissionKeysByUserId(userId);
    }

    @Transactional(readOnly = true)
    public List<String> getMembershipPermissionKeys(UUID membershipId) {
        return userPermissionRepo.findPermissionKeysByMembershipId(membershipId);
    }

    /** What a session has: its membership's grants, else the account's. */
    @Transactional(readOnly = true)
    public List<String> getSessionPermissionKeys(UUID userId, UUID membershipId) {
        return membershipId != null ? getMembershipPermissionKeys(membershipId) : getUserPermissionKeys(userId);
    }

    /**
     * What the permission matrix shows for a user: their grants in the channel the editor is
     * working in (first membership there), else the account-level grants.
     */
    @Transactional(readOnly = true)
    public List<String> getKeysForEditing(UUID userId) {
        UUID tenantId = TenantContext.isUnscoped() ? null : TenantContext.getTenantId();
        if (tenantId != null) {
            List<Membership> here = membershipRepo.findByUserIdAndTenantId(userId, tenantId);
            if (!here.isEmpty()) return getMembershipPermissionKeys(here.get(0).getId());
        }
        return getUserPermissionKeys(userId);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, String permissionKey) {
        return userPermissionRepo.existsByUserIdAndPermissionKey(userId, permissionKey);
    }

    /**
     * Replaces a user's grants from the admin permission matrix. Applies to the account-level
     * fallback and to the user's memberships in the editor's channel — or every membership
     * when SUPER_ADMIN edits from the platform view (no channel).
     */
    public void setPermissions(UUID userId, List<String> permissionKeys, UUID grantedBy) {
        List<String> valid = validKeys(permissionKeys);
        userPermissionRepo.deleteAllByUserId(userId);
        userPermissionRepo.saveAll(valid.stream().map(k -> grantRow(userId, null, k, grantedBy)).toList());
        for (Membership m : membershipsInScope(userId)) {
            setMembershipPermissions(m.getId(), valid, grantedBy);
        }
    }

    /** Replaces exactly one membership's grants (e.g. a new channel owner gets every module). */
    public void setMembershipPermissions(UUID membershipId, List<String> permissionKeys, UUID grantedBy) {
        Membership m = membershipRepo.findById(membershipId).orElseThrow();
        userPermissionRepo.deleteAllByMembershipId(membershipId);
        userPermissionRepo.flush();
        userPermissionRepo.saveAll(validKeys(permissionKeys).stream()
                .map(k -> grantRow(m.getUser().getId(), membershipId, k, grantedBy)).toList());
    }

    /** Grant a single permission, account-level and on every membership of the user. */
    public void grant(UUID userId, String permissionKey, UUID grantedBy) {
        if (!userPermissionRepo.existsByUserIdAndPermissionKey(userId, permissionKey)) {
            userPermissionRepo.save(grantRow(userId, null, permissionKey, grantedBy));
        }
        for (Membership m : membershipRepo.findByUserId(userId)) {
            if (!userPermissionRepo.findPermissionKeysByMembershipId(m.getId()).contains(permissionKey)) {
                userPermissionRepo.save(grantRow(userId, m.getId(), permissionKey, grantedBy));
            }
        }
    }

    /** Revoke a single account-level permission. */
    public void revoke(UUID userId, String permissionKey) {
        userPermissionRepo.findByUserId(userId).stream()
                .filter(up -> up.getPermissionKey().equals(permissionKey))
                .findFirst()
                .ifPresent(userPermissionRepo::delete);
    }

    private List<Membership> membershipsInScope(UUID userId) {
        if (TenantContext.isUnscoped() || TenantContext.getTenantId() == null) {
            return membershipRepo.findByUserId(userId);
        }
        return membershipRepo.findByUserIdAndTenantId(userId, TenantContext.getTenantId());
    }

    private List<String> validKeys(List<String> keys) {
        return keys.stream().distinct()
                .filter(key -> permissionRepo.existsById(key) || moduleRepo.existsById(key))
                .toList();
    }

    private static UserPermission grantRow(UUID userId, UUID membershipId, String key, UUID grantedBy) {
        return UserPermission.builder()
                .userId(userId)
                .membershipId(membershipId)
                .permissionKey(key)
                .grantedBy(grantedBy)
                .build();
    }
}
