package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "invoice_payments")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class InvoicePayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    // Optional (V120): without a bank account the money is held on Cash on Hand (1050).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_account_id")
    private BankAccount bankAccount;

    @Column(nullable = false, precision = 14, scale = 2)
    private BigDecimal amount;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "reference_number", length = 100)
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "journal_entry_id", nullable = false)
    private JournalEntry journalEntry;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(columnDefinition = "text")
    private String notes;

    // A customer payment settled across several invoices shares one receipt number.
    @Column(name = "receipt_number", length = 30)
    private String receiptNumber;

    // A payment recorded by mistake is voided (its ledger entry reversed), never deleted.
    @Column(name = "voided_at")
    private Instant voidedAt;

    @Column(name = "voided_by")
    private UUID voidedBy;

    @Column(name = "void_reason", length = 255)
    private String voidReason;

    public boolean isVoided() {
        return voidedAt != null;
    }

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
