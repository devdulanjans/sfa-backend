package com.sfa.service;

import com.sfa.dto.order.CreateOrderRequest;
import com.sfa.dto.order.OrderItemRequest;
import com.sfa.dto.order.OrderResponseDto;
import com.sfa.entity.*;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.notification.SalesDocumentEvent;
import com.sfa.repository.*;
import com.sfa.security.TenantContext;
import com.sfa.security.TenantGuard;
import com.sfa.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
import java.util.LinkedHashMap;
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
    private final PromotionRepository    promotionRepo;
    private final ApplicationEventPublisher events;
    private final TenantScope            tenantScope;

    @PersistenceContext
    private EntityManager em;

    public Order createOrder(CreateOrderRequest request, UUID salesRepId) {
        Order order = buildOrder(request, salesRepId, true);
        User salesRep = order.getSalesRep();
        Order.OrderSource orderSource = order.getOrderSource();

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

    /**
     * Prices a cart into an (unsaved) Order — shared by createOrder and quoteAsCustomer, so the
     * price a customer is quoted is computed by exactly the same code that later charges it.
     * {@code assignNumber} false for a quote, which must not consume an order number.
     */
    private Order buildOrder(CreateOrderRequest request, UUID salesRepId, boolean assignNumber) {
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
        // @Filter doesn't cover a by-id lookup — without this, a channel-scoped rep could
        // place an order against another channel's customer by guessing its id. Not needed
        // for a customer-portal order: the customer comes from the login itself, and its
        // record may legitimately sit in a different channel than the one being ordered from.
        if (orderSource != Order.OrderSource.CUSTOMER_APP) {
            customer = TenantGuard.requireVisible(customer, "Customer", customerId);
        }

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
                .orderNumber(assignNumber ? generateOrderNumber() : null)
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
                .poNumber(request.poNumber())
                .build();

        Map<UUID, BigDecimal> purchasedQty = new java.util.LinkedHashMap<>();
        // FREE_PRODUCT promotions the rep explicitly passed over for one of their trigger
        // products (picked a different promotion instead) — collected below, then excluded from
        // resolveFreeItems so that per-line choice is actually exclusive rather than being
        // silently re-granted by the cart-wide free-item step, which otherwise only looks at
        // purchased quantities. See PricingEngine.resolve's promotionId javadoc.
        java.util.Set<UUID> excludedFreePromotionIds = new java.util.LinkedHashSet<>();
        int lineNo = 0;

        for (OrderItemRequest itemReq : request.items()) {
            Product product = productRepo.findById(itemReq.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", itemReq.productId()));
            product = TenantGuard.requireVisible(product, "Product", itemReq.productId());
            // An order belongs to exactly one channel, and every product on it must be available
            // in that channel. A customer-portal session can SEE several channels, so mixed carts
            // go through checkoutAsCustomer, which splits them per channel.
            if (!TenantContext.isUnscoped()
                    && !product.getChannelIds().contains(TenantContext.getTenantId())) {
                throw new BusinessException("\"" + product.getName()
                        + "\" isn't available in this channel — order it separately.");
            }

            PricingEngine.LineItemResult line = pricingEngine.calculateLine(
                    itemReq.productId(),
                    customerId,
                    itemReq.quantity(),
                    // A customer never sets their own discount — promotions are already applied
                    // by the pricing engine, so a client-sent discount would stack on top of it.
                    orderSource == Order.OrderSource.CUSTOMER_APP ? null : itemReq.discountPct(),
                    itemReq.batchPriceId(),
                    itemReq.promotionId());

            if (itemReq.promotionId() != null) {
                promotionRepo.findActivePromotions(itemReq.productId(), customerId, LocalDate.now()).stream()
                        .filter(p -> p.getType() == Promotion.PromotionType.FREE_PRODUCT)
                        .filter(p -> !p.getId().equals(itemReq.promotionId()))
                        .forEach(p -> excludedFreePromotionIds.add(p.getId()));
            }

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
                purchasedQty, customerId, systemSettingService.isShowPromotionAsDiscount(),
                excludedFreePromotionIds)) {
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
        return order;
    }

    /**
     * Customer-app checkout. A customer can order from several channels at once, but each
     * order belongs to exactly one — so the cart is split into one order per channel (in the
     * order the channels first appear in the cart), each created and submitted scoped to just
     * its own channel so pricing, promotions and the order's channel stamp only see that one.
     * All-or-nothing: runs in one transaction, so a failure on any channel creates no orders.
     */
    public List<Order> checkoutAsCustomer(CreateOrderRequest request, UUID userId) {
        requireCustomerAccount(userId);

        Map<UUID, List<OrderItemRequest>> itemsByChannel = groupItemsByChannel(request.items());
        List<Order> orders = new ArrayList<>();
        itemsByChannel.forEach((tenantId, items) -> orders.add(tenantScope.callInChannel(tenantId, () -> {
            Order created = createOrder(channelRequest(request, items, itemsByChannel.size()), userId);
            return submitOrder(created.getId(), userId);
        })));
        return orders;
    }

    /**
     * Customer-app cart quote: prices the cart exactly as {@link #checkoutAsCustomer} would —
     * same split per channel, same pricing engine (customer price → promotion → batch price →
     * default, tier by qty, promotion free items) — without saving anything. The app shows this
     * instead of computing prices itself, so the price shown is always the price charged.
     */
    @Transactional(readOnly = true)
    public List<Order> quoteAsCustomer(CreateOrderRequest request, UUID userId) {
        requireCustomerAccount(userId);
        Map<UUID, List<OrderItemRequest>> itemsByChannel = groupItemsByChannel(request.items());
        List<Order> quotes = new ArrayList<>();
        itemsByChannel.forEach((tenantId, items) -> quotes.add(tenantScope.callInChannel(tenantId, () -> {
            Order quote = buildOrder(channelRequest(request, items, itemsByChannel.size()), userId, false);
            quote.setTenant(em.find(Tenant.class, tenantId));
            return quote;
        })));
        return quotes;
    }

    private void requireCustomerAccount(UUID userId) {
        User user = userRepo.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (!Role.CUSTOMER.equals(user.getRole().getName())) {
            throw new BusinessException("Only available to customer accounts");
        }
    }

    /** Cart lines grouped by the channel each product is ordered in (see ChannelChoice), in first-seen order. */
    private Map<UUID, List<OrderItemRequest>> groupItemsByChannel(List<OrderItemRequest> items) {
        Map<UUID, List<OrderItemRequest>> byChannel = new LinkedHashMap<>();
        for (OrderItemRequest item : items) {
            Product product = productRepo.findById(item.productId())
                    .orElseThrow(() -> new ResourceNotFoundException("Product", item.productId()));
            product = TenantGuard.requireVisible(product, "Product", item.productId());
            UUID channel = com.sfa.security.ChannelChoice.orderChannelFor(product);
            if (channel == null) {
                throw new BusinessException("\"" + product.getName() + "\" isn't available in your channels.");
            }
            byChannel.computeIfAbsent(channel, k -> new ArrayList<>()).add(item);
        }
        return byChannel;
    }

    private static CreateOrderRequest channelRequest(CreateOrderRequest request, List<OrderItemRequest> items,
                                                     int channelCount) {
        return new CreateOrderRequest(
                request.customerId(), items, request.notes(),
                // A distributor is channel-specific — only meaningful when nothing was split.
                channelCount == 1 ? request.distributorId() : null,
                request.deliveryAddressLabel(), request.deliveryAddressLine(),
                request.customerSignature(), request.salespersonSignature(), request.poNumber());
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
        Order saved = orderRepo.save(order);
        // Auto-approved orders returned above get the invoice notification instead, so the
        // customer receives one message per order either way.
        events.publishEvent(SalesDocumentEvent.orderSubmitted(saved.getId()));
        return saved;
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
    public Page<OrderResponseDto> getOrders(UUID userId, UUID linkedCustomerId, UUID linkedDistributorId, String roleName,
                                  String status, String source,
                                  String orderNo, String invoiceNo,
                                  LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        Specification<Order> spec = buildOrderSpec(userId, linkedCustomerId, linkedDistributorId, roleName,
                status, source, orderNo, invoiceNo, dateFrom, dateTo);

        Page<Order> page = orderRepo.findAll(spec, pageable);
        List<UUID> orderIds = page.getContent().stream().map(Order::getId).toList();
        Map<UUID, String> invoiceNumbers = invoiceService.getInvoiceNumbersForOrders(orderIds);
        return page.map(o -> OrderResponseDto.from(o, invoiceNumbers.get(o.getId())));
    }

    /** Same filters as {@link #getOrders}, unpaged entities — used by the bulk
     *  full-detail (one row per product line) export, which needs the actual
     *  Order/OrderItem/Product entities rather than the flattened response DTO. */
    @Transactional(readOnly = true)
    public List<Order> getOrdersForExport(UUID userId, UUID linkedCustomerId, UUID linkedDistributorId, String roleName,
                                  String status, String source,
                                  String orderNo, String invoiceNo,
                                  LocalDate dateFrom, LocalDate dateTo) {
        Specification<Order> spec = buildOrderSpec(userId, linkedCustomerId, linkedDistributorId, roleName,
                status, source, orderNo, invoiceNo, dateFrom, dateTo);
        return orderRepo.findAll(spec);
    }

    private Specification<Order> buildOrderSpec(UUID userId, UUID linkedCustomerId, UUID linkedDistributorId, String roleName,
                                  String status, String source,
                                  String orderNo, String invoiceNo,
                                  LocalDate dateFrom, LocalDate dateTo) {
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

        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // CUSTOMER role always sees only their own orders; DISTRIBUTOR only orders placed
            // under it; SALES_REP only their own — source filter only applies for
            // SUPER_ADMIN/SALES_MANAGER (matches prior behavior)
            if (Role.CUSTOMER.equals(roleName) && linkedCustomerId != null) {
                predicates.add(cb.equal(root.get("customer").get("id"), linkedCustomerId));
            } else if (Role.DISTRIBUTOR.equals(roleName) && linkedDistributorId != null) {
                predicates.add(cb.equal(root.get("distributor").get("id"), linkedDistributorId));
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
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @Transactional(readOnly = true)
    public Order getOrder(UUID id) {
        Order order = orderRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Order", id));
        // @Filter doesn't cover a by-id lookup (only HQL/Criteria queries) — without this,
        // any channel could fetch/submit/approve/cancel another channel's order by guessing its id.
        return TenantGuard.requireVisible(order, "Order", id);
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
