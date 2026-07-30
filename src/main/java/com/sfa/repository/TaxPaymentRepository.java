package com.sfa.repository;

import com.sfa.entity.TaxPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TaxPaymentRepository extends JpaRepository<TaxPayment, UUID> {
    List<TaxPayment> findAllByOrderByPaymentDateDesc();
}
