package com.sfa.repository;

import com.sfa.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    @Query("SELECT u FROM User u WHERE u.username = :identifier OR u.email = :identifier")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    @Query(value = "SELECT u FROM User u JOIN u.distributors d WHERE d.id = :distributorId",
           countQuery = "SELECT COUNT(u) FROM User u JOIN u.distributors d WHERE d.id = :distributorId")
    Page<User> findByDistributorId(@Param("distributorId") UUID distributorId, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role.name = :roleName AND u.status = :status ORDER BY u.fullName")
    List<User> findActiveByRoleName(@Param("roleName") String roleName, @Param("status") User.UserStatus status);

    Optional<User> findByCustomerId(UUID customerId);
}
