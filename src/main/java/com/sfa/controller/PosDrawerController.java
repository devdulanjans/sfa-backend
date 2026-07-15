package com.sfa.controller;

import com.sfa.dto.DrawerSessionDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.DrawerService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pos/drawer")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.POS)
public class PosDrawerController {

    private final DrawerService drawerService;

    @PostMapping("/open")
    @PreAuthorize("isAuthenticated()")
    public DrawerSessionDto open(@RequestBody Map<String, Object> body,
                                  @AuthenticationPrincipal UserDetailsImpl principal) {
        BigDecimal openingFloat = new BigDecimal(body.get("openingFloat").toString());
        String notes = (String) body.get("notes");
        return drawerService.openSession(principal.getId(), openingFloat, notes);
    }

    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<DrawerSessionDto> current(@AuthenticationPrincipal UserDetailsImpl principal) {
        DrawerSessionDto session = drawerService.getCurrentSession(principal.getId());
        return session != null ? ResponseEntity.ok(session) : ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/movements")
    @PreAuthorize("isAuthenticated()")
    public DrawerSessionDto recordMovement(@PathVariable UUID id, @RequestBody Map<String, Object> body,
                                            @AuthenticationPrincipal UserDetailsImpl principal) {
        String type = (String) body.get("type");
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String notes = (String) body.get("notes");
        return drawerService.recordMovement(id, type, amount, notes, principal.getId());
    }

    @PostMapping("/{id}/close")
    @PreAuthorize("isAuthenticated()")
    public DrawerSessionDto close(@PathVariable UUID id, @RequestBody Map<String, Object> body) {
        BigDecimal countedCash = new BigDecimal(body.get("countedCash").toString());
        String notes = (String) body.get("notes");
        return drawerService.closeSession(id, countedCash, notes);
    }

    @GetMapping("/sessions")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
    public Page<DrawerSessionDto> sessions(
            @RequestParam(required = false) UUID cashierId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return drawerService.listSessions(cashierId, status, dateFrom, dateTo,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "openedAt")));
    }
}
