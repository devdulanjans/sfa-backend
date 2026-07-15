package com.sfa.repository;

import com.sfa.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface UserPermissionRepository extends JpaRepository<UserPermission, UUID> {

    List<UserPermission> findByUserId(UUID userId);

    boolean existsByUserIdAndPermissionKey(UUID userId, String permissionKey);

    @Modifying
    @Query("DELETE FROM UserPermission up WHERE up.userId = :userId")
    void deleteAllByUserId(@Param("userId") UUID userId);

    @Query("SELECT up.permissionKey FROM UserPermission up WHERE up.userId = :userId")
    List<String> findPermissionKeysByUserId(@Param("userId") UUID userId);
}
