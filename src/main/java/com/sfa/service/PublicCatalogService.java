package com.sfa.service;

import com.sfa.dto.publicsite.PublicCatalogDto;
import com.sfa.entity.CompanyProfile;
import com.sfa.entity.Product;
import com.sfa.entity.Promotion;
import com.sfa.entity.Tenant;
import com.sfa.repository.CompanyProfileRepository;
import com.sfa.repository.ProductRepository;
import com.sfa.repository.PromotionRepository;
import com.sfa.repository.TenantRepository;
import com.sfa.security.TenantScope;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Products shown on the public website: every active product of every active channel, tagged
 * with its business and the promotions open to everyone. Each product is priced inside its own
 * home channel (TenantScope), so batch prices and promotions of other channels never leak in.
 */
@Service
@RequiredArgsConstructor
public class PublicCatalogService {

    private static final int MAX_PAGE_SIZE = 100;

    private final ProductRepository productRepository;
    private final TenantRepository tenantRepository;
    private final CompanyProfileRepository companyProfileRepository;
    private final PromotionRepository promotionRepository;
    private final PricingEngine pricingEngine;
    private final TenantScope tenantScope;

    @Transactional(readOnly = true)
    public PublicCatalogDto.Page catalog(String query, String business, int page, int size) {
        String q = query == null ? "" : query.trim();
        String code = business == null ? "" : business.trim();
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        int safePage = Math.max(page, 0);

        Map<UUID, PublicCatalogDto.Business> businesses = new HashMap<>();
        List<Tenant> activeTenants = tenantRepository.findAll().stream()
                .filter(t -> t.getStatus() == Tenant.TenantStatus.ACTIVE)
                .toList();
        activeTenants.forEach(t -> businesses.put(t.getId(), toBusiness(t)));

        Page<Product> products = productRepository.findPublicCatalog(q, code,
                PageRequest.of(safePage, safeSize, Sort.by("tenant.name", "name")));

        LocalDate today = LocalDate.now();
        List<PublicCatalogDto.Item> items = new ArrayList<>();
        for (Product p : products.getContent()) {
            UUID tenantId = p.getTenant().getId();
            items.add(tenantScope.callInChannel(tenantId, () -> toItem(p, businesses.get(tenantId), today)));
        }

        // Only businesses that actually list something are offered as filters.
        List<PublicCatalogDto.Business> listed = activeTenants.stream()
                .filter(t -> productRepository.findPublicCatalog("", t.getCode(), PageRequest.of(0, 1))
                        .hasContent())
                .map(t -> businesses.get(t.getId()))
                .sorted(Comparator.comparing(PublicCatalogDto.Business::name, String.CASE_INSENSITIVE_ORDER))
                .toList();

        return new PublicCatalogDto.Page(items, products.getNumber(), products.getSize(),
                products.getTotalElements(), products.getTotalPages(), listed);
    }

    private PublicCatalogDto.Item toItem(Product p, PublicCatalogDto.Business business, LocalDate today) {
        // No customer: the general price (batch tier for qty 1, else default), with any
        // promotion that applies to all customers.
        PricingEngine.PriceResult price = pricingEngine.resolve(p.getId(), null);
        List<PublicCatalogDto.Promotion> promotions = promotionRepository
                .findActivePromotions(p.getId(), null, today).stream()
                .map(pr -> new PublicCatalogDto.Promotion(pr.getId(), pr.getName(), pr.getType().name(), label(pr)))
                .toList();
        String unit = p.getUnit() == null ? null
                : p.getUnit().getAbbreviation() != null && !p.getUnit().getAbbreviation().isBlank()
                        ? p.getUnit().getAbbreviation() : p.getUnit().getName();
        return new PublicCatalogDto.Item(
                p.getId(), p.getName(), p.getProductCode(), p.getDescription(),
                p.getCategory() != null ? p.getCategory().getName() : null,
                unit, price.unitPrice(), price.basePrice() != null ? price.basePrice() : price.unitPrice(),
                business, promotions,
                p.getImageObjectPath() == null ? null
                        : "/api/public/products/" + p.getId() + "/image?v="
                          + (p.getImageUpdatedAt() != null ? p.getImageUpdatedAt().toEpochMilli() : 0));
    }

    private PublicCatalogDto.Business toBusiness(Tenant t) {
        String name = companyProfileRepository.findByTenant_Id(t.getId())
                .map(CompanyProfile::getCompanyName)
                .filter(n -> n != null && !n.isBlank())
                .orElse(t.getName());
        return new PublicCatalogDto.Business(t.getCode(), name);
    }

    /** A short badge text spelling out the promotion's rule, e.g. "Buy 10 get 1 free". */
    static String label(Promotion p) {
        int min = p.getMinOrderQty() == null ? 1 : p.getMinOrderQty();
        String from = min > 1 ? " from " + min : "";
        return switch (p.getType()) {
            case PERCENTAGE -> plain(p.getDiscountValue()) + "% off" + from;
            case FIXED_AMOUNT -> "LKR " + plain(p.getDiscountValue()) + " off each" + from;
            case FREE_PRODUCT -> {
                int free = p.getMaxFreeCount() == null ? 1 : p.getMaxFreeCount();
                Integer repeat = p.getRepeatEveryQty();
                if (repeat != null && repeat != min) {
                    yield "From " + min + ", " + free + " free every " + repeat;
                }
                yield "Buy " + min + " get " + free + " free";
            }
        };
    }

    private static String plain(BigDecimal v) {
        return v == null ? "0" : v.stripTrailingZeros().toPlainString();
    }
}
