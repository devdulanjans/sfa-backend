package com.sfa.controller;

import com.sfa.dto.lead.ConvertLeadRequest;
import com.sfa.dto.lead.CreateLeadRequest;
import com.sfa.dto.lead.LeadDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.LeadService;
import com.sfa.service.SystemSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/leads")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_REP')")
@RequiresLicense(LicensedPackage.SFA)
public class LeadController {

    private final LeadService leadService;
    private final SystemSettingService settingService;

    @GetMapping
    public Page<LeadDto> list(@AuthenticationPrincipal UserDetailsImpl user,
                              @RequestParam(defaultValue = "") String search,
                              @RequestParam(required = false) String status,
                              @RequestParam(defaultValue = "0") int page,
                              @RequestParam(defaultValue = "20") int size) {
        return leadService.list(user, search, status, PageRequest.of(page, size));
    }

    /** Lets clients decide what to show before creating: whether leads auto-convert, and
     *  whether the caller may approve/reject them. */
    @GetMapping("/config")
    public Map<String, Boolean> config(@AuthenticationPrincipal UserDetailsImpl user) {
        return Map.of(
                "autoConvert", settingService.isLeadAutoConvertEnabled(),
                "canConvert", leadService.canReview(user));
    }

    @GetMapping("/{id}")
    public LeadDto getById(@AuthenticationPrincipal UserDetailsImpl user, @PathVariable UUID id) {
        return leadService.getById(id, user);
    }

    @PostMapping
    public ResponseEntity<LeadDto> create(@AuthenticationPrincipal UserDetailsImpl user,
                                          @Valid @RequestBody CreateLeadRequest req) {
        LeadDto dto = leadService.create(req, user);
        return ResponseEntity.created(URI.create("/api/leads/" + dto.id())).body(dto);
    }

    @PutMapping("/{id}")
    public LeadDto update(@AuthenticationPrincipal UserDetailsImpl user,
                          @PathVariable UUID id,
                          @Valid @RequestBody CreateLeadRequest req) {
        return leadService.update(id, req, user);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal UserDetailsImpl user, @PathVariable UUID id) {
        leadService.delete(id, user);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/convert")
    public LeadDto convert(@AuthenticationPrincipal UserDetailsImpl user,
                           @PathVariable UUID id,
                           @Valid @RequestBody(required = false) ConvertLeadRequest req) {
        return leadService.convert(id, req, user);
    }

    @PostMapping("/{id}/reject")
    public LeadDto reject(@AuthenticationPrincipal UserDetailsImpl user,
                          @PathVariable UUID id,
                          @RequestBody(required = false) Map<String, String> body) {
        return leadService.reject(id, body != null ? body.get("reason") : null, user);
    }
}
