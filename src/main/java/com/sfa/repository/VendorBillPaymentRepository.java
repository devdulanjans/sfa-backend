package com.sfa.repository;

import com.sfa.entity.VendorBillPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface VendorBillPaymentRepository extends JpaRepository<VendorBillPayment, UUID> {
    List<VendorBillPayment> findByVendorBillIdOrderByPaymentDateDesc(UUID vendorBillId);
}
