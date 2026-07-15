package com.sfa.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfa.dto.pricing.PromotionResponseDto;
import com.sfa.entity.*;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.*;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final PromotionRepository         promotionRepository;
    private final PromotionEditLogRepository  editLogRepository;
    private final ObjectMapper                objectMapper;

    // ── Batch prices ──────────────────────────────────────────────────────────

    @GetMapping("/api/batch-prices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_REP')")
    public Page<BatchPrice> listBatchPrices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return batchPriceRepository.findAll(
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping("/api/batch-prices")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<BatchPrice> createBatchPrice(@RequestBody Map<String, Object> body) {
        UUID    productId  = UUID.fromString((String) body.get("productId"));
        Object  custIdObj  = body.get("customerId");

        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));

        BatchPrice bp = new BatchPrice();
        bp.setProduct(product);
        if (custIdObj != null && !((String) custIdObj).isBlank()) {
            UUID customerId = UUID.fromString((String) custIdObj);
            Customer customer = customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId));
            bp.setCustomer(customer);
        }
        bp.setPrice(new BigDecimal(body.get("price").toString()));
        Object mqRaw = body.get("minQty");
        bp.setMinQty(mqRaw != null && !mqRaw.toString().isBlank()
                ? new BigDecimal(mqRaw.toString()) : null);
        bp.setStartDate(LocalDate.parse((String) body.get("startDate")));
        bp.setEndDate(LocalDate.parse((String) body.get("endDate")));

        BatchPrice saved = batchPriceRepository.save(bp);
        return ResponseEntity.created(URI.create("/api/batch-prices/" + saved.getId())).body(saved);
    }

    @PutMapping("/api/batch-prices/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<BatchPrice> updateBatchPrice(
            @PathVariable UUID id, @RequestBody Map<String, Object> body) {
        BatchPrice bp = batchPriceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("BatchPrice", id));
        UUID productId = UUID.fromString((String) body.get("productId"));
        bp.setProduct(productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId)));
        Object custIdObj = body.get("customerId");
        if (custIdObj instanceof String s && !s.isBlank()) {
            UUID customerId = UUID.fromString(s);
            bp.setCustomer(customerRepository.findById(customerId)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", customerId)));
        } else {
            bp.setCustomer(null);
        }
        bp.setPrice(new BigDecimal(body.get("price").toString()));
        Object mqRaw = body.get("minQty");
        bp.setMinQty(mqRaw != null && !mqRaw.toString().isBlank()
                ? new BigDecimal(mqRaw.toString()) : null);
        bp.setStartDate(LocalDate.parse((String) body.get("startDate")));
        bp.setEndDate(LocalDate.parse((String) body.get("endDate")));
        return ResponseEntity.ok(batchPriceRepository.save(bp));
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
        addIfChanged(diff, "Free Product", s(beforeFP),   s(afterFP));
        addIfChanged(diff, "Customer",     s(beforeCust), s(afterCust));

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

        // Products
        @SuppressWarnings("unchecked")
        List<String> productIdStrings = (List<String>) body.get("productIds");
        if (productIdStrings != null && !productIdStrings.isEmpty()) {
            Set<Product> products = productIdStrings.stream()
                    .map(s -> UUID.fromString(s.trim()))
                    .map(pid -> productRepository.findById(pid)
                            .orElseThrow(() -> new ResourceNotFoundException("Product", pid)))
                    .collect(Collectors.toSet());
            promo.setProducts(products);
        }

        // Customer (blank = all customers)
        Object custId = body.get("customerId");
        if (custId instanceof String s && !s.isBlank()) {
            UUID cid = UUID.fromString(s);
            promo.setCustomer(customerRepository.findById(cid)
                    .orElseThrow(() -> new ResourceNotFoundException("Customer", cid)));
        } else {
            promo.setCustomer(null);
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
