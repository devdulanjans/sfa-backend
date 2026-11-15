package com.sfa.service;

import com.sfa.dto.InvoicePaymentDto;
import com.sfa.entity.ChartOfAccount;
import com.sfa.entity.Customer;
import com.sfa.entity.Invoice;
import com.sfa.entity.JournalEntry;
import com.sfa.entity.PaymentMethod;
import com.sfa.entity.Role;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.ChartOfAccountRepository;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.InvoiceRepository;
import com.sfa.security.TenantContext;
import com.sfa.security.TenantGuard;
import com.sfa.security.UserDetailsImpl;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Money a sales rep collects at the customer (V122): previous bills paid on a visit, in full
 * or in part. A collection is always accepted — the cash is already collected — and settles
 * the customer's unpaid invoices oldest due first. Anything above what they owed by the time
 * it reaches the server (e.g. an offline collection after the office already took a payment)
 * is held as a customer advance and flagged. The channel's owners/managers get an alert, and
 * review collections on the admin Rep Collections page.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class CollectionService {

    private static final List<Invoice.InvoiceStatus> OUTSTANDING =
            List.of(Invoice.InvoiceStatus.ISSUED, Invoice.InvoiceStatus.PARTIALLY_PAID);

    private static final String CASH_ON_HAND_CODE      = "1050";
    private static final String CUSTOMER_ADVANCES_CODE = "2150";

    // ── DTOs ─────────────────────────────────────────────────────────────────────

    public record CustomerDue(UUID customerId, BigDecimal balance, BigDecimal overdue, long openInvoices,
                              LocalDate oldestDue, long daysOverdue) {}

    public record OpenInvoice(UUID id, String invoiceNumber, LocalDate issuedDate, LocalDate dueDate,
                              BigDecimal total, BigDecimal paidAmount, BigDecimal balance,
                              boolean overdue, long daysOverdue) {}

    public record CustomerDueDetail(UUID customerId, String customerName, BigDecimal balance, BigDecimal overdue,
                                    BigDecimal advance, List<OpenInvoice> openInvoices,
                                    List<CollectionDto> recentCollections) {}

    public record CollectRequest(String clientRef, UUID customerId, BigDecimal amount, String paymentMethod,
                                 String referenceNumber, String notes, List<UUID> invoiceIds,
                                 Instant collectedAt, BigDecimal latitude, BigDecimal longitude) {}

    public record CollectionDto(UUID id, String receiptNumber, UUID customerId, String customerName,
                                String customerCode, UUID collectedById, String collectedByName,
                                BigDecimal amount, BigDecimal appliedAmount, BigDecimal unappliedAmount,
                                BigDecimal balanceBefore, BigDecimal balanceAfter, LocalDate paymentDate,
                                String paymentMethod, String referenceNumber, String notes,
                                BigDecimal latitude, BigDecimal longitude, Instant collectedAt, Instant createdAt,
                                Instant reviewedAt, String reviewedByName, String invoiceNumbers,
                                boolean partial) {}

    public record CollectionTotals(long count, BigDecimal amount, BigDecimal unapplied, long unreviewed) {}

    public record CollectionList(CollectionTotals totals, List<CollectionDto> items) {}

    // ─────────────────────────────────────────────────────────────────────────────

    private final InvoiceRepository invoiceRepo;
    private final CustomerRepository customerRepo;
    private final ChartOfAccountRepository accountRepo;
    private final InvoicePaymentService paymentService;
    private final JournalEntryService journalEntryService;
    private final AdminNotificationService notifications;
    private final AuditLogService auditLog;

    @PersistenceContext
    private EntityManager em;

    // ── Rep (mobile) ───────────────────────────────────────────────────────────

    /** Amount due per customer the caller can see (a rep with assigned customers: only those). */
    @Transactional(readOnly = true)
    public List<CustomerDue> balances(UserDetailsImpl user) {
        Set<UUID> restricted = restrictedIds(user);
        LocalDate today = LocalDate.now();
        return invoiceRepo.balancesByCustomer(OUTSTANDING, today).stream()
                .filter(r -> restricted == null || restricted.contains((UUID) r[0]))
                .map(r -> {
                    LocalDate oldest = (LocalDate) r[7];
                    BigDecimal overdue = (BigDecimal) r[6];
                    long late = oldest != null && overdue.signum() > 0 ? ChronoUnit.DAYS.between(oldest, today) : 0;
                    return new CustomerDue((UUID) r[0], (BigDecimal) r[5], overdue, ((Number) r[4]).longValue(),
                            oldest, Math.max(0, late));
                })
                .toList();
    }

    /** One customer's open invoices, advance on account and latest collections. */
    @Transactional(readOnly = true)
    public CustomerDueDetail customerDue(UUID customerId, UserDetailsImpl user) {
        Customer c = accessibleCustomer(customerId, user);
        LocalDate today = LocalDate.now();
        List<OpenInvoice> open = invoiceRepo.findPayableForCustomer(customerId, OUTSTANDING).stream()
                .map(i -> {
                    boolean overdue = i.isOverdue();
                    long late = overdue && i.getDueDate() != null ? ChronoUnit.DAYS.between(i.getDueDate(), today) : 0;
                    return new OpenInvoice(i.getId(), i.getInvoiceNumber(), i.getIssuedDate(), i.getDueDate(),
                            i.getTotal(), i.getPaidAmount(), i.getBalance(), overdue, late);
                }).toList();
        BigDecimal balance = open.stream().map(OpenInvoice::balance).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal overdue = open.stream().filter(OpenInvoice::overdue).map(OpenInvoice::balance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        List<CollectionDto> recent = query(null, null, null, customerId, false, 10);
        BigDecimal advance = recent.isEmpty() ? BigDecimal.ZERO : advanceFor(customerId);
        return new CustomerDueDetail(c.getId(), c.getName(), balance, overdue, advance, open, recent);
    }

    /**
     * Records a collection. Idempotent on {@code clientRef}: the app retries a queued upload until
     * it gets an answer, and a retry returns the collection already recorded instead of a second one.
     */
    public CollectionDto collect(CollectRequest req, UserDetailsImpl user) {
        if (req.clientRef() != null && !req.clientRef().isBlank()) {
            em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(:ref))")
                    .setParameter("ref", req.clientRef()).getSingleResult();
            UUID existing = existingId(req.clientRef());
            if (existing != null) return get(existing);
        }
        UUID tenantId = TenantContext.isUnscoped() ? null : TenantContext.getTenantId();
        if (tenantId == null) throw new BusinessException("Open a channel before recording a collection");
        if (req.customerId() == null) throw new BusinessException("Choose the customer");
        BigDecimal amount = req.amount() == null ? null : req.amount().setScale(2, RoundingMode.HALF_UP);
        if (amount == null || amount.signum() <= 0) throw new BusinessException("Enter the amount collected");

        Customer customer = accessibleCustomer(req.customerId(), user);
        // One collection per customer at a time, so two can't both settle the same balance.
        em.createNativeQuery("SELECT pg_advisory_xact_lock(hashtext(CAST(:c AS text)))")
                .setParameter("c", customer.getId().toString()).getSingleResult();

        PaymentMethod method = paymentService.parseMethod(req.paymentMethod());
        if (method == PaymentMethod.CHEQUE && blank(req.referenceNumber()) == null) {
            throw new BusinessException("Enter the cheque number");
        }
        Instant collectedAt = req.collectedAt() != null && !req.collectedAt().isAfter(Instant.now().plusSeconds(300))
                ? req.collectedAt() : Instant.now();
        LocalDate date = LocalDate.ofInstant(collectedAt, ZoneId.systemDefault());
        if (date.isAfter(LocalDate.now())) date = LocalDate.now();

        BigDecimal balanceBefore = owed(customer.getId());
        String receipt = paymentService.newReceiptNumber();
        UUID id = UUID.randomUUID();
        em.createNativeQuery("""
                INSERT INTO payment_collections (id, tenant_id, customer_id, collected_by, amount, balance_before,
                    payment_date, payment_method, reference_number, notes, receipt_number, client_ref,
                    latitude, longitude, collected_at)
                VALUES (:id, :t, :c, :by, :amt, :before, :date, :method, :ref, :notes, :receipt, :cref,
                    :lat, :lng, :at)
                """)
                .setParameter("id", id).setParameter("t", tenantId).setParameter("c", customer.getId())
                .setParameter("by", user.getId()).setParameter("amt", amount).setParameter("before", balanceBefore)
                .setParameter("date", Date.valueOf(date)).setParameter("method", method.name())
                .setParameter("ref", blank(req.referenceNumber())).setParameter("notes", blank(req.notes()))
                .setParameter("receipt", receipt).setParameter("cref", blank(req.clientRef()))
                .setParameter("lat", req.latitude()).setParameter("lng", req.longitude())
                .setParameter("at", Timestamp.from(collectedAt))
                .executeUpdate();

        List<InvoicePaymentDto> allocations = paymentService.applyCollection(customer.getId(), amount, method, date,
                req.referenceNumber(), notesFor(req.notes()), receipt, id, req.invoiceIds(), user.getId());
        BigDecimal applied = allocations.stream().map(InvoicePaymentDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal unapplied = amount.subtract(applied);
        if (unapplied.signum() > 0) {
            holdAsAdvance(id, customer, unapplied, date, receipt, user.getId());
        }
        em.createNativeQuery("UPDATE payment_collections SET applied_amount = :a WHERE id = :id")
                .setParameter("a", applied).setParameter("id", id).executeUpdate();

        BigDecimal after = balanceBefore.subtract(applied).max(BigDecimal.ZERO);
        auditLog.log(user.getId(), "COLLECT", "PAYMENT_COLLECTION", id, null,
                java.util.Map.of("customer", customer.getName(), "amount", amount, "receiptNumber", receipt,
                        "applied", applied, "balanceAfter", after));
        notifyOwners(tenantId, id, customer, user, amount, method, balanceBefore, after, unapplied);
        return get(id);
    }

    // ── Admin ────────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CollectionList list(LocalDate from, LocalDate to, UUID collectedBy, UUID customerId, boolean unreviewedOnly) {
        List<CollectionDto> items = query(from, to, collectedBy, customerId, unreviewedOnly, 500);
        CollectionTotals totals = new CollectionTotals(items.size(),
                items.stream().map(CollectionDto::amount).reduce(BigDecimal.ZERO, BigDecimal::add),
                items.stream().map(CollectionDto::unappliedAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
                items.stream().filter(i -> i.reviewedAt() == null).count());
        return new CollectionList(totals, items);
    }

    /** Marks a collection as checked (e.g. the cash was handed in). */
    public CollectionDto markReviewed(UUID id, boolean reviewed, UUID userId) {
        get(id); // channel check
        em.createNativeQuery(reviewed
                        ? "UPDATE payment_collections SET reviewed_at = now(), reviewed_by = :u WHERE id = :id"
                        : "UPDATE payment_collections SET reviewed_at = NULL, reviewed_by = NULL WHERE id = :id AND :u IS NOT NULL")
                .setParameter("u", userId).setParameter("id", id).executeUpdate();
        return get(id);
    }

    @Transactional(readOnly = true)
    public CollectionDto get(UUID id) {
        Query q = em.createNativeQuery(SELECT + " WHERE pc.id = :id" + scopeClause());
        q.setParameter("id", id);
        bindScope(q);
        @SuppressWarnings("unchecked")
        List<Object[]> rows = q.getResultList();
        if (rows.isEmpty()) throw new ResourceNotFoundException("Collection", id);
        return map(rows.get(0));
    }

    // ── Internals ──────────────────────────────────────────────────────────────

    private static final String SELECT = """
            SELECT pc.id, pc.receipt_number, pc.customer_id, c.name, c.customer_code, pc.collected_by,
                   COALESCE(u.full_name, u.username), pc.amount, pc.applied_amount, pc.balance_before,
                   COALESCE((SELECT SUM(i.total - i.paid_amount) FROM invoices i
                             WHERE i.customer_id = pc.customer_id AND i.tenant_id = pc.tenant_id
                               AND i.status IN ('ISSUED','PARTIALLY_PAID')), 0),
                   pc.payment_date, pc.payment_method, pc.reference_number, pc.notes, pc.latitude, pc.longitude,
                   pc.collected_at, pc.created_at, pc.reviewed_at, COALESCE(rv.full_name, rv.username),
                   (SELECT STRING_AGG(i.invoice_number, ', ' ORDER BY i.invoice_number)
                      FROM invoice_payments ip JOIN invoices i ON i.id = ip.invoice_id
                     WHERE ip.collection_id = pc.id AND ip.voided_at IS NULL)
            FROM payment_collections pc
            JOIN customers c ON c.id = pc.customer_id
            JOIN users u ON u.id = pc.collected_by
            LEFT JOIN users rv ON rv.id = pc.reviewed_by""";

    @SuppressWarnings("unchecked")
    private List<CollectionDto> query(LocalDate from, LocalDate to, UUID collectedBy, UUID customerId,
                                      boolean unreviewedOnly, int limit) {
        StringBuilder sql = new StringBuilder(SELECT).append(" WHERE 1 = 1").append(scopeClause());
        if (from != null) sql.append(" AND pc.payment_date >= :from");
        if (to != null) sql.append(" AND pc.payment_date <= :to");
        if (collectedBy != null) sql.append(" AND pc.collected_by = :rep");
        if (customerId != null) sql.append(" AND pc.customer_id = :cust");
        if (unreviewedOnly) sql.append(" AND pc.reviewed_at IS NULL");
        sql.append(" ORDER BY pc.collected_at DESC LIMIT ").append(limit);
        Query q = em.createNativeQuery(sql.toString());
        bindScope(q);
        if (from != null) q.setParameter("from", Date.valueOf(from));
        if (to != null) q.setParameter("to", Date.valueOf(to));
        if (collectedBy != null) q.setParameter("rep", collectedBy);
        if (customerId != null) q.setParameter("cust", customerId);
        return ((List<Object[]>) q.getResultList()).stream().map(CollectionService::map).toList();
    }

    private static CollectionDto map(Object[] r) {
        BigDecimal amount = (BigDecimal) r[7], applied = (BigDecimal) r[8], before = (BigDecimal) r[9];
        return new CollectionDto((UUID) r[0], (String) r[1], (UUID) r[2], (String) r[3], (String) r[4],
                (UUID) r[5], (String) r[6], amount, applied, amount.subtract(applied), before, (BigDecimal) r[10],
                toLocalDate(r[11]), (String) r[12], (String) r[13], (String) r[14],
                (BigDecimal) r[15], (BigDecimal) r[16], toInstant(r[17]), toInstant(r[18]), toInstant(r[19]),
                (String) r[20], (String) r[21], amount.compareTo(before) < 0);
    }

    private UUID existingId(String clientRef) {
        List<?> rows = em.createNativeQuery("SELECT id FROM payment_collections WHERE client_ref = :r")
                .setParameter("r", clientRef).getResultList();
        return rows.isEmpty() ? null : (UUID) rows.get(0);
    }

    private BigDecimal owed(UUID customerId) {
        return invoiceRepo.findPayableForCustomer(customerId, OUTSTANDING).stream()
                .map(Invoice::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal advanceFor(UUID customerId) {
        Query q = em.createNativeQuery(
                "SELECT COALESCE(SUM(pc.amount - pc.applied_amount), 0) FROM payment_collections pc WHERE pc.customer_id = :c"
                        + scopeClause());
        q.setParameter("c", customerId);
        bindScope(q);
        return (BigDecimal) q.getSingleResult();
    }

    /** Dr Cash on Hand / Cr Customer Advances for the part above what was owed. */
    private void holdAsAdvance(UUID collectionId, Customer customer, BigDecimal amount, LocalDate date,
                               String receipt, UUID userId) {
        ChartOfAccount cash = accountRepo.findByAccountCode(CASH_ON_HAND_CODE)
                .orElseThrow(() -> new BusinessException("Cash on Hand system account is missing"));
        ChartOfAccount advances = accountRepo.findByAccountCode(CUSTOMER_ADVANCES_CODE)
                .orElseThrow(() -> new BusinessException("Customer Advances system account is missing"));
        journalEntryService.postEntry(date,
                "Advance from " + customer.getName() + " — collection " + receipt,
                List.of(new JournalEntryService.LinePosting(cash.getId(), amount, null, receipt),
                        new JournalEntryService.LinePosting(advances.getId(), null, amount, receipt)),
                JournalEntry.SourceType.CUSTOMER_ADVANCE, collectionId, userId);
    }

    private void notifyOwners(UUID tenantId, UUID id, Customer customer, UserDetailsImpl rep, BigDecimal amount,
                              PaymentMethod method, BigDecimal before, BigDecimal after, BigDecimal unapplied) {
        String who = (String) em.createNativeQuery("SELECT COALESCE(full_name, username) FROM users WHERE id = :id")
                .setParameter("id", rep.getId()).getSingleResult();
        String kind = unapplied.signum() > 0 ? "Collection (more than owed)"
                : amount.compareTo(before) < 0 ? "Part payment collected" : "Payment collected";
        StringBuilder msg = new StringBuilder()
                .append(who).append(" collected LKR ").append(money(amount)).append(" (").append(label(method))
                .append(") from ").append(customer.getName()).append(". ");
        if (unapplied.signum() > 0) {
            msg.append("LKR ").append(money(unapplied)).append(" was more than the customer owed and is held as an advance. ");
        }
        msg.append("Balance now LKR ").append(money(after)).append('.');
        notifications.notify(tenantId, "COLLECTION", kind + " — " + customer.getName(), msg.toString(),
                "/collections?id=" + id, rep.getId());
    }

    private Customer accessibleCustomer(UUID customerId, UserDetailsImpl user) {
        Customer c = customerRepo.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
        TenantGuard.requireVisible(c, "Customer", customerId);
        Set<UUID> restricted = restrictedIds(user);
        if (restricted != null && !restricted.contains(customerId)) {
            throw new AccessDeniedException("Customer not accessible");
        }
        return c;
    }

    /** Same rule as the customer list: a rep with assigned customers only works with those. */
    private static Set<UUID> restrictedIds(UserDetailsImpl user) {
        if (user != null && Role.SALES_REP.equals(user.getRoleName())
                && user.getAssignedCustomerIds() != null && !user.getAssignedCustomerIds().isEmpty()) {
            return user.getAssignedCustomerIds();
        }
        return null;
    }

    private static String scopeClause() {
        return TenantContext.isUnscoped() ? "" : " AND pc.tenant_id = :tenant";
    }

    private static void bindScope(Query q) {
        if (TenantContext.isUnscoped()) return;
        UUID t = TenantContext.getTenantId();
        q.setParameter("tenant", t != null ? t : new UUID(0, 0));
    }

    private static String notesFor(String notes) {
        String n = blank(notes);
        return n == null ? "Collected by sales rep" : "Collected by sales rep — " + n;
    }

    private static String label(PaymentMethod m) {
        return switch (m) {
            case CASH -> "cash";
            case CHEQUE -> "cheque";
            case BANK_TRANSFER -> "bank transfer";
            case CARD -> "card";
            default -> "other";
        };
    }

    private static String money(BigDecimal v) {
        return new DecimalFormat("#,##0.00").format(v);
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static LocalDate toLocalDate(Object o) {
        if (o instanceof LocalDate d) return d;
        if (o instanceof Date d) return d.toLocalDate();
        return null;
    }

    private static Instant toInstant(Object o) {
        if (o instanceof Instant i) return i;
        if (o instanceof Timestamp ts) return ts.toInstant();
        if (o instanceof OffsetDateTime odt) return odt.toInstant();
        return null;
    }
}
