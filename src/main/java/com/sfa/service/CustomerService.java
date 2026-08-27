package com.sfa.service;

import com.sfa.dto.customer.AddressRequest;
import com.sfa.dto.customer.CreateCustomerRequest;
import com.sfa.dto.customer.CustomerAnalyticsDto;
import com.sfa.dto.customer.CustomerBranchSummaryDto;
import com.sfa.dto.customer.CustomerDto;
import com.sfa.dto.customer.QuickCreateCustomerRequest;
import com.sfa.dto.product.ProductDto;
import com.sfa.entity.Customer;
import com.sfa.entity.CustomerAddress;
import com.sfa.entity.CustomerCategory;
import com.sfa.entity.Order;
import com.sfa.entity.Product;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerCategoryRepository;
import com.sfa.repository.CustomerGroupRepository;
import com.sfa.repository.CustomerRepository;
import com.sfa.security.TenantGuard;
import com.sfa.repository.OrderRepository;
import com.sfa.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerCategoryRepository categoryRepository;
    private final CustomerGroupRepository customerGroupRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final TenantAccessService tenantAccessService;
    private final AuditLogService auditLogService;

    @PersistenceContext
    private EntityManager em;

    /**
     * @param restrictedIds non-null non-empty → only return customers in that set;
     *                      null or empty → return all customers
     * @param topLevelOnly  excludes branches (customers with a parentCustomer) — callers should
     *                      only pass true when search is blank, so a branch is still directly
     *                      findable by name/code once the user actually searches for it
     */
    public Page<CustomerDto> list(String search, Set<UUID> restrictedIds, boolean includeDeleted, boolean topLevelOnly, Pageable pageable) {
        String q = search == null ? "" : search;
        Page<Customer> page = (restrictedIds != null && !restrictedIds.isEmpty())
                ? customerRepository.searchWithinIds(q, includeDeleted, topLevelOnly, restrictedIds, pageable)
                : customerRepository.search(q, includeDeleted, topLevelOnly, pageable);

        List<CustomerDto> dtos = toDtos(page.getContent());
        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public Page<CustomerDto> listBranches(UUID parentId, String search, Pageable pageable) {
        findOrThrow(parentId); // 404s cleanly if the parent doesn't exist
        String q = search == null ? "" : search;
        Page<Customer> page = customerRepository.searchByParentCustomerId(parentId, q, pageable);
        List<CustomerDto> dtos = toDtos(page.getContent());
        return new org.springframework.data.domain.PageImpl<>(dtos, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CustomerBranchSummaryDto getBranchSummary(UUID parentId) {
        findOrThrow(parentId);
        long branchCount = customerRepository.countBranchesForCustomers(List.of(parentId)).stream()
                .filter(row -> row.getParentId().equals(parentId))
                .findFirst()
                .map(CustomerRepository.BranchCountRow::getCount)
                .orElse(0L);
        BigDecimal totalOutstanding = customerRepository.sumOutstandingForParentAndBranches(parentId);
        return new CustomerBranchSummaryDto((int) branchCount, totalOutstanding);
    }

    public CustomerDto getById(UUID id) {
        return toDtos(List.of(findOrThrow(id))).get(0);
    }

    /**
     * Bulk-builds DTOs for a list of customers, populating both {@code assignedProductIds}
     * (direct-only — what the admin "Select Products" picker reads/writes back, must never
     * silently include group-derived products) and {@code effectiveAssignedProductIds} (direct
     * union every customer group the customer belongs to — what mobile's order-creation catalog
     * filter reads). This is the single place that computes the group-inclusive value; reused by
     * {@link #list} and {@link #getById} and by {@code SyncController} so all three stay
     * consistent. assignedProducts/customer-group-membership are both lazy @ManyToMany — touching
     * them per-row here would either N+1 or (per CustomerDto's no-arg overload) silently come back
     * empty, so both are bulk-loaded in one query each regardless of how many customers are passed in.
     */
    public List<CustomerDto> toDtos(List<Customer> customers) {
        List<UUID> customerIds = customers.stream().map(Customer::getId).toList();
        Map<UUID, List<UUID>> directByCustomer = new java.util.HashMap<>();
        Map<UUID, List<UUID>> groupByCustomer = new java.util.HashMap<>();
        if (!customerIds.isEmpty()) {
            for (var row : customerRepository.findAssignedProductIdsForCustomers(customerIds)) {
                directByCustomer
                        .computeIfAbsent(row.getCustomerId(), k -> new java.util.ArrayList<>())
                        .add(row.getProductId());
            }
            for (var row : customerGroupRepository.findAssignedProductIdsForCustomers(customerIds)) {
                groupByCustomer
                        .computeIfAbsent(row.getCustomerId(), k -> new java.util.ArrayList<>())
                        .add(row.getProductId());
            }
        }

        // branchCount: one grouped query for however many of these customers are parents.
        Map<UUID, Long> branchCountByParent = new java.util.HashMap<>();
        if (!customerIds.isEmpty()) {
            for (var row : customerRepository.countBranchesForCustomers(customerIds)) {
                branchCountByParent.put(row.getParentId(), row.getCount());
            }
        }

        // parentCustomerName: getId() on a lazy proxy is safe (see CustomerDto.from), but the
        // name requires actually loading the parent — one bulk findAllById rather than N+1.
        List<UUID> parentIds = customers.stream()
                .map(c -> c.getParentCustomer() != null ? c.getParentCustomer().getId() : null)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        Map<UUID, String> parentNameById = parentIds.isEmpty() ? Map.of()
                : customerRepository.findAllById(parentIds).stream()
                        .collect(java.util.stream.Collectors.toMap(Customer::getId, Customer::getName));

        return customers.stream().map(c -> {
            List<UUID> direct = directByCustomer.getOrDefault(c.getId(), List.of());
            java.util.Set<UUID> effective = new java.util.LinkedHashSet<>(direct);
            effective.addAll(groupByCustomer.getOrDefault(c.getId(), List.of()));
            UUID parentId = c.getParentCustomer() != null ? c.getParentCustomer().getId() : null;
            String parentName = parentId != null ? parentNameById.get(parentId) : null;
            int branchCount = branchCountByParent.getOrDefault(c.getId(), 0L).intValue();
            return CustomerDto.from(c, direct, List.copyOf(effective), parentName, branchCount);
        }).toList();
    }

    @Transactional
    public CustomerDto create(CreateCustomerRequest req, UUID actingUserId) {
        if (customerRepository.findByCustomerCode(req.customerCode()).isPresent()) {
            throw new BusinessException("Customer code already exists: " + req.customerCode());
        }
        Customer c = new Customer();
        applyFields(c, req);
        tenantAccessService.resolveExplicitTenant(req.tenantId(), actingUserId, "customer")
                .ifPresent(c::setTenant);
        Customer saved = customerRepository.save(c);
        auditLogService.log(null, "CREATE", "Customer", saved.getId(), null, saved);
        return CustomerDto.from(saved);
    }

    @Transactional
    public CustomerDto update(UUID id, CreateCustomerRequest req) {
        Customer c = findOrThrow(id);
        Object before = CustomerDto.from(c);
        applyFields(c, req);
        Customer saved = customerRepository.save(c);
        auditLogService.log(null, "UPDATE", "Customer", saved.getId(), before, CustomerDto.from(saved));
        return CustomerDto.from(saved);
    }

    @Transactional
    public void softDelete(UUID id) {
        Customer c = findOrThrow(id);
        if (c.getDeletedAt() == null) {
            c.setDeletedAt(Instant.now());
            customerRepository.save(c);
            auditLogService.log(null, "DELETE", "Customer", c.getId(), null, null);
        }
    }

    @Transactional
    public void restore(UUID id) {
        Customer c = findOrThrow(id);
        c.setDeletedAt(null);
        customerRepository.save(c);
        auditLogService.log(null, "RESTORE", "Customer", c.getId(), null, null);
    }

    @Transactional
    public CustomerDto quickCreate(QuickCreateCustomerRequest req) {
        Customer c = new Customer();
        c.setCustomerCode(generatePosCustomerCode(req.name()));
        c.setName(req.name());
        c.setPhone(req.phone());
        c.setSource(Customer.CustomerSource.POS);
        Customer saved = customerRepository.save(c);
        auditLogService.log(null, "CREATE", "Customer", saved.getId(), null, saved);
        return CustomerDto.from(saved);
    }

    public Page<CustomerDto> listPosCustomers(String search, Pageable pageable) {
        String q = search == null ? "" : search;
        return customerRepository.searchPosCustomers(q, pageable).map(CustomerDto::from);
    }

    private String generatePosCustomerCode(String name) {
        String base = name == null ? "" : name.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        String prefix = base.isEmpty() ? "CUST" : base.substring(0, Math.min(4, base.length()));
        Number seq = (Number) em.createNativeQuery("SELECT NEXTVAL('customer_pos_seq')").getSingleResult();
        return prefix + "-" + String.format("%04d", seq.longValue());
    }

    private void applyFields(Customer c, CreateCustomerRequest req) {
        c.setCustomerCode(req.customerCode());
        c.setName(req.name());
        c.setContactPerson(req.contactPerson());
        c.setPhone(req.phone());
        c.setEmail(req.email());
        c.setLocation(req.location());
        c.setPlaceOfSupplier(req.placeOfSupplier());
        c.setTaxNumber(req.taxNumber());

        // Customer.addresses has a @Builder.Default initializer, which Lombok only applies via
        // .builder().build() — plain `new Customer()` (used here) leaves it null, so initialize
        // it on create. On update, c is a Hibernate-managed entity with a live, tracked
        // collection — replacing that reference (rather than clearing it in place) orphans the
        // original persistent collection and Hibernate throws
        // "A collection with cascade=all-delete-orphan was no longer referenced" on flush.
        if (c.getAddresses() == null) {
            c.setAddresses(new java.util.ArrayList<>());
        } else {
            c.getAddresses().clear();
        }
        for (int i = 0; i < req.addresses().size(); i++) {
            AddressRequest a = req.addresses().get(i);
            CustomerAddress addr = new CustomerAddress();
            addr.setLabel(a.label());
            addr.setAddressLine(a.addressLine());
            addr.setPrimary(i == 0);
            addr.setSortOrder(i);
            addr.setCustomer(c);
            c.getAddresses().add(addr);
        }
        if (req.taxType() != null) {
            c.setTaxType(Customer.TaxType.valueOf(req.taxType()));
        }
        if (req.taxRate() != null) {
            c.setTaxRate(req.taxRate());
        }
        if (req.categoryId() != null) {
            CustomerCategory cat = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("CustomerCategory", req.categoryId()));
            c.setCategory(cat);
        }
        if (req.visibilityRule() != null) {
            c.setVisibilityRule(Customer.VisibilityRule.valueOf(req.visibilityRule()));
        }
        c.setCreditLimit(req.creditLimit());
        c.setCreditDays(req.creditDays());
        if (req.source() != null) {
            c.setSource(Customer.CustomerSource.valueOf(req.source()));
        }

        // Single-level branch hierarchy: a branch can never itself become a parent, and a
        // parent (has its own branches) can never become someone else's branch.
        if (req.parentCustomerId() != null) {
            Customer parent = customerRepository.findById(req.parentCustomerId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", req.parentCustomerId()));
            if (c.getId() != null && parent.getId().equals(c.getId())) {
                throw new BusinessException("A customer cannot be its own parent");
            }
            if (parent.getParentCustomer() != null) {
                throw new BusinessException("'" + parent.getName() + "' is itself a branch and cannot have branches of its own");
            }
            if (c.getId() != null && customerRepository.existsByParentCustomerId(c.getId())) {
                throw new BusinessException("This customer already has branches and cannot become a branch itself");
            }
            c.setParentCustomer(parent);
        } else {
            c.setParentCustomer(null);
        }
    }

    public CustomerAnalyticsDto getAnalytics(UUID customerId) {
        Customer customer = findOrThrow(customerId);

        long totalOrders = orderRepository.countByCustomer(customerId);
        BigDecimal totalRevenue = orderRepository.sumTotalByCustomer(customerId);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;
        BigDecimal avgOrderValue = totalOrders > 0
                ? totalRevenue.divide(BigDecimal.valueOf(totalOrders), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        Instant lastOrderDate = orderRepository.lastOrderDateByCustomer(customerId).orElse(null);

        List<Object[]> statusRows = orderRepository.statusBreakdownByCustomer(customerId);
        Map<String, Long> statusBreakdown = new LinkedHashMap<>();
        for (Object[] row : statusRows) {
            statusBreakdown.put(((Order.OrderStatus) row[0]).name(), ((Number) row[1]).longValue());
        }

        Instant threeMonthsAgo = Instant.now().minus(90, ChronoUnit.DAYS);
        List<Object[]> productRows = orderRepository.customerTopProducts(customerId, threeMonthsAgo);
        List<CustomerAnalyticsDto.ProductStat> topProducts = productRows.stream()
                .map(row -> new CustomerAnalyticsDto.ProductStat(
                        (String) row[0],
                        (String) row[1],
                        row[2] != null ? (String) row[2] : "",
                        row[3] != null ? (BigDecimal) row[3] : BigDecimal.ZERO,
                        row[4] != null ? (BigDecimal) row[4] : BigDecimal.ZERO))
                .toList();

        Instant sixMonthsAgo = Instant.now().minus(180, ChronoUnit.DAYS);
        List<Object[]> monthlyRows = orderRepository.customerMonthlyRevenue(customerId, sixMonthsAgo);
        List<CustomerAnalyticsDto.MonthlyRevenue> monthlyTrend = monthlyRows.stream()
                .map(row -> new CustomerAnalyticsDto.MonthlyRevenue(
                        String.valueOf(row[0]),
                        row[1] instanceof BigDecimal bd ? bd : new BigDecimal(row[1].toString()),
                        ((Number) row[2]).longValue()))
                .toList();

        return new CustomerAnalyticsDto(
                totalOrders,
                totalRevenue,
                avgOrderValue,
                lastOrderDate,
                customer.getCreditLimit() != null ? customer.getCreditLimit() : BigDecimal.ZERO,
                customer.getCurrentBalance() != null ? customer.getCurrentBalance() : BigDecimal.ZERO,
                topProducts,
                monthlyTrend,
                statusBreakdown);
    }

    public Page<Order> getCustomerOrders(UUID customerId, Pageable pageable) {
        return orderRepository.findByCustomerIdOrderByOrderDateDesc(customerId, pageable);
    }

    @Transactional(readOnly = true)
    public List<ProductDto> getAssignedProducts(UUID customerId) {
        Customer c = findOrThrow(customerId);
        return c.getAssignedProducts().stream()
                .map(ProductDto::from)
                .sorted(java.util.Comparator.comparing(p -> p.name() == null ? "" : p.name()))
                .toList();
    }

    @Transactional
    public void setAssignedProducts(UUID customerId, Set<UUID> productIds) {
        Customer c = findOrThrow(customerId);
        Set<Product> products = new java.util.HashSet<>(productRepository.findAllById(productIds));
        c.setAssignedProducts(products);
        // Modifying only the join-table-backed collection doesn't dirty any
        // scalar column on the customer row, so Hibernate can skip the UPDATE
        // entirely and @LastModifiedDate never fires — meaning incremental
        // delta sync (keyed on updatedAt) would never pick up this change,
        // leaving mobile's cached assignedProductIds stale until a full
        // resync. Bump it explicitly so the change is always sync-visible.
        c.setUpdatedAt(Instant.now());
        customerRepository.save(c);
    }

    private Customer findOrThrow(UUID id) {
        Customer c = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", id));
        // @Filter doesn't cover a by-id lookup (only HQL/Criteria queries) — without this,
        // any channel could fetch/edit another channel's customer by guessing its id.
        return TenantGuard.requireVisible(c, "Customer", id);
    }
}
