package com.sfa.repository;

import com.sfa.entity.Vendor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    boolean existsByVendorCode(String vendorCode);

    @Query("""
        SELECT v FROM Vendor v
        WHERE (:search IS NULL OR LOWER(v.name) LIKE LOWER(CONCAT('%', :search, '%'))
                              OR LOWER(v.vendorCode) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY v.name ASC
        """)
    Page<Vendor> search(@Param("search") String search, Pageable pageable);
}
