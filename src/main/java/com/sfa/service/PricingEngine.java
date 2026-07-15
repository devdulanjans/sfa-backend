package com.sfa.service;

import com.sfa.entity.BatchPrice;
import com.sfa.entity.Customer;
import com.sfa.entity.Product;
import com.sfa.entity.Promotion;
import com.sfa.repository.BatchPriceRepository;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.ProductRepository;
import com.sfa.repository.PromotionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;


@Service
@RequiredArgsConstructor
public class PricingEngine {

    private final BatchPriceRepository batchPriceRepo;
    private final PromotionRepository  promotionRepo;
    private final ProductRepository    productRepo;
    private final CustomerRepository   customerRepo;

    /** Carries the resolved free product when a FREE_PRODUCT promotion applies. */
    public record FreeProductInfo(UUID id, String name, String productCode,
                                  int maxFreeCount, int minOrderQty, List<UUID> applicableProductIds) {}

    public record PriceResult(
            BigDecimal      unitPrice,
            String          source,
            String          promotionName,
            BigDecimal      maxDiscountAmount,
            BigDecimal      taxPct,
            FreeProductInfo freeProduct) {}

    public record LineItemResult(
            BigDecimal unitPrice,
            BigDecimal discountPct,
            BigDecimal discountAmount,
            BigDecimal taxPct,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            String     priceSource,
            String     promotionName) {}

    /** A FREE_PRODUCT line earned by the cart, priced per the customer's display setting. */
    public record FreeLineResult(
            UUID       productId,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal discountPct,
            BigDecimal discountAmount,
            BigDecimal taxPct,
            BigDecimal taxAmount,
            BigDecimal lineTotal,
            String     promotionName) {}

    /** Priority: customer price → promotion → general batch → default, at qty=1 (see {@link #resolve(UUID, UUID, BigDecimal)}). */
    public PriceResult resolve(UUID productId, UUID customerId) {
        return resolve(productId, customerId, BigDecimal.ONE);
    }

    /**
     * Priority: customer price → promotion → general batch → default. Both the customer-specific
     * and general batch-price steps are tier-aware: when a product has multiple qty-gated tiers
     * (e.g. a customer with several {@code BatchPrice} rows at increasing {@code minQty}), the
     * tier whose {@code minQty} is the highest one satisfied by {@code qty} wins — not just
     * whichever row happens to have the latest {@code startDate}.
     */
    public PriceResult resolve(UUID productId, UUID customerId, BigDecimal qty) {
        LocalDate today   = LocalDate.now();
        BigDecimal q      = qty != null ? qty : BigDecimal.ONE;
        Product   product = productRepo.findById(productId).orElseThrow();
        BigDecimal taxPct = resolveTaxPct(product, customerId);

        List<Promotion> activePromotions = promotionRepo.findActivePromotions(productId, customerId, today);

        // A FREE_PRODUCT promotion's eligibility is independent of which price source wins for
        // the *paid* unit below — a product can have both a customer-specific batch price and
        // a "buy X get Y free" promotion active at once, and the free item must still be surfaced
        // even when the batch price (step 1) is what prices the paid unit. Resolved once here and
        // attached to whichever step below returns, rather than only being reachable from step 2.
        FreeProductInfo freeInfo = activePromotions.stream()
                .filter(p -> p.getType() == Promotion.PromotionType.FREE_PRODUCT && p.getFreeProduct() != null)
                .findFirst()
                .map(p -> {
                    Product fp = p.getFreeProduct();
                    int maxFree   = p.getMaxFreeCount() != null ? p.getMaxFreeCount() : 1;
                    int minOrdQty = p.getMinOrderQty()  != null ? p.getMinOrderQty()  : 1;
                    List<UUID> applicableIds = p.getProducts().stream().map(Product::getId).toList();
                    return new FreeProductInfo(fp.getId(), fp.getName(), fp.getProductCode(),
                            maxFree, minOrdQty, applicableIds);
                })
                .orElse(null);

        // 1. Customer-specific batch price (best tier for this qty)
        Optional<BatchPrice> customerPrice =
                batchPriceRepo.findBestCustomerBatchPrice(productId, customerId, q, today);
        if (customerPrice.isPresent()) {
            return new PriceResult(customerPrice.get().getPrice(), "CUSTOMER_PRICE",
                    null, product.getMaxDiscountAmount(), taxPct, freeInfo);
        }

        // 2. Active promotion (first match wins — customer-specific before general)
        Optional<Promotion> promo = activePromotions.stream().findFirst();
        if (promo.isPresent()) {
            Promotion p = promo.get();
            BigDecimal base = batchPriceRepo.findBestGeneralBatchPrice(productId, q, today)
                    .map(BatchPrice::getPrice)
                    .orElseGet(product::getDefaultPrice);

            if (p.getType() == Promotion.PromotionType.FREE_PRODUCT) {
                return new PriceResult(base, "PROMOTION",
                        p.getName(), product.getMaxDiscountAmount(), taxPct, freeInfo);
            }

            BigDecimal promoPrice = applyPromotion(base, p);
            return new PriceResult(promoPrice, "PROMOTION",
                    p.getName(), product.getMaxDiscountAmount(), taxPct, freeInfo);
        }

        // 3. General batch price (best tier for this qty)
        Optional<BatchPrice> batchPrice =
                batchPriceRepo.findBestGeneralBatchPrice(productId, q, today);
        if (batchPrice.isPresent()) {
            return new PriceResult(batchPrice.get().getPrice(), "BATCH_PRICE",
                    null, product.getMaxDiscountAmount(), taxPct, freeInfo);
        }

        // 4. Default price
        return new PriceResult(product.getDefaultPrice(), "DEFAULT_PRICE",
                null, product.getMaxDiscountAmount(), taxPct, freeInfo);
    }

    public LineItemResult calculateLine(UUID productId, UUID customerId,
                                        BigDecimal qty, BigDecimal requestedDiscountPct) {
        PriceResult base    = resolve(productId, customerId, qty);
        Product     product = productRepo.findById(productId).orElseThrow();

        // maxDiscountAmount is a fixed per-unit Rs cap, not a percentage — convert the requested
        // percentage discount to a per-unit amount, clamp it, then convert back to a percentage
        // so the rest of the calculation (and the stored OrderItem.discountPct) is unchanged.
        BigDecimal requestedPct = requestedDiscountPct != null ? requestedDiscountPct : BigDecimal.ZERO;
        BigDecimal requestedPerUnitDiscount = base.unitPrice().multiply(requestedPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal cappedPerUnitDiscount = product.getMaxDiscountAmount() != null
                ? requestedPerUnitDiscount.min(product.getMaxDiscountAmount())
                : requestedPerUnitDiscount;
        BigDecimal discountPct = base.unitPrice().compareTo(BigDecimal.ZERO) > 0
                ? cappedPerUnitDiscount.divide(base.unitPrice(), 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        BigDecimal gross          = base.unitPrice().multiply(qty);
        BigDecimal discountAmount = gross.multiply(discountPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal afterDiscount  = gross.subtract(discountAmount);
        BigDecimal taxPct         = base.taxPct(); // already accounts for customer VAT registration
        BigDecimal taxAmount      = afterDiscount.multiply(taxPct)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal lineTotal      = afterDiscount.add(taxAmount);

        return new LineItemResult(base.unitPrice(), discountPct, discountAmount,
                taxPct, taxAmount, lineTotal, base.source(), base.promotionName());
    }

    /**
     * Computes FREE_PRODUCT lines earned by this cart. For each active FREE_PRODUCT promotion
     * whose trigger products appear in {@code purchasedQtyByProduct} with a combined quantity
     * meeting {@code minOrderQty}, returns a line for {@code maxFreeCount} units of the free
     * product. When {@code showAsDiscount} is true the line is priced at the free product's
     * normal price with a 100% discount (so it prints as a real line + a discount, reducing the
     * grand total by the same amount); otherwise it is priced at zero (today's behavior).
     */
    public List<FreeLineResult> resolveFreeItems(Map<UUID, BigDecimal> purchasedQtyByProduct,
                                                  UUID customerId, boolean showAsDiscount) {
        LocalDate today = LocalDate.now();
        Map<UUID, Promotion> triggered = new java.util.LinkedHashMap<>();
        for (UUID productId : purchasedQtyByProduct.keySet()) {
            promotionRepo.findActivePromotions(productId, customerId, today).stream()
                    .filter(p -> p.getType() == Promotion.PromotionType.FREE_PRODUCT && p.getFreeProduct() != null)
                    .forEach(p -> triggered.putIfAbsent(p.getId(), p));
        }

        List<FreeLineResult> results = new java.util.ArrayList<>();
        for (Promotion p : triggered.values()) {
            BigDecimal purchased = p.getProducts().stream()
                    .map(prod -> purchasedQtyByProduct.getOrDefault(prod.getId(), BigDecimal.ZERO))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int minQty = p.getMinOrderQty() != null ? p.getMinOrderQty() : 1;
            if (purchased.compareTo(BigDecimal.valueOf(minQty)) < 0) continue;

            BigDecimal freeQty = BigDecimal.valueOf(p.getMaxFreeCount() != null ? p.getMaxFreeCount() : 1);
            Product freeProduct = p.getFreeProduct();

            BigDecimal unitPrice;
            BigDecimal taxPct;
            BigDecimal discountPct;
            if (showAsDiscount) {
                PriceResult freePrice = resolve(freeProduct.getId(), customerId);
                unitPrice   = freePrice.unitPrice();
                taxPct      = freePrice.taxPct();
                discountPct = BigDecimal.valueOf(100);
            } else {
                unitPrice   = BigDecimal.ZERO;
                taxPct      = BigDecimal.ZERO;
                discountPct = BigDecimal.ZERO;
            }

            BigDecimal gross = unitPrice.multiply(freeQty);
            BigDecimal discountAmount = gross.multiply(discountPct)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            // afterDiscount is always 0 here (discountPct is 0% or 100%), so taxAmount/lineTotal are 0
            results.add(new FreeLineResult(freeProduct.getId(), freeQty, unitPrice, discountPct,
                    discountAmount, taxPct, BigDecimal.ZERO, BigDecimal.ZERO, p.getName()));
        }
        return results;
    }

    /**
     * A customer is taxable unless explicitly marked EXEMPT or ZERO_RATED — Tax
     * Type STANDARD (or unset) always gets taxed, regardless of whether a Tax
     * Number is on file (Tax Number is purely an optional record/display field,
     * not a precondition for taxation). Once a customer is determined taxable,
     * their own {@code Customer.taxRate} (default 18%, editable per customer)
     * takes priority over the product's rate — the product's rate is only used
     * as a fallback when the customer has no rate of their own, or when there
     * is no customer context at all (e.g. a bare product price lookup).
     *
     * Shared by {@link #resolve} (orders/invoices) and {@link PosService}
     * (POS sales) so both channels apply the exact same rule.
     */
    public BigDecimal resolveTaxPct(Product product, UUID customerId) {
        if (customerId == null) {
            return product.getTaxRate() != null ? product.getTaxRate() : BigDecimal.ZERO;
        }
        Customer customer = customerRepo.findById(customerId).orElse(null);
        boolean taxable = customer == null
                || customer.getTaxType() == null
                || customer.getTaxType() == Customer.TaxType.STANDARD;
        if (!taxable) {
            return BigDecimal.ZERO;
        }
        if (customer != null && customer.getTaxRate() != null) {
            return customer.getTaxRate();
        }
        return product.getTaxRate() != null ? product.getTaxRate() : BigDecimal.ZERO;
    }

    private BigDecimal applyPromotion(BigDecimal base, Promotion promo) {
        return switch (promo.getType()) {
            case PERCENTAGE   -> base.multiply(BigDecimal.ONE.subtract(
                    promo.getDiscountValue().divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP)));
            case FIXED_AMOUNT -> base.subtract(promo.getDiscountValue()).max(BigDecimal.ZERO);
            case FREE_PRODUCT -> base; // main product price unchanged
        };
    }
}
