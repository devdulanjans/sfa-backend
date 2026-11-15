package com.sfa.service;

import com.sfa.dto.invoice.InvoiceSummaryDto;
import com.sfa.entity.ChartOfAccount;
import com.sfa.entity.Invoice;
import com.sfa.entity.InvoiceItem;
import com.sfa.entity.JournalEntry;
import com.sfa.entity.Order;
import com.sfa.entity.OrderItem;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.notification.SalesDocumentEvent;
import com.sfa.repository.ChartOfAccountRepository;
import com.sfa.repository.InvoiceItemRepository;
import com.sfa.repository.InvoiceRepository;
import com.sfa.repository.JournalEntryRepository;
import com.sfa.repository.OrderRepository;
import com.sfa.security.TenantGuard;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class InvoiceService {

    private static final String ACCOUNTS_RECEIVABLE_CODE = "1100";
    private static final String SALES_REVENUE_CODE       = "4000";
    private static final String SALES_DISCOUNTS_CODE     = "4900";
    private static final String TAX_PAYABLE_CODE         = "2200";

    private final InvoiceRepository        invoiceRepo;
    private final OrderRepository          orderRepo;
    private final InvoicePdfGenerator      pdfGenerator;
    private final MinioStorageService      storage;
    private final AuditLogService          auditLog;
    private final JournalEntryService      journalEntryService;
    private final ChartOfAccountRepository accountRepo;
    private final ApplicationEventPublisher events;
    private final InvoiceItemRepository    invoiceItemRepo;
    private final JournalEntryRepository   journalEntryRepo;

    @PersistenceContext
    private EntityManager em;

    public record InvoiceFilter(
            String    invoiceNo,
            String    orderNo,
            UUID      customerId,
            UUID      salesRepId,
            LocalDate createdFrom,
            LocalDate createdTo,
            LocalDate issuedFrom,
            LocalDate issuedTo,
            LocalDate dueFrom,
            LocalDate dueTo,
            /** UNPAID, PARTIALLY_PAID, PAID, OUTSTANDING (unpaid or part paid), OVERDUE, CANCELLED; null = all. */
            String    paymentStatus
    ) {
        public InvoiceFilter(String invoiceNo, String orderNo, UUID customerId, UUID salesRepId,
                             LocalDate createdFrom, LocalDate createdTo, LocalDate issuedFrom, LocalDate issuedTo,
                             LocalDate dueFrom, LocalDate dueTo) {
            this(invoiceNo, orderNo, customerId, salesRepId, createdFrom, createdTo, issuedFrom, issuedTo,
                    dueFrom, dueTo, null);
        }
    }

    /** An order line and how much of it to invoice now. */
    public record InvoiceLineRequest(UUID orderItemId, BigDecimal quantity) {}

    /**
     * What to invoice. No lines = everything still to invoice on the order (the on-the-spot
     * invoice); otherwise only these quantities, the rest stays open for a later invoice.
     */
    public record CreateInvoiceRequest(List<InvoiceLineRequest> lines, LocalDate dueDate, String notes) {}

    @Transactional(readOnly = true)
    public Page<InvoiceSummaryDto> listInvoices(InvoiceFilter f, Pageable pageable) {
        Specification<Invoice> spec = buildInvoiceSpec(f);
        return invoiceRepo.findAll(spec, pageable).map(InvoiceSummaryDto::from);
    }

    /** Same filters as {@link #listInvoices}, unpaged entities — used by the bulk
     *  full-detail (one row per product line) export, which needs the actual
     *  Invoice/Order/OrderItem/Product entities rather than the summary DTO. */
    @Transactional(readOnly = true)
    public List<Invoice> getInvoicesForExport(InvoiceFilter f) {
        Specification<Invoice> spec = buildInvoiceSpec(f);
        return invoiceRepo.findAll(spec);
    }

    private Specification<Invoice> buildInvoiceSpec(InvoiceFilter f) {
        final Instant createdFromInst = f.createdFrom() != null
                ? f.createdFrom().atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        final Instant createdToInst   = f.createdTo()   != null
                ? f.createdTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            final Join<?, ?> order;
            final Join<?, ?> salesRep;

            if (!Long.class.equals(query.getResultType())) {
                // Data query: fetch-join to avoid N+1 on ManyToOne associations
                root.fetch("customer", JoinType.INNER);
                @SuppressWarnings("unchecked")
                Join<?, ?> orderJoin = (Join<?, ?>) root.fetch("order", JoinType.INNER);
                @SuppressWarnings("unchecked")
                Join<?, ?> repJoin   = (Join<?, ?>) orderJoin.fetch("salesRep", JoinType.INNER);
                order    = orderJoin;
                salesRep = repJoin;
            } else {
                // Count query: plain joins (no fetch)
                order    = root.join("order",    JoinType.INNER);
                salesRep = order.join("salesRep", JoinType.INNER);
            }

            String invNo  = blank(f.invoiceNo());
            String ordNo  = blank(f.orderNo());

            if (invNo != null)
                predicates.add(cb.like(cb.lower(root.get("invoiceNumber")), "%" + invNo.toLowerCase() + "%"));
            if (ordNo != null)
                predicates.add(cb.like(cb.lower(order.get("orderNumber")), "%" + ordNo.toLowerCase() + "%"));
            if (f.customerId() != null)
                predicates.add(cb.equal(root.get("customer").get("id"), f.customerId()));
            if (f.salesRepId() != null)
                predicates.add(cb.equal(salesRep.get("id"), f.salesRepId()));
            if (createdFromInst != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), createdFromInst));
            if (createdToInst != null)
                predicates.add(cb.lessThan(root.get("createdAt"), createdToInst));
            if (f.issuedFrom() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("issuedDate"), f.issuedFrom()));
            if (f.issuedTo() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("issuedDate"), f.issuedTo()));
            if (f.dueFrom() != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("dueDate"), f.dueFrom()));
            if (f.dueTo() != null)
                predicates.add(cb.lessThanOrEqualTo(root.get("dueDate"), f.dueTo()));

            String ps = blank(f.paymentStatus());
            if (ps != null) {
                var status = root.<Invoice.InvoiceStatus>get("status");
                var unpaidStatuses = List.of(Invoice.InvoiceStatus.ISSUED, Invoice.InvoiceStatus.PARTIALLY_PAID);
                switch (ps.toUpperCase(Locale.ROOT)) {
                    case "UNPAID" -> predicates.add(cb.and(status.in(unpaidStatuses),
                            cb.equal(root.get("paidAmount"), BigDecimal.ZERO)));
                    case "PARTIALLY_PAID" -> predicates.add(cb.and(status.in(unpaidStatuses),
                            cb.greaterThan(root.get("paidAmount"), BigDecimal.ZERO)));
                    case "PAID" -> predicates.add(cb.equal(status, Invoice.InvoiceStatus.PAID));
                    case "CANCELLED" -> predicates.add(cb.equal(status, Invoice.InvoiceStatus.CANCELLED));
                    case "OUTSTANDING" -> predicates.add(status.in(unpaidStatuses));
                    case "OVERDUE" -> predicates.add(cb.and(status.in(unpaidStatuses),
                            cb.lessThan(root.get("dueDate"), LocalDate.now())));
                    default -> throw new BusinessException("Unknown payment status: " + ps);
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Bulk order-id → invoice-number lookup, used to annotate order list rows. */
    @Transactional(readOnly = true)
    public Map<UUID, String> getInvoiceNumbersForOrders(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        // An order can have several invoices (V120): "A, B" in the order they were raised.
        return invoiceRepo.findByOrderIdIn(orderIds).stream()
                .filter(inv -> inv.getStatus() != Invoice.InvoiceStatus.CANCELLED)
                .sorted(java.util.Comparator.comparing(Invoice::getCreatedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .collect(Collectors.toMap(inv -> inv.getOrder().getId(), Invoice::getInvoiceNumber,
                        (a, b) -> a + ", " + b, LinkedHashMap::new));
    }

    /** Every invoice raised for an order, oldest first (cancelled ones included, marked so). */
    @Transactional(readOnly = true)
    public List<Invoice> listForOrder(UUID orderId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        TenantGuard.requireVisible(order, "Order", orderId);
        return invoiceRepo.findByOrderIdOrderByCreatedAtAsc(orderId);
    }

    /** Invoices everything still to invoice on the order — the on-the-spot invoice. */
    public Invoice generateInvoice(UUID orderId, UUID userId) {
        return generateInvoice(orderId, null, userId);
    }

    /**
     * Raises an invoice for all or part of an approved order (V120). Each line bills the
     * chosen quantity at the order's price; its discount and tax are the same share of the
     * line's, and the invoice that bills the last of a line bills exactly what's left of it —
     * so an order invoiced in several goes adds up to the order total to the cent.
     */
    public Invoice generateInvoice(UUID orderId, CreateInvoiceRequest request, UUID userId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));
        order = TenantGuard.requireVisible(order, "Order", orderId);
        // Two people invoicing the same order at once must not both bill the same quantity.
        em.flush();
        em.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        if (order.getStatus() != Order.OrderStatus.APPROVED
                && order.getStatus() != Order.OrderStatus.PARTIALLY_INVOICED) {
            throw new BusinessException(order.getStatus() == Order.OrderStatus.INVOICED
                    ? "This order is fully invoiced — nothing is left to invoice"
                    : "Only approved orders can be invoiced");
        }

        Map<UUID, BigDecimal> wanted = quantitiesToInvoice(order, request);

        LocalDate today = LocalDate.now();
        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber(order))
                .order(order)
                .tenant(order.getTenant())
                .customer(order.getCustomer())
                .issuedDate(today)
                .dueDate(request != null && request.dueDate() != null ? request.dueDate() : defaultDueDate(order, today))
                .status(Invoice.InvoiceStatus.ISSUED)
                .notes(request != null ? blank(request.notes()) : null)
                .createdBy(userId)
                .build();

        BigDecimal subtotal = BigDecimal.ZERO, discount = BigDecimal.ZERO, tax = BigDecimal.ZERO;
        for (OrderItem item : order.getItems()) {
            BigDecimal qty = wanted.get(item.getId());
            if (qty == null) continue;
            InvoiceItem line = billLine(item, qty);
            invoice.addItem(line);
            item.setInvoicedQty(item.getInvoicedQty().add(qty));
            subtotal = subtotal.add(line.getUnitPrice().multiply(qty));
            discount = discount.add(line.getDiscountAmount());
            tax      = tax.add(line.getTaxAmount());
        }
        subtotal = subtotal.setScale(2, RoundingMode.HALF_UP);
        invoice.setSubtotal(subtotal);
        invoice.setDiscountTotal(discount);
        invoice.setTaxTotal(tax);
        invoice.setTotal(subtotal.subtract(discount).add(tax));

        Invoice saved = invoiceRepo.save(invoice);

        // Always posts, regardless of this install's FINANCE license flag — the license only
        // gates UI/API access to Financial Management, not the ledger's data integrity, so an
        // install that enables FINANCE later still has a complete history instead of a gap.
        postInvoiceIssuedEntry(saved, userId);

        // Generate PDF asynchronously and store in MinIO
        try {
            byte[] pdfBytes = pdfGenerator.generate(saved, order);
            String pdfPath  = "invoices/%s/%s.pdf".formatted(
                    saved.getIssuedDate().getYear(), saved.getInvoiceNumber());
            storage.upload(pdfPath, pdfBytes, "application/pdf");
            saved.setPdfPath(pdfPath);
            invoiceRepo.save(saved);
        } catch (Exception ex) {
            log.warn("PDF storage skipped for invoice {} (MinIO unavailable?): {}",
                    saved.getInvoiceNumber(), ex.getMessage());
        }

        // Partly invoiced until nothing is left to invoice or close.
        order.setStatus(order.hasRemainingToInvoice()
                ? Order.OrderStatus.PARTIALLY_INVOICED : Order.OrderStatus.INVOICED);
        orderRepo.save(order);

        auditLog.log(userId, "GENERATE", "INVOICE", saved.getId(), null,
                Map.of("invoiceNumber", saved.getInvoiceNumber(),
                       "status",        saved.getStatus().name(),
                       "total",         saved.getTotal(),
                       "orderStatus",   order.getStatus().name()));
        events.publishEvent(SalesDocumentEvent.invoiceGenerated(saved.getId()));
        return saved;
    }

    @Transactional
    public byte[] getPdfBytes(UUID invoiceId) {
        Invoice invoice = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));
        invoice = TenantGuard.requireVisible(invoice, "Invoice", invoiceId);

        // Try to serve the stored PDF first
        if (invoice.getPdfPath() != null) {
            try {
                return storage.download(invoice.getPdfPath());
            } catch (Exception ex) {
                log.warn("Stored PDF not accessible for invoice {}, regenerating on-the-fly", invoice.getInvoiceNumber());
            }
        }

        // Fallback: generate PDF on-the-fly (also handles invoices where pdfPath was never set)
        Order order = invoice.getOrder();
        try {
            byte[] pdfBytes = pdfGenerator.generate(invoice, order);
            // Persist so next request is served from storage
            try {
                String pdfPath = "invoices/%s/%s.pdf".formatted(
                        invoice.getIssuedDate().getYear(), invoice.getInvoiceNumber());
                storage.upload(pdfPath, pdfBytes, "application/pdf");
                invoice.setPdfPath(pdfPath);
                invoiceRepo.save(invoice);
            } catch (Exception storageEx) {
                log.warn("Could not persist regenerated PDF for invoice {}: {}", invoice.getInvoiceNumber(), storageEx.getMessage());
            }
            return pdfBytes;
        } catch (Exception ex) {
            log.error("PDF generation failed for invoice {}: {}", invoice.getInvoiceNumber(), ex.getMessage());
            throw new BusinessException("Failed to generate PDF for invoice " + invoice.getInvoiceNumber());
        }
    }

    public byte[] getThermalBytes(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        Order   order   = invoice.getOrder();
        return pdfGenerator.generateEscPos(invoice, order);
    }

    /**
     * TEMPORARY — dev/QA aid, remove before production (see InvoiceController's matching note).
     * Narrow receipt-style PDF mirroring the exact thermal-print content/layout — for
     * the mobile app's print-preview screen, not persisted (unlike getPdfBytes, this
     * is cheap to regenerate and has no reason to be cached in MinIO).
     */
    public byte[] getThermalPreviewBytes(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        Order   order   = invoice.getOrder();
        try {
            return pdfGenerator.generateThermalPreview(invoice, order);
        } catch (IOException ex) {
            log.error("Thermal preview generation failed for invoice {}: {}", invoice.getInvoiceNumber(), ex.getMessage());
            throw new BusinessException("Failed to generate print preview for invoice " + invoice.getInvoiceNumber());
        }
    }

    public Invoice recordPrint(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        invoice.incrementPrintCount();
        return invoiceRepo.save(invoice);
    }

    @Transactional(readOnly = true)
    public Invoice getInvoice(UUID id) {
        Invoice invoice = invoiceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
        // @Filter doesn't cover a by-id lookup (only HQL/Criteria queries) — without this,
        // any channel could fetch/print another channel's invoice by guessing its id.
        return TenantGuard.requireVisible(invoice, "Invoice", id);
    }

    /** The order's most recent invoice that wasn't cancelled (an order can have several). */
    @Transactional(readOnly = true)
    public Invoice getInvoiceByOrder(UUID orderId) {
        Invoice invoice = invoiceRepo.findByOrderIdOrderByCreatedAtAsc(orderId).stream()
                .filter(i -> i.getStatus() != Invoice.InvoiceStatus.CANCELLED)
                .reduce((first, second) -> second)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice for order", orderId));
        return TenantGuard.requireVisible(invoice, "Invoice for order", orderId);
    }

    /**
     * Cancels an invoice raised by mistake (V120): reverses its ledger entry and gives its
     * quantities back to the order, so they can be invoiced again. Refused while it has
     * payments — void those first, so no money is silently left unallocated.
     */
    public Invoice cancelInvoice(UUID invoiceId, String reason, UUID userId) {
        Invoice invoice = getInvoice(invoiceId);
        if (invoice.getStatus() == Invoice.InvoiceStatus.CANCELLED) {
            throw new BusinessException("Invoice is already cancelled");
        }
        if (invoice.getPaidAmount() != null && invoice.getPaidAmount().signum() > 0) {
            throw new BusinessException("This invoice has payments of " + invoice.getPaidAmount()
                    + " — void them before cancelling the invoice");
        }
        Order order = invoice.getOrder();
        em.flush();
        em.refresh(order, LockModeType.PESSIMISTIC_WRITE);

        for (JournalEntry entry : journalEntryRepo.findBySourceTypeAndSourceIdAndStatus(
                JournalEntry.SourceType.INVOICE_ISSUED, invoice.getId(), JournalEntry.JournalStatus.POSTED)) {
            journalEntryService.voidEntry(entry.getId(), userId);
        }

        for (InvoiceItem line : invoice.getItems()) {
            OrderItem item = line.getOrderItem();
            BigDecimal back = item.getInvoicedQty().subtract(line.getQuantity());
            item.setInvoicedQty(back.signum() < 0 ? BigDecimal.ZERO : back);
        }
        Invoice.InvoiceStatus previous = invoice.getStatus();
        invoice.setStatus(Invoice.InvoiceStatus.CANCELLED);
        invoice.setCancelledAt(Instant.now());
        invoice.setCancelledBy(userId);
        invoice.setCancelReason(blank(reason));
        invoiceRepo.save(invoice);

        if (order.getStatus() != Order.OrderStatus.CANCELLED) {
            order.setStatus(!order.hasInvoicedAnything() ? Order.OrderStatus.APPROVED
                    : order.hasRemainingToInvoice() ? Order.OrderStatus.PARTIALLY_INVOICED
                    : Order.OrderStatus.INVOICED);
            orderRepo.save(order);
        }

        auditLog.log(userId, "CANCEL", "INVOICE", invoice.getId(), previous, Invoice.InvoiceStatus.CANCELLED);
        return invoice;
    }

    /** Line id -> quantity to bill now, validated against what's left on each line. */
    private Map<UUID, BigDecimal> quantitiesToInvoice(Order order, CreateInvoiceRequest request) {
        Map<UUID, BigDecimal> wanted = new LinkedHashMap<>();
        if (request == null || request.lines() == null || request.lines().isEmpty()) {
            for (OrderItem item : order.getItems()) {
                if (item.getRemainingQty().signum() > 0) wanted.put(item.getId(), item.getRemainingQty());
            }
        } else {
            Map<UUID, OrderItem> byId = order.getItems().stream()
                    .collect(Collectors.toMap(OrderItem::getId, i -> i));
            for (InvoiceLineRequest line : request.lines()) {
                if (line == null || line.quantity() == null || line.quantity().signum() == 0) continue;
                OrderItem item = byId.get(line.orderItemId());
                if (item == null) {
                    throw new BusinessException("That item isn't on order " + order.getOrderNumber());
                }
                if (line.quantity().signum() < 0) {
                    throw new BusinessException("Quantity for " + item.getProduct().getName() + " can't be negative");
                }
                BigDecimal qty = wanted.getOrDefault(item.getId(), BigDecimal.ZERO).add(line.quantity());
                if (qty.compareTo(item.getRemainingQty()) > 0) {
                    throw new BusinessException("Only " + item.getRemainingQty().stripTrailingZeros().toPlainString()
                            + " of " + item.getProduct().getName() + " is left to invoice");
                }
                wanted.put(item.getId(), qty);
            }
        }
        if (wanted.isEmpty()) {
            throw new BusinessException("Nothing to invoice — choose a quantity for at least one item");
        }
        return wanted;
    }

    /** Bills {@code qty} of an order line — its share, or exactly the rest when it's the last of it. */
    private InvoiceItem billLine(OrderItem item, BigDecimal qty) {
        BigDecimal discount, tax, lineTotal;
        boolean last = qty.compareTo(item.getRemainingQty()) == 0 && item.getClosedQty().signum() == 0;
        if (qty.compareTo(item.getQuantity()) == 0) {
            discount = item.getDiscountAmount();
            tax = item.getTaxAmount();
            lineTotal = item.getLineTotal();
        } else if (last) {
            Object[] billed = invoiceItemRepo.billedSoFar(item.getId()).get(0);
            discount  = item.getDiscountAmount().subtract((BigDecimal) billed[0]);
            tax       = item.getTaxAmount().subtract((BigDecimal) billed[1]);
            lineTotal = item.getLineTotal().subtract((BigDecimal) billed[2]);
        } else {
            discount  = share(item.getDiscountAmount(), qty, item.getQuantity());
            tax       = share(item.getTaxAmount(), qty, item.getQuantity());
            lineTotal = item.getUnitPrice().multiply(qty).subtract(discount).add(tax).setScale(2, RoundingMode.HALF_UP);
        }
        return InvoiceItem.builder()
                .orderItem(item)
                .product(item.getProduct())
                .quantity(qty)
                .unitPrice(item.getUnitPrice())
                .discountPct(item.getDiscountPct())
                .taxPct(item.getTaxPct())
                .discountAmount(nz(discount))
                .taxAmount(nz(tax))
                .lineTotal(nz(lineTotal))
                .priceSource(item.getPriceSource())
                .promotionName(item.getPromotionName())
                .lineNo(item.getLineNo())
                .build();
    }

    private static BigDecimal share(BigDecimal amount, BigDecimal part, BigDecimal whole) {
        if (amount == null || whole.signum() == 0) return BigDecimal.ZERO;
        return amount.multiply(part).divide(whole, 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** The customer's credit days (or its head office's), else 30 days. */
    private static LocalDate defaultDueDate(Order order, LocalDate issued) {
        var customer = order.getCustomer();
        Integer days = customer.getCreditDays();
        if ((days == null || days <= 0) && customer.getParentCustomer() != null) {
            days = customer.getParentCustomer().getCreditDays();
        }
        return issued.plusDays(days != null && days > 0 ? days : 30);
    }

    /**
     * Dr Accounts Receivable = total, Dr Sales Discounts = discountTotal (contra-revenue,
     * so a discount can never net Sales Revenue into a negative credit), Cr Sales Revenue =
     * subtotal (gross), Cr Tax Payable = taxTotal (if any). Balances because
     * total = subtotal - discountTotal + taxTotal (see Order.recalculateTotals()).
     */
    private void postInvoiceIssuedEntry(Invoice invoice, UUID userId) {
        ChartOfAccount ar        = getSystemAccount(ACCOUNTS_RECEIVABLE_CODE);
        ChartOfAccount revenue   = getSystemAccount(SALES_REVENUE_CODE);
        ChartOfAccount discounts = getSystemAccount(SALES_DISCOUNTS_CODE);
        ChartOfAccount taxPayable = getSystemAccount(TAX_PAYABLE_CODE);

        List<JournalEntryService.LinePosting> lines = new ArrayList<>();
        lines.add(new JournalEntryService.LinePosting(ar.getId(), invoice.getTotal(), null, invoice.getInvoiceNumber()));
        if (invoice.getDiscountTotal() != null && invoice.getDiscountTotal().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new JournalEntryService.LinePosting(discounts.getId(), invoice.getDiscountTotal(), null, invoice.getInvoiceNumber()));
        }
        lines.add(new JournalEntryService.LinePosting(revenue.getId(), null, invoice.getSubtotal(), invoice.getInvoiceNumber()));
        if (invoice.getTaxTotal() != null && invoice.getTaxTotal().compareTo(BigDecimal.ZERO) > 0) {
            lines.add(new JournalEntryService.LinePosting(taxPayable.getId(), null, invoice.getTaxTotal(), invoice.getInvoiceNumber()));
        }

        journalEntryService.postEntry(
                invoice.getIssuedDate(),
                "Invoice " + invoice.getInvoiceNumber() + " issued — " + invoice.getCustomer().getName(),
                lines,
                JournalEntry.SourceType.INVOICE_ISSUED,
                invoice.getId(),
                userId);
    }

    private ChartOfAccount getSystemAccount(String code) {
        return accountRepo.findByAccountCode(code)
                .orElseThrow(() -> new BusinessException("Required system account missing: " + code));
    }

    private String generateInvoiceNumber(Order order) {
        long seq = ((Number) em.createNativeQuery("SELECT NEXTVAL('invoice_number_seq')").getSingleResult()).longValue();
        LocalDate today = LocalDate.now();
        String yy  = "%02d".formatted(today.getYear() % 100);
        String mon = today.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ENGLISH);
        return yy + mon + "_" + resolveInvoiceCode(order) + "_" + "%05d".formatted(seq);
    }

    /**
     * Fixed "IT" code concatenated with the first letter of the customer's location
     * (e.g. "Kandy" -> "K"). Falls back to "X" when the customer has no location —
     * invoice numbering must never fail on missing data.
     */
    private String resolveInvoiceCode(Order order) {
        String location = order.getCustomer().getLocation();
        String locationLetter = (location != null && !location.isBlank())
                ? location.trim().substring(0, 1).toUpperCase(Locale.ENGLISH)
                : "X";

        return "IT" + locationLetter;
    }
}
