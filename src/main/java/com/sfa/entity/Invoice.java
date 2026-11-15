package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.sfa.security.TenantAwareEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "invoices", indexes = {
    @Index(name = "idx_invoices_number",   columnList = "invoice_number"),
    @Index(name = "idx_invoices_customer", columnList = "customer_id"),
    @Index(name = "idx_invoices_date",     columnList = "issued_date"),
    @Index(name = "idx_invoices_tenant",   columnList = "tenant_id")
})
@EntityListeners({AuditingEntityListener.class, TenantAwareEntityListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id in (:tenantIds)")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Invoice implements TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    // Set explicitly from the order's own tenant at generation time (InvoiceService),
    // not left to ambient auto-stamping — an invoice must always carry the SAME channel
    // as its order, never whichever channel happens to be active in the generating
    // request (matters if that ever differs, e.g. a background/batch invoice run).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 20)
    private String invoiceNumber;

    // An order can be invoiced in several goes (V120) — each invoice bills part of it.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /** What this invoice bills: the chosen quantity of each order line (V120). */
    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo ASC")
    @Builder.Default
    private List<InvoiceItem> items = new ArrayList<>();

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "issued_date", nullable = false)
    private LocalDate issuedDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(name = "tax_total", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal taxTotal = BigDecimal.ZERO;

    @Column(name = "discount_total", precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal discountTotal = BigDecimal.ZERO;

    @Column(precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal total = BigDecimal.ZERO;

    @Column(name = "pdf_path", length = 500)
    private String pdfPath;

    @Column(name = "print_count")
    @Builder.Default
    private Integer printCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InvoiceStatus status = InvoiceStatus.ISSUED;

    @Column(name = "paid_amount", nullable = false, precision = 14, scale = 2)
    @Builder.Default
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancel_reason", length = 255)
    private String cancelReason;

    public void addItem(InvoiceItem item) {
        items.add(item);
        item.setInvoice(this);
    }

    /** Still to be paid (never negative). */
    public BigDecimal getBalance() {
        if (status == InvoiceStatus.CANCELLED) return BigDecimal.ZERO;
        BigDecimal b = (total != null ? total : BigDecimal.ZERO)
                .subtract(paidAmount != null ? paidAmount : BigDecimal.ZERO);
        return b.signum() < 0 ? BigDecimal.ZERO : b;
    }

    /** Unpaid (in full or part) and past its due date. */
    public boolean isOverdue() {
        return getBalance().signum() > 0 && dueDate != null && dueDate.isBefore(LocalDate.now());
    }

    /**
     * The lines to print / export / send for this invoice — its own billed quantities as
     * detached OrderItems. Falls back to the order's lines only for an invoice that somehow
     * has none (every invoice from before V120 was backfilled with its order's lines).
     */
    public List<OrderItem> printLines() {
        if (items != null && !items.isEmpty()) {
            return items.stream().map(InvoiceItem::asPrintLine).toList();
        }
        return order != null ? order.getItems() : List.of();
    }

    public void incrementPrintCount() {
        this.printCount = (this.printCount == null ? 0 : this.printCount) + 1;
    }

    public enum InvoiceStatus { DRAFT, ISSUED, PARTIALLY_PAID, PAID, CANCELLED }
}
