package com.sfa.controller;

import com.sfa.dto.MonthlySalesTargetDto;
import com.sfa.entity.Role;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.SalesTargetService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sales-targets")
@RequiredArgsConstructor
public class SalesTargetController {

    private final SalesTargetService salesTargetService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Page<MonthlySalesTargetDto> list(
            @RequestParam(required = false) UUID repId,
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return salesTargetService.list(repId, year, month, PageRequest.of(page, size));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public MonthlySalesTargetDto create(@RequestBody Map<String, Object> body,
                                         @AuthenticationPrincipal UserDetailsImpl principal) {
        UUID repId = UUID.fromString(body.get("salesRepId").toString());
        UUID productId = UUID.fromString(body.get("productId").toString());
        int year = Integer.parseInt(body.get("targetYear").toString());
        int month = Integer.parseInt(body.get("targetMonth").toString());
        BigDecimal qty = new BigDecimal(body.get("targetQty").toString());
        return salesTargetService.create(repId, productId, year, month, qty, principal.getId());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public MonthlySalesTargetDto update(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        BigDecimal qty = new BigDecimal(body.get("targetQty").toString());
        return salesTargetService.update(id, qty);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        salesTargetService.delete(id);
        return ResponseEntity.noContent().build();
    }

    /** This rep's own current-month targets, with today's computed daily target — used by the
     *  mobile dashboard card. Empty for any non-SALES_REP caller or when the feature is off. */
    @GetMapping("/my-today")
    @PreAuthorize("isAuthenticated()")
    public List<MonthlySalesTargetDto> myToday(@AuthenticationPrincipal UserDetailsImpl principal) {
        if (!Role.SALES_REP.equals(principal.getRoleName())) return List.of();
        return salesTargetService.getMyToday(principal.getId());
    }
}
