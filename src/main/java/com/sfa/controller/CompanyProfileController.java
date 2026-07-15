package com.sfa.controller;

import com.sfa.dto.CompanyProfileDto;
import com.sfa.dto.CompanyProfileUpdateRequest;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.CompanyProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/company-profile")
@RequiredArgsConstructor
public class CompanyProfileController {

    private final CompanyProfileService companyProfileService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public CompanyProfileDto get() {
        return companyProfileService.get();
    }

    @PutMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanyProfileDto update(@Valid @RequestBody CompanyProfileUpdateRequest req,
                                     @AuthenticationPrincipal UserDetailsImpl principal) {
        return companyProfileService.update(req, principal.getId());
    }

    @PostMapping(value = "/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public CompanyProfileDto uploadLogo(@RequestParam("file") MultipartFile file,
                                        @AuthenticationPrincipal UserDetailsImpl principal) {
        return companyProfileService.uploadLogo(file, principal.getId());
    }

    // Public — rendered directly via <img src> in the admin UI and printed receipts,
    // and browsers don't attach the JWT bearer header to image requests.
    @GetMapping("/logo")
    public ResponseEntity<byte[]> logo() {
        byte[] bytes = companyProfileService.getLogoBytes();
        String contentType = companyProfileService.getLogoContentType();
        return ResponseEntity.ok()
                .contentType(contentType != null ? MediaType.parseMediaType(contentType) : MediaType.APPLICATION_OCTET_STREAM)
                .body(bytes);
    }
}
