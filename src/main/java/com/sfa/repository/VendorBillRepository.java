package com.sfa.repository;

import com.sfa.entity.VendorBill;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface VendorBillRepository extends JpaRepository<VendorBill, UUID> {

    @Query("""
        SELECT b FROM VendorBill b JOIN FETCH b.vendor
        WHERE (:vendorId IS NULL OR b.vendor.id = :vendorId)
          AND (:status IS NULL OR b.status = :status)
        ORDER BY b.billDate DESC, b.createdAt DESC
        """)
    Page<VendorBill> findFiltered(
            @Param("vendorId") UUID vendorId,
            @Param("status") VendorBill.BillStatus status,
            Pageable pageable);
}
