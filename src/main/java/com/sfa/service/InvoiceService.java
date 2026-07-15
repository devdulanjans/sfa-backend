package com.sfa.service;

import com.sfa.dto.invoice.InvoiceSummaryDto;
import com.sfa.entity.Invoice;
import com.sfa.entity.Order;
import com.sfa.entity.OrderItem;
import com.sfa.entity.Product;
import com.sfa.entity.ProductCategory;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.InvoiceRepository;
import com.sfa.repository.OrderRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collection;
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

    private final InvoiceRepository     invoiceRepo;
    private final OrderRepository       orderRepo;
    private final InvoicePdfGenerator   pdfGenerator;
    private final MinioStorageService   storage;
    private final AuditLogService       auditLog;

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
            LocalDate dueTo
    ) {}

    @Transactional(readOnly = true)
    public Page<InvoiceSummaryDto> listInvoices(InvoiceFilter f, Pageable pageable) {
        final Instant createdFromInst = f.createdFrom() != null
                ? f.createdFrom().atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        final Instant createdToInst   = f.createdTo()   != null
                ? f.createdTo().plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;

        Specification<Invoice> spec = (root, query, cb) -> {
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return invoiceRepo.findAll(spec, pageable).map(InvoiceSummaryDto::from);
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    /** Bulk order-id → invoice-number lookup, used to annotate order list rows. */
    @Transactional(readOnly = true)
    public Map<UUID, String> getInvoiceNumbersForOrders(Collection<UUID> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        return invoiceRepo.findByOrderIdIn(orderIds).stream()
                .collect(Collectors.toMap(inv -> inv.getOrder().getId(), Invoice::getInvoiceNumber));
    }

    public Invoice generateInvoice(UUID orderId, UUID userId) {
        Order order = orderRepo.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Order", orderId));

        if (order.getStatus() != Order.OrderStatus.APPROVED) {
            throw new BusinessException("Only APPROVED orders can be invoiced");
        }
        if (invoiceRepo.existsByOrderId(orderId)) {
            throw new BusinessException("Invoice already exists for this order");
        }

        Invoice invoice = Invoice.builder()
                .invoiceNumber(generateInvoiceNumber(order))
                .order(order)
                .customer(order.getCustomer())
                .issuedDate(LocalDate.now())
                .dueDate(LocalDate.now().plusDays(30))
                .subtotal(order.getSubtotal())
                .taxTotal(order.getTaxAmount())
                .discountTotal(order.getDiscountAmount())
                .total(order.getTotal())
                .status(Invoice.InvoiceStatus.ISSUED)
                .createdBy(userId)
                .build();

        Invoice saved = invoiceRepo.save(invoice);

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

        // Update order status
        order.setStatus(Order.OrderStatus.INVOICED);
        orderRepo.save(order);

        auditLog.log(userId, "GENERATE", "INVOICE", saved.getId(), null,
                Map.of("invoiceNumber", saved.getInvoiceNumber(),
                       "status",        saved.getStatus().name(),
                       "total",         saved.getTotal()));
        return saved;
    }

    @Transactional
    public byte[] getPdfBytes(UUID invoiceId) {
        Invoice invoice = invoiceRepo.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", invoiceId));

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

    public Invoice recordPrint(UUID invoiceId) {
        Invoice invoice = getInvoice(invoiceId);
        invoice.incrementPrintCount();
        return invoiceRepo.save(invoice);
    }

    @Transactional(readOnly = true)
    public Invoice getInvoice(UUID id) {
        return invoiceRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", id));
    }

    @Transactional(readOnly = true)
    public Invoice getInvoiceByOrder(UUID orderId) {
        return invoiceRepo.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice for order", orderId));
    }

    private String generateInvoiceNumber(Order order) {
        long seq = ((Number) em.createNativeQuery("SELECT NEXTVAL('invoice_number_seq')").getSingleResult()).longValue();
        LocalDate today = LocalDate.now();
        String yy  = "%02d".formatted(today.getYear() % 100);
        String mon = today.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH).toUpperCase(Locale.ENGLISH);
        return yy + mon + "_" + resolveInvoiceCode(order) + "_" + "%05d".formatted(seq);
    }

    /**
     * Category code (e.g. "IT" for Iceman Products, "YA" for Yara Product) of the first
     * item added to the cart, concatenated with the first letter of the customer's location
     * (e.g. "Kandy" -> "K"). Falls back to "IT" when the category has no code set, and "X"
     * when the customer has no location — invoice numbering must never fail on missing data.
     */
    private String resolveInvoiceCode(Order order) {
        String categoryCode = order.getItems().stream()
                .findFirst()
                .map(OrderItem::getProduct)
                .map(Product::getCategory)
                .map(ProductCategory::getCode)
                .filter(c -> c != null && !c.isBlank())
                .orElse("IT")
                .trim().toUpperCase(Locale.ENGLISH);
        // Cap so a future admin-entered code (up to 10 chars) can never push the
        // whole invoice number past its 20-char column limit.
        categoryCode = categoryCode.substring(0, Math.min(categoryCode.length(), 6));

        String location = order.getCustomer().getLocation();
        String locationLetter = (location != null && !location.isBlank())
                ? location.trim().substring(0, 1).toUpperCase(Locale.ENGLISH)
                : "X";

        return categoryCode + locationLetter;
    }
}
