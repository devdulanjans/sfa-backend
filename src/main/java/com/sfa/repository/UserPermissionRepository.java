package com.sfa.repository;

import com.sfa.entity.UserPermission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Permission grants (V119): rows with a membershipId belong to one membership (role in one
 * channel); rows without are the account-level fallback used by sessions with no membership.
 */
public interface UserPermissionRepository extends JpaRepository<UserPermission, UUID> {

    @Query("SELECT up FROM UserPermission up WHERE up.userId = :userId AND up.membershipId IS NULL")
    List<UserPermission> findByUserId(@Param("userId") UUID userId);

    @Query("""
        SELECT COUNT(up) > 0 FROM UserPermission up
        WHERE up.userId = :userId AND up.permissionKey = :key AND up.membershipId IS NULL
        """)
    boolean existsByUserIdAndPermissionKey(@Param("userId") UUID userId, @Param("key") String permissionKey);

    @Modifying
    @Query("DELETE FROM UserPermission up WHERE up.userId = :userId AND up.membershipId IS NULL")
    void deleteAllByUserId(@Param("userId") UUID userId);

    @Query("SELECT up.permissionKey FROM UserPermission up WHERE up.userId = :userId AND up.membershipId IS NULL")
    List<String> findPermissionKeysByUserId(@Param("userId") UUID userId);

    @Query("SELECT up.permissionKey FROM UserPermission up WHERE up.membershipId = :membershipId")
    List<String> findPermissionKeysByMembershipId(@Param("membershipId") UUID membershipId);

    @Modifying
    @Query("DELETE FROM UserPermission up WHERE up.membershipId = :membershipId")
    void deleteAllByMembershipId(@Param("membershipId") UUID membershipId);
}
