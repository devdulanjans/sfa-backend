package com.sfa.service;

import com.sfa.dto.product.CreateProductRequest;
import com.sfa.dto.pricing.CustomerPromotionDto;
import com.sfa.dto.product.CustomerCatalogItemDto;
import com.sfa.dto.product.ProductDto;
import com.sfa.dto.tenant.BulkAssignResultDto;
import com.sfa.dto.tenant.BulkAssignTenantRequest;
import com.sfa.entity.BatchPrice;
import com.sfa.entity.Product;
import com.sfa.entity.ProductCategory;
import com.sfa.entity.StockLevel;
import com.sfa.entity.Tenant;
import com.sfa.entity.Unit;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.BatchPriceRepository;
import com.sfa.repository.PromotionRepository;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.ProductCategoryRepository;
import com.sfa.repository.ProductRepository;
import com.sfa.repository.StockLevelRepository;
import com.sfa.repository.UnitRepository;
import com.sfa.security.TenantGuard;
import com.sfa.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final CustomerRepository customerRepository;
    private final StockLevelRepository stockLevelRepository;
    private final TenantAccessService tenantAccessService;
    private final AuditLogService auditLogService;
    private final PricingEngine pricingEngine;
    private final BatchPriceRepository batchPriceRepository;
    private final TenantScope tenantScope;
    private final PromotionRepository promotionRepository;
    private final ChannelMembershipService channelMembershipService;
    private final com.sfa.repository.TenantRepository tenantRepository;

    public Page<ProductDto> list(String search, Pageable pageable) {
        Page<Product> page = productRepository.search(search == null ? "" : search, pageable);

        List<UUID> ids = page.getContent().stream().map(Product::getId).toList();
        Map<UUID, StockLevel> stockByProductId = stockLevelRepository.findByProductIdIn(ids).stream()
                .collect(Collectors.toMap(StockLevel::getProductId, s -> s));

        return page.map(p -> ProductDto.from(p, stockByProductId.get(p.getId())));
    }

    @Transactional(readOnly = true)
    public List<ProductDto> visibleToCustomer(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", customerId);
        }

        // Assignment itself is the signal that restricts the catalog — not the
        // visibilityRule flag, which can drift out of sync with the actual
        // assignment list (e.g. products assigned without also flipping the
        // flag). A customer with no assigned products (directly or via a
        // customer group they belong to) always sees the full active catalog;
        // any assignment narrows it to just those products.
        Set<Product> assigned = new LinkedHashSet<>(productRepository.findAssignedToCustomer(customerId));
        assigned.addAll(productRepository.findAssignedToCustomerViaGroups(customerId));
        List<Product> products = assigned.isEmpty()
                ? productRepository.findByStatusOrderByName(Product.ProductStatus.ACTIVE)
                : assigned.stream().sorted(Comparator.comparing(Product::getName)).toList();

        return products.stream().map(ProductDto::from).toList();
    }

    /**
     * The customer app's catalog: {@link #visibleToCustomer} plus the one price this customer
     * pays per unit, resolved by the same PricingEngine that prices the order — the customer
     * never chooses between batch-price tiers. Each product is priced scoped to its own channel,
     * matching how checkout prices it.
     */
    @Transactional(readOnly = true)
    public List<CustomerCatalogItemDto> customerCatalog(UUID customerId) {
        List<ProductDto> products = visibleToCustomer(customerId);
        Map<UUID, List<ProductDto>> byChannel = new LinkedHashMap<>();
        // Price each product in the channel checkout will order it in (same rule, ChannelChoice).
        products.forEach(p -> byChannel.computeIfAbsent(
                com.sfa.security.ChannelChoice.orderChannelFor(p.channelIds()), k -> new ArrayList<>()).add(p));

        LocalDate today = LocalDate.now();
        Map<UUID, CustomerCatalogItemDto> priced = new java.util.HashMap<>();
        Map<UUID, String> channelNames = new java.util.HashMap<>();
        tenantRepository.findAllById(byChannel.keySet().stream().filter(java.util.Objects::nonNull).toList())
                .forEach(t -> channelNames.put(t.getId(), t.getName()));
        byChannel.forEach((tenantId, channelProducts) -> tenantScope.callInChannel(tenantId, () -> {
            for (ProductDto p : channelProducts) {
                PricingEngine.PriceResult r = pricingEngine.resolve(p.id(), customerId);
                boolean qtyTiers = batchPriceRepository.findAllActiveForProduct(p.id(), customerId, today).stream()
                        .anyMatch(bp -> bp.getMinQty() != null && bp.getMinQty().compareTo(BigDecimal.ONE) > 0);
                List<CustomerPromotionDto> promotions = promotionRepository
                        .findActivePromotions(p.id(), customerId, today).stream()
                        .map(CustomerPromotionDto::from).toList();
                priced.put(p.id(), new CustomerCatalogItemDto(p, r.unitPrice(), r.basePrice(), r.source(),
                        r.promotionName(), r.taxPct(), qtyTiers, promotions,
                        tenantId, channelNames.get(tenantId)));
            }
            return null;
        }));
        return products.stream().map(p -> priced.get(p.id())).toList();
    }

    public ProductDto getById(UUID id) {
        return ProductDto.from(findOrThrow(id));
    }

    @Transactional
    public ProductDto create(CreateProductRequest req, UUID actingUserId) {
        if (productRepository.findByProductCode(req.productCode()).isPresent()) {
            throw new BusinessException("Product code already exists: " + req.productCode());
        }
        checkBarcodeUnique(req.barcode(), null);
        Product p = new Product();
        applyFields(p, req);

        // Explicit channel (SUPER_ADMIN always; a multi-channel actor optionally) — findById,
        // not getReferenceById: ProductDto.from() below reads tenant.getCode() on this same
        // object, and forcing that lazy proxy to initialize this late races the @Async audit
        // logger touching the same proxy, corrupting Hibernate's session state. Absent, the
        // TenantAwareEntityListener stamps the ambient channel automatically on save().
        tenantAccessService.resolveExplicitTenant(req.tenantId(), actingUserId, "product")
                .ifPresent(p::setTenant);
        channelMembershipService.initChannels(p, req.tenantIds(), actingUserId);

        Product saved = productRepository.save(p);
        auditLogService.log(null, "CREATE", "Product", saved.getId(), null, saved);
        return ProductDto.from(saved);
    }

    @Transactional
    public ProductDto update(UUID id, CreateProductRequest req, UUID actingUserId) {
        Product p = findOrThrow(id);
        checkBarcodeUnique(req.barcode(), id);
        Object before = ProductDto.from(p);
        applyFields(p, req);
        if (req.tenantIds() != null) {
            Set<UUID> beforeChannels = Set.copyOf(p.getChannelIds());
            channelMembershipService.setChannels(p, req.tenantIds(), actingUserId);
            syncBatchPriceChannels(p, beforeChannels);
        }
        Product saved = productRepository.save(p);
        auditLogService.log(null, "UPDATE", "Product", saved.getId(), before, ProductDto.from(saved));
        return ProductDto.from(saved);
    }

    /** Adds / removes / sets the channels a batch of products is available in — SUPER_ADMIN only
     *  (see ProductController), so every id is looked up unfiltered by channel. The product's own
     *  batch prices follow it (see syncBatchPriceChannels). Each row is validated/saved
     *  independently so one bad id doesn't fail the whole batch. */
    @Transactional
    public BulkAssignResultDto bulkAssignTenant(BulkAssignTenantRequest req, UUID actingUserId) {
        List<UUID> channels = req.channels();
        if (channels.isEmpty()) throw new BusinessException("Select at least one channel.");
        ChannelMembershipService.Mode mode = req.resolvedMode();
        List<BulkAssignResultDto.RowError> errors = new ArrayList<>();
        int successCount = 0;

        for (UUID id : req.ids()) {
            Product p = productRepository.findById(id).orElse(null);
            String label = p != null ? p.getName() : null;
            try {
                if (p == null) {
                    throw new ResourceNotFoundException("Product", id);
                }
                Object before = ProductDto.from(p);
                Set<UUID> beforeChannels = Set.copyOf(p.getChannelIds());
                channelMembershipService.modify(p, channels, mode, actingUserId);
                Product saved = productRepository.save(p);
                syncBatchPriceChannels(saved, beforeChannels);
                auditLogService.log(actingUserId, "UPDATE", "Product", saved.getId(), before, ProductDto.from(saved));
                successCount++;
            } catch (Exception e) {
                errors.add(new BulkAssignResultDto.RowError(id, label, e.getMessage()));
            }
        }
        return new BulkAssignResultDto(req.ids().size(), successCount, errors.size(), errors);
    }

    /**
     * Keeps a product's own batch prices (rows priced for this product, not a product group)
     * available wherever the product is: channels the product just gained are added to them,
     * channels it just lost are removed from them (a price left with no channel keeps its home
     * channel so it isn't orphaned). Legacy channel-less prices get the product's channels.
     */
    private void syncBatchPriceChannels(Product p, Set<UUID> beforeChannels) {
        Set<UUID> now = p.getChannelIds();
        Set<UUID> added = new HashSet<>(now);
        added.removeAll(beforeChannels);
        Set<UUID> removed = new HashSet<>(beforeChannels);
        removed.removeAll(now);
        for (BatchPrice bp : batchPriceRepository.findByProductId(p.getId())) {
            boolean legacy = bp.getTenant() == null;
            if (legacy) {
                bp.setTenant(p.getTenant());
                bp.getChannelIds().addAll(now);
            } else {
                bp.getChannelIds().addAll(added);
                bp.getChannelIds().removeAll(removed);
                if (bp.getChannelIds().isEmpty()) bp.getChannelIds().add(bp.getTenant().getId());
                if (!bp.getChannelIds().contains(bp.getTenant().getId())) {
                    bp.setTenant(p.getTenant());
                    bp.getChannelIds().add(p.getTenant().getId());
                }
            }
            batchPriceRepository.save(bp);
        }
    }

    private void checkBarcodeUnique(String barcode, UUID excludeId) {
        if (barcode == null || barcode.isBlank()) return;
        productRepository.findByBarcode(barcode.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(excludeId)) {
                throw new BusinessException("Barcode already assigned to another product: " + barcode);
            }
        });
    }

    @Transactional
    public void deactivate(UUID id) {
        Product p = findOrThrow(id);
        p.setStatus(Product.ProductStatus.INACTIVE);
        productRepository.save(p);
    }

    private void applyFields(Product p, CreateProductRequest req) {
        if (req.maxDiscountAmount() != null && req.maxDiscountAmount().compareTo(req.defaultPrice()) >= 0) {
            throw new BusinessException("Max discount must be less than the product's selling price");
        }

        p.setProductCode(req.productCode());
        p.setBarcode(req.barcode() != null && !req.barcode().isBlank() ? req.barcode().trim() : null);
        p.setName(req.name());
        p.setDescription(req.description());
        if (req.categoryId() != null) {
            ProductCategory cat = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", req.categoryId()));
            p.setCategory(cat);
        }
        if (req.unitId() != null) {
            Unit unit = unitRepository.findById(req.unitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", req.unitId()));
            p.setUnit(unit);
        }
        p.setDefaultPrice(req.defaultPrice());
        p.setPurchasePrice(req.purchasePrice());
        p.setTaxRate(req.taxRate());
        p.setMaxDiscountAmount(req.maxDiscountAmount());
    }

    private Product findOrThrow(UUID id) {
        Product p = productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
        // @Filter doesn't cover a by-id lookup (only HQL/Criteria queries) — without this,
        // any channel could fetch/edit/deactivate another channel's product by guessing its id.
        return TenantGuard.requireVisible(p, "Product", id);
    }
}
