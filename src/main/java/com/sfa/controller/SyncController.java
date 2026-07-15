package com.sfa.controller;

import com.sfa.dto.customer.CustomerDto;
import com.sfa.dto.product.ProductDto;
import com.sfa.entity.Role;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.ProductRepository;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/sync")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
public class SyncController {

    private final CustomerRepository customerRepository;
    private final ProductRepository  productRepository;

    @GetMapping("/delta")
    public Map<String, Object> delta(
            @RequestParam(required = false) String since,
            @AuthenticationPrincipal UserDetailsImpl user) {

        Instant threshold = Instant.EPOCH;
        if (since != null) {
            try {
                threshold = Instant.parse(since);
            } catch (DateTimeParseException e1) {
                try {
                    threshold = LocalDateTime.parse(since).toInstant(ZoneOffset.UTC);
                } catch (DateTimeParseException e2) {
                    log.warn("Could not parse 'since' param '{}', defaulting to Epoch", since);
                }
            }
        }

        // SALES_REP with assigned customers only receives their assigned customers in the delta
        Set<UUID> restricted = (user != null
                && Role.SALES_REP.equals(user.getRoleName())
                && !user.getAssignedCustomerIds().isEmpty())
                ? user.getAssignedCustomerIds()
                : null;

        List<CustomerDto> customers = restricted != null
                ? customerRepository.findByIdsUpdatedSinceWithProducts(restricted, threshold)
                        .stream().map(CustomerDto::from).toList()
                : customerRepository.findUpdatedSinceWithProducts(threshold)
                        .stream().map(CustomerDto::from).toList();

        List<ProductDto> products = productRepository.findUpdatedSince(threshold)
                .stream().map(ProductDto::from).toList();

        return Map.of(
                "customers", customers,
                "products",  products,
                "syncedAt",  Instant.now().toString()
        );
    }
}
