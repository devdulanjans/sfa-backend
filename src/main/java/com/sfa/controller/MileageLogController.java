package com.sfa.controller;

import com.sfa.dto.CurrentMileageDto;
import com.sfa.dto.MileageLogDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.MileageLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/mileage")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
public class MileageLogController {

    private final MileageLogService mileageLogService;

    @GetMapping("/current")
    @PreAuthorize("isAuthenticated()")
    public CurrentMileageDto current(@AuthenticationPrincipal UserDetailsImpl principal) {
        return mileageLogService.getCurrentStatus(principal.getId());
    }

    @PostMapping("/start")
    @PreAuthorize("isAuthenticated()")
    public MileageLogDto start(@RequestBody Map<String, Object> body,
                                @AuthenticationPrincipal UserDetailsImpl principal) {
        BigDecimal startMileage = new BigDecimal(body.get("startMileage").toString());
        return mileageLogService.recordStart(principal.getId(), startMileage);
    }

    @PostMapping("/end")
    @PreAuthorize("isAuthenticated()")
    public MileageLogDto end(@RequestBody Map<String, Object> body,
                              @AuthenticationPrincipal UserDetailsImpl principal) {
        BigDecimal endMileage = new BigDecimal(body.get("endMileage").toString());
        return mileageLogService.recordEnd(principal.getId(), endMileage);
    }

    @GetMapping("/report")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Page<MileageLogDto> report(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return mileageLogService.getReport(userId, dateFrom, dateTo,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "logDate")));
    }
}
