package com.sfa.controller;

import com.sfa.dto.ret.CreateReturnRequest;
import com.sfa.entity.Return;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.ReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/returns")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
public class ReturnController {

    private final ReturnService returnService;

    @GetMapping
    public Page<Return> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return returnService.list(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @PostMapping
    public ResponseEntity<Return> create(@Valid @RequestBody CreateReturnRequest req) {
        Return ret = returnService.create(req);
        return ResponseEntity.created(URI.create("/api/returns/" + ret.getId())).body(ret);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Return> get(@PathVariable UUID id) {
        return ResponseEntity.ok(returnService.getById(id));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        Return ret = returnService.getById(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + ret.getReturnNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(returnService.getPdfBytes(id));
    }

    @GetMapping("/{id}/thermal")
    public ResponseEntity<byte[]> thermalData(@PathVariable UUID id) {
        returnService.recordPrint(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(returnService.getThermalBytes(id));
    }

    // TEMPORARY — dev/QA aid for visually verifying the thermal receipt layout without
    // a physical printer. Remove this endpoint (and ReturnService.getThermalPreviewBytes,
    // ReturnDamageNoteGenerator.generateReturnThermalPreview, and sfa-mobile's return
    // print-preview screen) before shipping to production.
    @GetMapping("/{id}/thermal-preview")
    public ResponseEntity<byte[]> thermalPreview(@PathVariable UUID id) {
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .body(returnService.getThermalPreviewBytes(id));
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Return updateStatus(@PathVariable UUID id, @RequestParam String status) {
        return returnService.updateStatus(id, status);
    }
}
