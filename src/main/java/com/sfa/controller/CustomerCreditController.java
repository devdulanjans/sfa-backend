package com.sfa.controller;

import com.sfa.entity.PosSale;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.PosService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pos/credit")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.POS)
public class CustomerCreditController {

    private final PosService posService;

    @GetMapping("/bills")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Page<PosController.PosSaleResponseDto> listBills(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<PosSale> bills = posService.listCreditBills(customerId, status, dateFrom, dateTo,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return bills.map(PosController.PosSaleResponseDto::from);
    }

    /** Sum of balance due across ALL credit bills matching the filter (not just the current page). */
    @GetMapping("/bills/total-due")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Map<String, BigDecimal> totalDue(
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo) {
        BigDecimal totalDue = posService.sumOutstandingCreditBalance(customerId, status, dateFrom, dateTo);
        return Map.of("totalDue", totalDue);
    }
}
