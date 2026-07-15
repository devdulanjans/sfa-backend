package com.sfa.repository;

import com.sfa.entity.CustomerVisit;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface CustomerVisitRepository extends JpaRepository<CustomerVisit, UUID> {
    Page<CustomerVisit> findBySalesRepId(UUID salesRepId, Pageable pageable);

    @Query("SELECT v FROM CustomerVisit v WHERE v.salesRep.id = :salesRepId AND v.checkOut IS NULL ORDER BY v.checkIn DESC")
    Optional<CustomerVisit> findOpenVisit(UUID salesRepId);

    Page<CustomerVisit> findByCustomerId(UUID customerId, Pageable pageable);

    @Query("SELECT v FROM CustomerVisit v WHERE v.checkIn >= :from AND v.checkIn <= :to")
    Page<CustomerVisit> findByDateRange(Instant from, Instant to, Pageable pageable);
}
