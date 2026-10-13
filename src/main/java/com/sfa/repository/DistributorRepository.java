package com.sfa.repository;

import com.sfa.entity.Distributor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface DistributorRepository extends JpaRepository<Distributor, UUID> {

    boolean existsByCode(String code);

    @Query("SELECT d FROM User u JOIN u.distributors d WHERE u.id = :userId")
    List<Distributor> findByUserId(@Param("userId") UUID userId);

    @Query("SELECT d FROM Distributor d WHERE LOWER(d.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(d.code) LIKE LOWER(CONCAT('%', :q, '%'))")
    Page<Distributor> search(@Param("q") String q, Pageable pageable);

    // Channel-scoped equivalents of findAll()/search() above — used for a non-unscoped
    // (non-SUPER_ADMIN) actor, who should only ever see distributors under their own
    // session's active channel. DISTINCT: a distributor could match the join more than once
    // if it somehow had duplicate rows in distributor_tenants for other tenants — harmless,
    // but DISTINCT keeps paging counts correct regardless.
    @Query("SELECT DISTINCT d FROM Distributor d JOIN d.tenants t WHERE t.id = :tenantId")
    Page<Distributor> findByTenantId(@Param("tenantId") UUID tenantId, Pageable pageable);

    @Query("""
        SELECT DISTINCT d FROM Distributor d JOIN d.tenants t
        WHERE t.id = :tenantId
          AND (LOWER(d.name) LIKE LOWER(CONCAT('%', :q, '%')) OR LOWER(d.code) LIKE LOWER(CONCAT('%', :q, '%')))
        """)
    Page<Distributor> searchByTenantId(@Param("tenantId") UUID tenantId, @Param("q") String q, Pageable pageable);
}
