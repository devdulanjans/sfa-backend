package com.sfa.service;

import com.sfa.dto.order.CreateOrderRequest;
import com.sfa.dto.order.OrderItemRequest;
import com.sfa.dto.order.OrderResponseDto;
import com.sfa.entity.*;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository        orderRepo;
    private final CustomerRepository     customerRepo;
    private final ProductRepository      productRepo;
    private final UserRepository         userRepo;
    private final DistributorRepository  distributorRepo;
    private final PricingEngine          pricingEngine;
    private final AuditLogService        auditLog;
    private final SystemSettingService   systemSettingService;
    private final InvoiceService         invoiceService;
    private final InventoryService       inventoryService;

    @PersistenceContext
    private EntityManager em;

    public Order createOrder(CreateOrderRequest request, UUID salesRepId) {
        User salesRep = userRepo.findById(salesRepId)
                .orElseThrow(() -> new ResourceNotFoundException("User", salesRepId));

        UUID resolvedCustomerId = request.customerId();
        Order.OrderSource orderSource = Order.OrderSource.SALES_REP;

        if (Role.CUSTOMER.equals(salesRep.getRole().getName())) {
            if (salesRep.getCustomer() == null) {
                throw new BusinessException("Your account is not linked to a customer record");
            }
            resolvedCustomerId = salesRep.getCustomer().getId();
            orderSource = Order.OrderSource.CUSTOMER_APP;
        }

        // Final alias required for use inside lambda expressions
        final UUID customerId = resolvedCustomerId;

        Customer customer = customerRepo.findById(customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));

        if (Role.SALES_REP.equals(salesRep.getRole().getName())) {
            Set<UUID> assignedIds = salesRep.getAssignedCustomers().stream()
                    .map(Customer::getId)
                    .collect(Collectors.toSet());
            if (!assignedIds.isEmpty() && !assignedIds.contains(customerId)) {
                throw new BusinessException("You are not assigned to customer: " + customer.getName());
            }
        }

        Distributor distributor = request.distributorId() != null
                ? distributorRepo.getReferenceById(request.distributorId())
                : null;

        Order order = Order.builder()
                .orderNumber(generateOrderNumber())
                .customer(customer)
                .salesRep(salesRep)
                .distributor(distributor)
                .status(Order.OrderStatus.DRAFT)
                .orderSource(orderSource)
                .orderDate(Instant.now())
                .notes(request.notes())
                .deliveryAddressLabel(request.deliveryAddressLabel())
                .deliveryAddressLine(request.deliveryAddressLine())
                .customerSignature(request.customerSignature())
                .salespersonSignature(request.salespersonSignature())
                .build();

        Map<UUID, BigDecimal> purchasedQty = new java.util.LinkedHashMap<>();
        int lineNo = 0;

        for (OrderItemRequest itemReq : request.items()) {
            Product product = productRepo.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.productId()));

            PricingEngine.LineItemResult line = pricingEngine.calculateLine(
                    itemReq.productId(),
                    customerId,
                    itemReq.quantity(),
                    itemReq.discountPct());

            OrderItem item = OrderItem.builder()
                    .product(product)
                    .quantity(itemReq.quantity())
                    .unitPrice(line.unitPrice())
                    .discountPct(line.discountPct())
                    .discountAmount(line.discountAmount())
                    .taxPct(line.taxPct())
                    .taxAmount(line.taxAmount())
                    .lineTotal(line.lineTotal())
                    .priceSource(line.priceSource())
                    .promotionName(line.promotionName())
                    .lineNo(lineNo++)
                    .build();
            item.calculate();
            order.addItem(item);

            purchasedQty.merge(itemReq.productId(), itemReq.quantity(), BigDecimal::add);
        }

        // Free items earned by promotions are computed authoritatively here, server-side —
        // any client-submitted "free" line in request.items() above was priced normally like
        // any other line and is ignored for this purpose.
        for (PricingEngine.FreeLineResult free : pricingEngine.resolveFreeItems(
                purchasedQty, customerId, systemSettingService.isShowPromotionAsDiscount())) {
            Product freeProduct = productRepo.findById(free.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", free.productId()));

            OrderItem freeItem = OrderItem.builder()
                    .product(freeProduct)
                    .quantity(free.quantity())
                    .unitPrice(free.unitPrice())
                    .discountPct(free.discountPct())
                    .discountAmount(free.discountAmount())
                    .taxPct(free.taxPct())
                    .taxAmount(free.taxAmount())
                    .lineTotal(free.lineTotal())
                    .priceSource("FREE_PRODUCT")
                    .promotionName(free.promotionName())
                    .lineNo(lineNo++)
                    .build();
            order.addItem(freeItem);
        }

        order.recalculateTotals();
        Order saved = orderRepo.save(order);
        auditLog.log(salesRepId, "CREATE", "ORDER", saved.getId(), null,
                Map.of("orderNumber", saved.getOrderNumber(),
                       "status",      saved.getStatus().name(),
                       "source",      saved.getOrderSource().name(),
                       "total",       saved.getTotal()));

        if (systemSettingService.isOrderPrevent() && orderSource != Order.OrderSource.CUSTOMER_APP) {
            saved.setStatus(Order.OrderStatus.APPROVED);
            saved.setApprovedBy(salesRep);
            saved.setApprovedAt(Instant.now());
            saved = orderRepo.save(saved);
            auditLog.log(salesRepId, "AUTO_APPROVE", "ORDER", saved.getId(), Order.OrderStatus.DRAFT, Order.OrderStatus.APPROVED);
            invoiceService.generateInvoice(saved.getId(), salesRepId);
        }

        return saved;
    }

    public void deleteDraftOrder(UUID orderId, UUID userId) {
        Order order = getOrder(orderId);
        if (!order.getSalesRep().getId().equals(userId)) {
            throw new BusinessException("You can only delete your own orders");
        }
        if (order.getStatus() != Order.OrderStatus.DRAFT) {
            throw new BusinessException("Only DRAFT orders can be deleted");
        }
        auditLog.log(userId, "DELETE", "ORDER", orderId, Order.OrderStatus.DRAFT, null);
        orderRepo.delete(order);
    }

    public Order submitOrder(UUID orderId, UUID userId) {
        Order order = getOrder(orderId);
        validateOwnership(order, userId);
        // If the order was auto-approved on creation (isOrderPrevent=true), it is already
        // past DRAFT. Return it as-is so the mobile two-step flow (create→submit) always succeeds.
        if (order.getStatus() != Order.OrderStatus.DRAFT) {
            return order;
        }
        order.setStatus(Order.OrderStatus.SUBMITTED);
        auditLog.log(userId, "SUBMIT", "ORDER", orderId, Order.OrderStatus.DRAFT, Order.OrderStatus.SUBMITTED);
        return orderRepo.save(order);
    }

    public Order approveOrder(UUID orderId, UUID managerId) {
        Order order = getOrder(orderId);
        if (order.getStatus() != Order.OrderStatus.SUBMITTED) {
            throw new BusinessException("Only SUBMITTED orders can be approved");
        }
        User manager = userRepo.findById(managerId).orElseThrow();
        order.setStatus(Order.OrderStatus.APPROVED);
        order.setApprovedBy(manager);
        order.setApprovedAt(Instant.now());
        auditLog.log(managerId, "APPROVE", "ORDER", orderId, Order.OrderStatus.SUBMITTED, Order.OrderStatus.APPROVED);
        Order saved = orderRepo.save(order);
        inventoryService.deductForOrder(saved, managerId);
        return saved;
    }

    public Order cancelOrder(UUID orderId, UUID userId) {
        Order order = getOrder(orderId);
        if (order.getStatus() == Order.OrderStatus.INVOICED) {
            throw new BusinessException("Invoiced orders cannot be cancelled");
        }
        Order.OrderStatus previous = order.getStatus();
        order.setStatus(Order.OrderStatus.CANCELLED);
        auditLog.log(userId, "CANCEL", "ORDER", orderId, previous, Order.OrderStatus.CANCELLED);
        Order saved = orderRepo.save(order);
        if (previous == Order.OrderStatus.APPROVED) {
            inventoryService.restoreForOrder(saved, userId);
        }
        return saved;
    }

    @Transactional(readOnly = true)
    public Page<OrderResponseDto> getOrders(UUID userId, UUID linkedCustomerId, String roleName,
                                  String status, String source,
                                  String orderNo, String invoiceNo,
                                  LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        Order.OrderStatus orderStatus = null;
        if (status != null && !status.isBlank()) {
            try { orderStatus = Order.OrderStatus.valueOf(status); } catch (IllegalArgumentException ignored) {}
        }
        Order.OrderSource orderSource = null;
        if (source != null && !source.isBlank()) {
            try { orderSource = Order.OrderSource.valueOf(source); } catch (IllegalArgumentException ignored) {}
        }

        final Order.OrderStatus statusFilter = orderStatus;
        final Order.OrderSource sourceFilter = orderSource;
        final Instant fromInst = dateFrom != null ? dateFrom.atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        final Instant toInst   = dateTo   != null ? dateTo.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant() : null;
        final String ordNo = blank(orderNo);
        final String invNo = blank(invoiceNo);

        Specification<Order> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // CUSTOMER role always sees only their own orders; SALES_REP only their own —
            // source filter only applies for SUPER_ADMIN/SALES_MANAGER (matches prior behavior)
            if (Role.CUSTOMER.equals(roleName) && linkedCustomerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), linkedCustomerId));
            } else if (Role.SALES_REP.equals(roleName)) {
                predicates.add(cb.equal(root.get("salesRep").get("id"), userId));
            } else if (sourceFilter != null) {
                predicates.add(cb.equal(root.get("orderSource"), sourceFilter));
            }

            if (statusFilter != null)
                predicates.add(cb.equal(root.get("status"), statusFilter));
            if (fromInst != null)
                predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), fromInst));
            if (toInst != null)
                predicates.add(cb.lessThan(root.get("orderDate"), toInst));
            if (ordNo != null)
                predicates.add(cb.like(cb.lower(root.get("orderNumber")), "%" + ordNo.toLowerCase() + "%"));
            if (invNo != null) {
                Subquery<UUID> sub = query.subquery(UUID.class);
                Root<Invoice> invRoot = sub.from(Invoice.class);
                sub.select(invRoot.get("order").get("id"))
                   .where(cb.like(cb.lower(invRoot.get("invoiceNumber")), "%" + invNo.toLowerCase() + "%"));
                predicates.add(root.get("id").in(sub));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Order> page = orderRepo.findAll(spec, pageable);
        List<UUID> orderIds = page.getContent().stream().map(Order::getId).toList();
        Map<UUID, String> invoiceNumbers = invoiceService.getInvoiceNumbersForOrders(orderIds);
        return page.map(o -> OrderResponseDto.from(o, invoiceNumbers.get(o.getId())));
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID id) {
        return orderRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
    }

    private void validateOwnership(Order order, UUID userId) {
        if (!order.getSalesRep().getId().equals(userId)) {
            throw new BusinessException("You can only modify your own orders");
        }
    }

    private String generateOrderNumber() {
        Object raw = em.createNativeQuery("SELECT NEXTVAL('order_number_seq')").getSingleResult();
        long next = ((Number) raw).longValue();
        return "ORD-%d-%05d".formatted(Year.now().getValue(), next);
    }
}
