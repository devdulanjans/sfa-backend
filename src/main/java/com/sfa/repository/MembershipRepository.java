package com.sfa.repository;

import com.sfa.entity.Membership;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MembershipRepository extends JpaRepository<Membership, UUID> {

    @Query("SELECT m FROM Membership m WHERE m.user.id = :userId ORDER BY m.tenant.name, m.role.name")
    List<Membership> findByUserId(@Param("userId") UUID userId);

    @Query("""
        SELECT m FROM Membership m WHERE m.user.id = :userId AND m.status IN :statuses
        ORDER BY m.tenant.name, m.role.name
        """)
    List<Membership> findByUserIdAndStatusIn(@Param("userId") UUID userId,
                                             @Param("statuses") Collection<Membership.Status> statuses);

    @Query("SELECT m FROM Membership m WHERE m.user.id = :userId AND m.tenant.id = :tenantId ORDER BY m.createdAt")
    List<Membership> findByUserIdAndTenantId(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId);

    @Query("""
        SELECT m FROM Membership m WHERE m.user.id = :userId AND m.tenant.id = :tenantId AND m.role.id = :roleId
        """)
    Optional<Membership> findOne(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId,
                                 @Param("roleId") UUID roleId);

    @Query("""
        SELECT m FROM Membership m JOIN FETCH m.user
        WHERE m.tenant.id = :tenantId AND m.status IN :statuses
        ORDER BY m.user.fullName, m.role.name
        """)
    List<Membership> findByTenantIdAndStatusIn(@Param("tenantId") UUID tenantId,
                                               @Param("statuses") Collection<Membership.Status> statuses);

    @Query("""
        SELECT COUNT(m) > 0 FROM Membership m
        WHERE m.user.id = :userId AND m.tenant.id = :tenantId AND m.role.name = :roleName
          AND m.status = com.sfa.entity.Membership$Status.ACTIVE
        """)
    boolean hasActiveRole(@Param("userId") UUID userId, @Param("tenantId") UUID tenantId,
                          @Param("roleName") String roleName);
}
