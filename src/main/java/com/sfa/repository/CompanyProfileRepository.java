package com.sfa.repository;

import com.sfa.entity.CompanyProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyProfileRepository extends JpaRepository<CompanyProfile, UUID> {

    Optional<CompanyProfile> findFirstByOrderByUpdatedAtDesc();

    /** Used by the public (unauthenticated) logo endpoint — TenantContext isn't
     *  populated for anonymous requests, so callers must resolve the tenant explicitly. */
    Optional<CompanyProfile> findByTenant_Id(UUID tenantId);
}
