package com.sfa.repository;

import com.sfa.entity.Damage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface DamageRepository extends JpaRepository<Damage, UUID> {
    Page<Damage> findByReportedById(UUID userId, Pageable pageable);
    Page<Damage> findByCustomerId(UUID customerId, Pageable pageable);
}
