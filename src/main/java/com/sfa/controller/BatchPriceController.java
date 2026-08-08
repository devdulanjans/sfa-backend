package com.sfa.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfa.dto.pricing.PromotionResponseDto;
import com.sfa.entity.*;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.*;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class BatchPriceController {

    private final BatchPriceRepository        batchPriceRepository;
    private final ProductRepository           productRepository;
    private final CustomerRepository          customerRepository;
    private final CustomerGroupRepository     customerGroupRepository;
    private final ProductGroupRepository      productGroupRepository;
    private final PromotionRepository         promotionRepository;
    private final PromotionEditLogRepository  editLogRepository;
    private final ObjectMapper                objectMapper;

    // ── Batch prices ──────────────────────────────────────────────────────────

    @GetMapping("/api/batch-prices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_REP')")
    public Page<BatchPrice> listBatchPrices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID customerGroupId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return batchPriceRepository.findFiltered(customerId, customerGroupId, productId, startDate, endDate,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    /**
     * Mobile-sync view: unlike {@link #listBatchPrices}, which returns raw rows for admin
     * management (one row = one rule, even if group-targeted), this flattens any
     * customer-group/product-group-targeted row into one synthetic row per current group
     * member (a full cross-product when both sides are group-targeted) — mobile's
     * offline_pricing.dart has no group concept and expects a plain single product/customer
     * per row, exactly like today's direct rows. Expansion happens in-memory since this
     * codebase's row volumes don't need DB-level fan-out pagination.
     */
    @GetMapping("/api/batch-prices/sync")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_REP')")
    @Transactional(readOnly = true)
    public Page<BatchPrice> syncBatchPrices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        List<BatchPrice> expanded = new ArrayList<>();
        for (BatchPrice bp : batchPriceRepository.findAll()) {
            if (bp.getCustomerGroup() == null && bp.getProductGroup() == null) {
                expanded.add(bp);
                continue;
            }
            List<Customer> customers = bp.getCustomerGroup() != null
                    ? List.copyOf(bp.getCustomerGroup().getMembers())
                    : Collections.singletonList(bp.getCustomer());
            List<Product> products = bp.getProductGroup() != null
                    ? List.copyOf(bp.getProductGroup().getMembers())
                    : Collections.singletonList(bp.getProduct());
            for (Customer c : customers) {
                for (Product p : products) {
                    expanded.add(BatchPrice.builder()
                            .id(bp.getId())
                            .product(p)
                            .customer(c)
                            .promotion(bp.getPromotion())
                            .price(bp.getPrice())
                            .minQty(bp.getMinQty())
                            .startDate(bp.getStartDate())
                            .endDate(bp.getEndDate())
                            .createdAt(bp.getCreatedAt())
                            .updatedAt(bp.getUpdatedAt())
                            .build());
                }
            }
        }
        return paginate(expanded, page, size);
    }

    @PostMapping("/api/batch-prices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<BatchPrice> createBatchPrice(@RequestBody Map<String, Object> body) {
        BatchPrice bp = new BatchPrice();
        applyBatchPriceBody(bp, body);
        BatchPrice saved = batchPriceRepository.save(bp);
        return ResponseEntity.created(URI.create("/api/batch-prices/" + saved.getId())).body(saved);
    }

    @PutMapping("/api/batch-prices/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<BatchPrice> updateBatchPrice(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        BatchPrice bp = batchPriceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BatchPrice", id));
        applyBatchPriceBody(bp, body);
        return ResponseEntity.ok(batchPriceRepository.save(bp));
    }

    /** Product target is productId XOR productGroupId; customer target is at most one of
     *  customerId/customerGroupId (both blank = applies to all customers). */
    private void applyBatchPriceBody(BatchPrice bp, Map<String, Object> body) {
        String productId      = blankToNull((String) body.get("productId"));
        String productGroupId = blankToNull((String) body.get("productGroupId"));
        if ((productId == null) == (productGroupId == null)) {
            throw new BusinessException("Specify exactly one of productId or productGroupId");
        }
        if (productId != null) {
            UUID pid = UUID.fromString(productId);
            bp.setProduct(productRepository.findById(pid)
                    .orElseThrow(() -> new ResourceNotFoundException("Product", pid)));
            bp.setProductGroup(null);
        } else {
            UUID gid = UUID.fromString(productGroupId);
            bp.setProductGroup(productGroupRepository.findById(gid)
                    .orElseThrow(() -> new ResourceNotFoundException("ProductGroup", gid)));
            bp.setProduct(null);
        }

        String customerId      = blankToNull((String) body.get("customerId"));
        String customerGroupId = blankToNull((String) body.get("customerGroupId"));
        if (customerId != null && customerGroupId != null) {
            throw new BusinessException("Specify at most one of customerId or customerGroupId");
        }
        if (customerId != null) {
            UUID cid = UUID.fromString(customerId);
            bp.setCustomer(customerRepository.findById(cid)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", cid)));
            bp.setCustomerGroup(null);
        } else if (customerGroupId != null) {
            UUID gid = UUID.fromString(customerGroupId);
            bp.setCustomerGroup(customerGroupRepository.findById(gid)
                    .orElseThrow(() -> new ResourceNotFoundException("CustomerGroup", gid)));
            bp.setCustomer(null);
        } else {
            bp.setCustomer(null);
            bp.setCustomerGroup(null);
        }

        bp.setPrice(new BigDecimal(body.get("price").toString()));
        Object mqRaw = body.get("minQty");
        bp.setMinQty(mqRaw != null && !mqRaw.toString().isBlank()
                ? new BigDecimal(mqRaw.toString()) : null);
        bp.setStartDate(LocalDate.parse((String) body.get("startDate")));
        bp.setEndDate(LocalDate.parse((String) body.get("endDate")));
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    @DeleteMapping("/api/batch-prices/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<Void> deleteBatchPrice(@PathVariable UUID id) {
        batchPriceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BatchPrice", id));
        batchPriceRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // ── Promotions ────────────────────────────────────────────────────────────

    @GetMapping("/api/promotions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_REP')")
    public Page<PromotionResponseDto> listPromotions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return promotionRepository
                .findAll(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(PromotionResponseDto::from);
    }

    /**
     * Mobile-sync view: flattens any customer-group-targeted promotion into one synthetic row
     * per current group member (mobile matches a promotion's single embedded {@code customer}
     * field, not a group). The product side needs no per-row expansion — a promotion already
     * ships its full {@code products} set per row, so a linked product group's current members
     * are simply unioned into that set instead.
     */
    @GetMapping("/api/promotions/sync")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_REP')")
    @Transactional(readOnly = true)
    public Page<PromotionResponseDto> syncPromotions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        List<Promotion> expanded = new ArrayList<>();
        for (Promotion p : promotionRepository.findAll()) {
            Set<Product> productsUnion = new LinkedHashSet<>(p.getProducts());
            if (p.getProductGroup() != null) {
                productsUnion.addAll(p.getProductGroup().getMembers());
            }
            if (p.getCustomerGroup() == null) {
                if (productsUnion.equals(p.getProducts())) {
                    expanded.add(p);
                } else {
                    expanded.add(p.toBuilder().products(productsUnion).build());
                }
                continue;
            }
            for (Customer c : p.getCustomerGroup().getMembers()) {
                expanded.add(p.toBuilder().customer(c).customerGroup(null).products(productsUnion).build());
            }
        }
        return paginate(expanded, page, size).map(PromotionResponseDto::from);
    }

    private <T> Page<T> paginate(List<T> items, int page, int size) {
        int from = Math.min(page * size, items.size());
        int to   = Math.min(from + size, items.size());
        return new PageImpl<>(items.subList(from, to), PageRequest.of(page, size), items.size());
    }

    @PostMapping("/api/promotions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<PromotionResponseDto> createPromotion(@RequestBody Map<String, Object> body) {
        Promotion promo = new Promotion();
        applyPromotionBody(promo, body);
        Promotion saved = promotionRepository.save(promo);
        return ResponseEntity.created(URI.create("/api/promotions/" + saved.getId()))
                .body(PromotionResponseDto.from(saved));
    }

    @PutMapping("/api/promotions/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<PromotionResponseDto> updatePromotion(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        Promotion promo = promotionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Promotion", id));

        // ── Snapshot before ───────────────────────────────────────────────────
        String        beforeName   = promo.getName();
        String        beforeType   = promo.getType().name();
        BigDecimal    beforeDV     = promo.getDiscountValue();
        Integer       beforeMFC    = promo.getMaxFreeCount();
        Integer       beforeMOQ    = promo.getMinOrderQty();
        LocalDate     beforeSD     = promo.getStartDate();
        LocalDate     beforeED     = promo.getEndDate();
        Boolean       beforeActive = promo.getIsActive();
        Set<String>   beforeProds  = promo.getProducts().stream().filter(Objects::nonNull).map(p -> p.getName() != null ? p.getName() : "").collect(Collectors.toSet());
        String        beforeFP     = promo.getFreeProduct() != null ? promo.getFreeProduct().getName() : null;
        String        beforeCust   = promo.getCustomer()    != null ? promo.getCustomer().getName()    : null;
        String        beforeCustGroup = promo.getCustomerGroup() != null ? promo.getCustomerGroup().getName() : null;
        String        beforeProdGroup = promo.getProductGroup() != null ? promo.getProductGroup().getName() : null;

        // ── Apply changes ─────────────────────────────────────────────────────
        applyPromotionBody(promo, body);
        Promotion saved = promotionRepository.save(promo);

        // ── Compute diff ──────────────────────────────────────────────────────
        Map<String, Object> diff = new LinkedHashMap<>();
        addIfChanged(diff, "Name",            beforeName,                  saved.getName());
        addIfChanged(diff, "Type",            beforeType,                  saved.getType().name());
        addIfChanged(diff, "Discount Value",  s(beforeDV),                 s(saved.getDiscountValue()));
        addIfChanged(diff, "Max Free Units",  s(beforeMFC),                s(saved.getMaxFreeCount()));
        addIfChanged(diff, "Min Order Qty",   s(beforeMOQ),                s(saved.getMinOrderQty()));
        addIfChanged(diff, "Start Date",     s(beforeSD),                 s(saved.getStartDate()));
        addIfChanged(diff, "End Date",       s(beforeED),                 s(saved.getEndDate()));
        addIfChanged(diff, "Active",         s(beforeActive),             s(saved.getIsActive()));
        Set<String> afterProds = saved.getProducts().stream().filter(Objects::nonNull).map(p -> p.getName() != null ? p.getName() : "").collect(Collectors.toSet());
        if (!beforeProds.equals(afterProds))
            diff.put("Applies To", Map.of("from", String.join(", ", beforeProds),
                                           "to",   String.join(", ", afterProds)));
        String afterFP   = saved.getFreeProduct() != null ? saved.getFreeProduct().getName() : null;
        String afterCust = saved.getCustomer()    != null ? saved.getCustomer().getName()    : null;
        String afterCustGroup = saved.getCustomerGroup() != null ? saved.getCustomerGroup().getName() : null;
        String afterProdGroup = saved.getProductGroup() != null ? saved.getProductGroup().getName() : null;
        addIfChanged(diff, "Free Product",    s(beforeFP),        s(afterFP));
        addIfChanged(diff, "Customer",        s(beforeCust),      s(afterCust));
        addIfChanged(diff, "Customer Group",  s(beforeCustGroup), s(afterCustGroup));
        addIfChanged(diff, "Product Group",   s(beforeProdGroup), s(afterProdGroup));

        // ── Persist edit log ──────────────────────────────────────────────────
        try {
            PromotionEditLog log = PromotionEditLog.builder()
                    .promotionId(id)
                    .promotionName(saved.getName())
                    .editedBy(principal != null ? principal.getId() : null)
                    .editedByName(principal != null ? principal.getUsername() : "system")
                    .changesJson(objectMapper.writeValueAsString(diff))
                    .build();
            editLogRepository.save(log);
        } catch (Exception ignored) { /* never fail the main request */ }

        return ResponseEntity.ok(PromotionResponseDto.from(saved));
    }

    // ── Edit history ──────────────────────────────────────────────────────────

    @GetMapping("/api/promotions/{id}/history")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public List<EditHistoryDto> getPromotionHistory(@PathVariable UUID id) {
        return editLogRepository.findByPromotionIdOrderByCreatedAtDesc(id)
                .stream()
                .map(log -> {
                    Map<String, Object> changes;
                    try {
                        changes = objectMapper.readValue(log.getChangesJson(), new TypeReference<>() {});
                    } catch (Exception e) {
                        changes = Map.of();
                    }
                    return new EditHistoryDto(log.getId(), log.getPromotionName(),
                            log.getEditedBy(), log.getEditedByName(), changes, log.getCreatedAt());
                })
                .toList();
    }

    public record EditHistoryDto(
            UUID id, String promotionName, UUID editedBy, String editedByName,
            Map<String, Object> changes, Instant createdAt) {}

    // ── Shared helpers ────────────────────────────────────────────────────────

    private void applyPromotionBody(Promotion promo, Map<String, Object> body) {
        promo.setName((String) body.get("name"));

        // Products — always a full replace (an intentionally-cleared list must actually clear,
        // now that a promotion can rely solely on productGroupId instead of an explicit list)
        @SuppressWarnings("unchecked")
        List<String> productIdStrings = (List<String>) body.get("productIds");
        Set<Product> products = (productIdStrings == null ? List.<String>of() : productIdStrings).stream()
                .map(s -> UUID.fromString(s.trim()))
                .map(pid -> productRepository.findById(pid)
                        .orElseThrow(() -> new ResourceNotFoundException("Product", pid)))
                .collect(Collectors.toSet());
        promo.setProducts(products);

        // Product group (additive to the explicit products list above)
        Object prodGroupIdObj = body.get("productGroupId");
        if (prodGroupIdObj instanceof String pgs && !pgs.isBlank()) {
            UUID pgid = UUID.fromString(pgs);
            promo.setProductGroup(productGroupRepository.findById(pgid)
                    .orElseThrow(() -> new ResourceNotFoundException("ProductGroup", pgid)));
        } else {
            promo.setProductGroup(null);
        }

        if (products.isEmpty() && promo.getProductGroup() == null) {
            throw new BusinessException("Specify at least one product (directly or via a product group)");
        }

        // Customer (blank = all customers); at most one of customerId/customerGroupId
        Object custId      = body.get("customerId");
        Object custGroupId = body.get("customerGroupId");
        String custIdStr      = (custId instanceof String s && !s.isBlank()) ? s : null;
        String custGroupIdStr = (custGroupId instanceof String s && !s.isBlank()) ? s : null;
        if (custIdStr != null && custGroupIdStr != null) {
            throw new BusinessException("Specify at most one of customerId or customerGroupId");
        }
        if (custIdStr != null) {
            UUID cid = UUID.fromString(custIdStr);
            promo.setCustomer(customerRepository.findById(cid)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", cid)));
            promo.setCustomerGroup(null);
        } else if (custGroupIdStr != null) {
            UUID gid = UUID.fromString(custGroupIdStr);
            promo.setCustomerGroup(customerGroupRepository.findById(gid)
                    .orElseThrow(() -> new ResourceNotFoundException("CustomerGroup", gid)));
            promo.setCustomer(null);
        } else {
            promo.setCustomer(null);
            promo.setCustomerGroup(null);
        }

        // Type + discount / free-product
        Promotion.PromotionType type = Promotion.PromotionType.valueOf((String) body.get("type"));
        promo.setType(type);

        if (type == Promotion.PromotionType.FREE_PRODUCT) {
            Object fpIdObj = body.get("freeProductId");
            if (fpIdObj == null || ((String) fpIdObj).isBlank())
                throw new IllegalArgumentException("freeProductId is required for FREE_PRODUCT promotions");
            UUID fpId = UUID.fromString(((String) fpIdObj).trim());
            promo.setFreeProduct(productRepository.findById(fpId)
                    .orElseThrow(() -> new ResourceNotFoundException("Free product", fpId)));
            promo.setDiscountValue(BigDecimal.ZERO);
            Object maxFree = body.get("maxFreeCount");
            promo.setMaxFreeCount(maxFree != null ? Integer.parseInt(maxFree.toString()) : 1);
            Object minOrdQty = body.get("minOrderQty");
            promo.setMinOrderQty(minOrdQty != null ? Integer.parseInt(minOrdQty.toString()) : 1);
        } else {
            promo.setFreeProduct(null);
            promo.setDiscountValue(new BigDecimal(body.get("discountValue").toString()));
        }

        promo.setStartDate(LocalDate.parse((String) body.get("startDate")));
        promo.setEndDate(LocalDate.parse((String) body.get("endDate")));
        promo.setIsActive(Boolean.TRUE.equals(body.get("isActive")));
    }

    private void addIfChanged(Map<String, Object> diff, String label, String before, String after) {
        if (!Objects.equals(before, after))
            diff.put(label, Map.of("from", before, "to", after));
    }

    private String s(Object v) { return v == null ? "—" : v.toString(); }
}
