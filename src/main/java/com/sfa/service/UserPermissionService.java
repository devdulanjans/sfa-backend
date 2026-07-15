package com.sfa.service;

import com.sfa.entity.Permission;
import com.sfa.entity.UserPermission;
import com.sfa.repository.PermissionRepository;
import com.sfa.repository.SystemModuleRepository;
import com.sfa.repository.UserPermissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class UserPermissionService {

    private final UserPermissionRepository userPermissionRepo;
    private final PermissionRepository     permissionRepo;
    private final SystemModuleRepository   moduleRepo;

    @Transactional(readOnly = true)
    public List<Permission> getAllDefinedPermissions() {
        return permissionRepo.findAllByOrderByCategoryAscLabelAsc();
    }

    @Transactional(readOnly = true)
    public List<String> getUserPermissionKeys(UUID userId) {
        return userPermissionRepo.findPermissionKeysByUserId(userId);
    }

    @Transactional(readOnly = true)
    public boolean hasPermission(UUID userId, String permissionKey) {
        return userPermissionRepo.existsByUserIdAndPermissionKey(userId, permissionKey);
    }

    /** Replaces the full permission set for a user (used by the admin permission matrix). */
    public void setPermissions(UUID userId, List<String> permissionKeys, UUID grantedBy) {
        userPermissionRepo.deleteAllByUserId(userId);
        List<UserPermission> grants = permissionKeys.stream()
                .filter(key -> permissionRepo.existsById(key) || moduleRepo.existsById(key))
                .map(key -> UserPermission.builder()
                        .userId(userId)
                        .permissionKey(key)
                        .grantedBy(grantedBy)
                        .build())
                .toList();
        userPermissionRepo.saveAll(grants);
    }

    /** Grant a single permission without touching others. */
    public void grant(UUID userId, String permissionKey, UUID grantedBy) {
        if (!userPermissionRepo.existsByUserIdAndPermissionKey(userId, permissionKey)) {
            userPermissionRepo.save(UserPermission.builder()
                    .userId(userId)
                    .permissionKey(permissionKey)
                    .grantedBy(grantedBy)
                    .build());
        }
    }

    /** Revoke a single permission. */
    public void revoke(UUID userId, String permissionKey) {
        userPermissionRepo.findByUserId(userId).stream()
                .filter(up -> up.getPermissionKey().equals(permissionKey))
                .findFirst()
                .ifPresent(userPermissionRepo::delete);
    }
}
