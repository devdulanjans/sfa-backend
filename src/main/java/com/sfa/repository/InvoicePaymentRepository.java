package com.sfa.repository;

import com.sfa.entity.InvoicePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface InvoicePaymentRepository extends JpaRepository<InvoicePayment, UUID> {
    List<InvoicePayment> findByInvoiceIdOrderByPaymentDateDesc(UUID invoiceId);

    /** A customer's payments, newest first — the payment history on the balances page. */
    @org.springframework.data.jpa.repository.Query("""
        SELECT p FROM InvoicePayment p JOIN FETCH p.invoice i
        WHERE i.customer.id = :customerId
        ORDER BY p.paymentDate DESC, p.createdAt DESC
        """)
    List<InvoicePayment> findByCustomer(@org.springframework.data.repository.query.Param("customerId") UUID customerId);
}
