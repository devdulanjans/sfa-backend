package com.sfa.controller;

import com.sfa.entity.Promotion;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.repository.PromotionRepository;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/promotions/active")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
public class CustomerPromotionController {

    private final PromotionRepository promotionRepository;

    @GetMapping
    public List<PromotionBannerDto> getActiveBanners(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID customerId = principal.getLinkedCustomerId();
        if (customerId == null) return List.of();

        List<Promotion> promotions = promotionRepository
                .findActiveForCustomer(customerId, LocalDate.now());

        return promotions.stream().map(p -> new PromotionBannerDto(
                p.getId(),
                p.getName(),
                p.getType().name(),
                p.getDiscountValue(),
                p.getMinOrderQty(),
                p.getStartDate().toString(),
                p.getEndDate().toString(),
                p.getProducts().stream()
                        .map(pr -> new ProductItem(
                                pr.getId().toString(),
                                pr.getName(),
                                pr.getProductCode()))
                        .toList()
        )).toList();
    }

    public record ProductItem(String id, String name, String productCode) {}

    public record PromotionBannerDto(
            UUID             id,
            String           name,
            String           type,
            BigDecimal       discountValue,
            Integer          minOrderQty,
            String           startDate,
            String           endDate,
            List<ProductItem> products
    ) {}
}
