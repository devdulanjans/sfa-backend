package com.sfa.repository;

import com.sfa.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID>, JpaSpecificationExecutor<Invoice> {
    boolean existsByOrderId(UUID orderId);
    Optional<Invoice> findByOrderId(UUID orderId);
    List<Invoice> findByOrderIdIn(Collection<UUID> orderIds);
    Page<Invoice> findByCustomerId(UUID customerId, Pageable pageable);
    Page<Invoice> findByIssuedDateBetween(LocalDate from, LocalDate to, Pageable pageable);

    @Query(
        value      = "SELECT i FROM Invoice i JOIN FETCH i.customer JOIN FETCH i.order o JOIN FETCH o.salesRep ORDER BY i.issuedDate DESC, i.createdAt DESC",
        countQuery = "SELECT COUNT(i) FROM Invoice i"
    )
    Page<Invoice> findAllWithCustomer(Pageable pageable);

    @Query("SELECT COUNT(i) FROM Invoice i WHERE i.status = :status AND i.createdAt BETWEEN :from AND :to")
    long countIssuedBetween(
            @Param("from") Instant from,
            @Param("to") Instant to,
            @Param("status") Invoice.InvoiceStatus status);

    default long countIssuedBetween(Instant from, Instant to) {
        return countIssuedBetween(from, to, Invoice.InvoiceStatus.ISSUED);
    }

}
