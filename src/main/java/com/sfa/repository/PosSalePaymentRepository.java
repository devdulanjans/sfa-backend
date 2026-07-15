package com.sfa.repository;

import com.sfa.entity.PosSalePayment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface PosSalePaymentRepository extends JpaRepository<PosSalePayment, UUID> {
    List<PosSalePayment> findBySaleIdOrderByCreatedAtAsc(UUID saleId);
    Page<PosSalePayment> findByCustomerIdOrderByCreatedAtDesc(UUID customerId, Pageable pageable);
}
