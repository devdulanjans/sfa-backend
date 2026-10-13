package com.sfa.controller;

import com.sfa.dto.pricing.CustomerPromotionDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.repository.PromotionRepository;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/promotions/active")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
public class CustomerPromotionController {

    private final PromotionRepository promotionRepository;

    /** Active promotions for the logged-in customer, with their full rules — see CustomerPromotionDto. */
    @GetMapping
    @Transactional(readOnly = true)
    public List<CustomerPromotionDto> getActiveBanners(
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID customerId = principal.getLinkedCustomerId();
        if (customerId == null) return List.of();

        return promotionRepository.findActiveForCustomer(customerId, LocalDate.now()).stream()
                .map(CustomerPromotionDto::from)
                .toList();
    }
}
