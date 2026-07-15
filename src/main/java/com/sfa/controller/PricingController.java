package com.sfa.controller;

import com.sfa.dto.pricing.BatchPriceTierDto;
import com.sfa.dto.pricing.PriceResolveRequest;
import com.sfa.dto.pricing.PriceResolveResponse;
import com.sfa.entity.BatchPrice;
import com.sfa.repository.BatchPriceRepository;
import com.sfa.service.PricingEngine;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/pricing")
@RequiredArgsConstructor
public class PricingController {

    private final PricingEngine pricingEngine;
    private final BatchPriceRepository batchPriceRepo;

    /** Used by web admin (POST with JSON body). */
    @PostMapping("/resolve")
    public PriceResolveResponse resolve(@Valid @RequestBody PriceResolveRequest req) {
        return doResolve(req.productId(), req.customerId());
    }

    /** Used by mobile (GET with query params). */
    @GetMapping("/resolve")
    public PriceResolveResponse resolveGet(
            @RequestParam UUID productId,
            @RequestParam UUID customerId) {
        return doResolve(productId, customerId);
    }

    private PriceResolveResponse doResolve(UUID productId, UUID customerId) {
        PricingEngine.PriceResult r = pricingEngine.resolve(productId, customerId);
        PriceResolveResponse.FreeProduct fp = r.freeProduct() == null ? null
                : new PriceResolveResponse.FreeProduct(
                        r.freeProduct().id(),
                        r.freeProduct().name(),
                        r.freeProduct().productCode(),
                        r.freeProduct().maxFreeCount(),
                        r.freeProduct().minOrderQty(),
                        r.freeProduct().applicableProductIds());
        return new PriceResolveResponse(r.unitPrice(), r.source(), r.promotionName(), r.maxDiscountAmount(), r.taxPct(), fp);
    }

    /**
     * Returns a flat "special price" per product for a customer, keyed by product ID —
     * used by mobile to pre-load a single price it can add to the cart with one tap, with
     * no further qty-tier decision needed. A product only qualifies when the customer has
     * exactly one active batch-price row for it and that row has no meaningful {@code minQty}
     * (null or &lt;= 1, i.e. it's a genuine flat override, not one tier of several). Products
     * with real multi-tier customer pricing (several rows, or a row gated behind minQty &gt; 1)
     * are deliberately excluded so mobile falls through to the full {@code /pricing/resolve} +
     * {@code /pricing/tiers} flow instead of silently picking one arbitrary tier's price.
     */
    @GetMapping("/customer-overrides")
    public Map<String, BigDecimal> customerOverrides(@RequestParam UUID customerId) {
        List<BatchPrice> rows = batchPriceRepo.findAllActiveForCustomer(customerId, LocalDate.now());
        Map<UUID, List<BatchPrice>> byProduct = rows.stream()
                .collect(Collectors.groupingBy(bp -> bp.getProduct().getId()));

        Map<String, BigDecimal> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, List<BatchPrice>> e : byProduct.entrySet()) {
            List<BatchPrice> productRows = e.getValue();
            if (productRows.size() != 1) continue; // genuine multi-tier — let mobile resolve tiers itself
            BatchPrice only = productRows.get(0);
            if (only.getMinQty() != null && only.getMinQty().compareTo(BigDecimal.ONE) > 0) continue;
            result.put(e.getKey().toString(), only.getPrice());
        }
        return result;
    }

    /**
     * Product IDs with any active batch price visible to this customer (general
     * or customer-specific). Used by mobile to bulk-check, for a whole product
     * list, which products should hide their plain default price in favor of
     * "select qty to see price" (their real price depends on the batch tier).
     */
    @GetMapping("/batch-product-ids")
    public List<UUID> batchProductIds(@RequestParam UUID customerId) {
        return batchPriceRepo.findActiveProductIdsVisibleToCustomer(customerId, LocalDate.now());
    }

    /**
     * Returns all active batch price tiers for a product visible to a customer.
     * Customer-specific tiers appear first, then general tiers, ordered by min qty.
     * Empty list means no batch prices — use default/promotion pricing.
     */
    @GetMapping("/tiers")
    public List<BatchPriceTierDto> tiers(
            @RequestParam UUID productId,
            @RequestParam(required = false) UUID customerId) {
        return batchPriceRepo
                .findAllActiveForProduct(productId, customerId, LocalDate.now())
                .stream()
                // customer-specific first (0), general second (1); then ascending min qty
                .sorted(Comparator
                        .comparingInt((BatchPrice bp) -> bp.getCustomer() != null ? 0 : 1)
                        .thenComparing(bp -> bp.getMinQty() != null ? bp.getMinQty() : BigDecimal.ZERO))
                .map(bp -> {
                    boolean isCustomerSpecific = bp.getCustomer() != null;
                    return new BatchPriceTierDto(
                            bp.getId(),
                            bp.getPrice(),
                            bp.getMinQty(),
                            bp.getStartDate(),
                            bp.getEndDate(),
                            isCustomerSpecific,
                            isCustomerSpecific ? "Customer Price" : "General Price"
                    );
                })
                .toList();
    }
}
