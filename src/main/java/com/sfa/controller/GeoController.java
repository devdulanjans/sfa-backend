package com.sfa.controller;

import com.sfa.dto.geo.CheckInRequest;
import com.sfa.entity.CustomerVisit;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.GeoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/visits")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
public class GeoController {

    private final GeoService geoService;

    @GetMapping
    public Page<CustomerVisit> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return geoService.listVisits(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "checkIn")));
    }

    @PostMapping("/check-in")
    public ResponseEntity<CustomerVisit> checkIn(@Valid @RequestBody CheckInRequest req) {
        return ResponseEntity.ok(geoService.checkIn(req));
    }

    @PostMapping("/check-out")
    public ResponseEntity<CustomerVisit> checkOut(
            @RequestParam(required = false) BigDecimal latitude,
            @RequestParam(required = false) BigDecimal longitude) {
        return ResponseEntity.ok(geoService.checkOut(latitude, longitude));
    }
}
