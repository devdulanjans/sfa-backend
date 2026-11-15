package com.sfa.repository;

import com.sfa.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByEmailIgnoreCase(String email);
    Optional<User> findByEmailIgnoreCase(String email);
    /** Phones are stored normalised (0XXXXXXXXX) and unique per account (V119). */
    Optional<User> findByPhone(String phone);
    boolean existsByPhone(String phone);

    // Sign in with username, email, or (normalised) phone number.
    @Query("SELECT u FROM User u WHERE u.username = :identifier OR u.email = :identifier OR u.phone = :identifier")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    @Query(value = "SELECT u FROM User u JOIN u.distributors d WHERE d.id = :distributorId",
           countQuery = "SELECT COUNT(u) FROM User u JOIN u.distributors d WHERE d.id = :distributorId")
    Page<User> findByDistributorId(@Param("distributorId") UUID distributorId, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role.name = :roleName AND u.status = :status ORDER BY u.fullName")
    List<User> findActiveByRoleName(@Param("roleName") String roleName, @Param("status") User.UserStatus status);

    /** Active accounts holding an ACTIVE membership with this role in any of these channels (V119). */
    @Query("""
        SELECT DISTINCT u FROM User u, Membership m
        WHERE m.user = u AND m.role.name = :roleName AND u.status = :status
          AND m.status = com.sfa.entity.Membership$Status.ACTIVE
          AND m.tenant.status = com.sfa.entity.Tenant$TenantStatus.ACTIVE
          AND m.tenant.id IN :tenantIds
        """)
    List<User> findActiveByMembershipRole(@Param("roleName") String roleName, @Param("status") User.UserStatus status,
                                          @Param("tenantIds") java.util.Collection<UUID> tenantIds);

    /** Same, across every channel — the SUPER_ADMIN platform view. */
    @Query("""
        SELECT DISTINCT u FROM User u, Membership m
        WHERE m.user = u AND m.role.name = :roleName AND u.status = :status
          AND m.status = com.sfa.entity.Membership$Status.ACTIVE
          AND m.tenant.status = com.sfa.entity.Tenant$TenantStatus.ACTIVE
        """)
    List<User> findActiveByMembershipRoleAnyChannel(@Param("roleName") String roleName,
                                                    @Param("status") User.UserStatus status);

    Optional<User> findByCustomerId(UUID customerId);

    Optional<User> findByLoginDistributorId(UUID distributorId);

    Page<User> findByUsernameNotIn(Collection<String> usernames, Pageable pageable);

    @Query(value = "SELECT u FROM User u JOIN u.distributors d WHERE d.id = :distributorId AND u.username NOT IN :hidden",
           countQuery = "SELECT COUNT(u) FROM User u JOIN u.distributors d WHERE d.id = :distributorId AND u.username NOT IN :hidden")
    Page<User> findByDistributorIdExcludingUsernames(@Param("distributorId") UUID distributorId,
                                                      @Param("hidden") Collection<String> hidden,
                                                      Pageable pageable);

    List<User> findByUsernameIn(Collection<String> usernames);

    // Used to scope the Users list for an ADMIN (channel-scoped) actor to only the
    // users sharing at least one of its own channels — done as a real query rather
    // than filtering a fetched Page, since post-filtering would desync page size/totals.
    @Query(value = "SELECT DISTINCT u FROM User u JOIN u.tenants t WHERE t.id IN :tenantIds AND u.username NOT IN :hidden",
           countQuery = "SELECT COUNT(DISTINCT u) FROM User u JOIN u.tenants t WHERE t.id IN :tenantIds AND u.username NOT IN :hidden")
    Page<User> findByAnyTenantIdInExcludingUsernames(@Param("tenantIds") Collection<UUID> tenantIds,
                                                      @Param("hidden") Collection<String> hidden,
                                                      Pageable pageable);
}
