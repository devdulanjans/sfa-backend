package com.sfa.repository;

import com.sfa.entity.Return;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReturnRepository extends JpaRepository<Return, UUID> {
    Page<Return> findBySalesRepId(UUID salesRepId, Pageable pageable);
    Page<Return> findByOrderId(UUID orderId, Pageable pageable);
    Page<Return> findByCustomerId(UUID customerId, Pageable pageable);
}
