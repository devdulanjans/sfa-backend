package com.sfa.repository;

import com.sfa.entity.Tenant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TenantRepository extends JpaRepository<Tenant, UUID> {

    boolean existsByCode(String code);

    @Query("SELECT t FROM User u JOIN u.tenants t WHERE u.id = :userId ORDER BY t.name")
    List<Tenant> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT c.tenant FROM Customer c WHERE c.id = :customerId")
    Optional<Tenant> findByCustomerId(@Param("customerId") UUID customerId);

    /** Every channel a customer is available in (V112), by name. */
    @Query("SELECT t FROM Tenant t WHERE t.id IN (SELECT cid FROM Customer c JOIN c.channelIds cid WHERE c.id = :customerId) ORDER BY t.name")
    List<Tenant> findChannelsOfCustomer(@Param("customerId") UUID customerId);

    @Query("SELECT t FROM Tenant t WHERE LOWER(t.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(t.code) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Tenant> search(@Param("q") String q, Pageable pageable);
}
