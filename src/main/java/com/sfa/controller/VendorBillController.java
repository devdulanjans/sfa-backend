package com.sfa.controller;

import com.sfa.dto.CreateVendorBillRequest;
import com.sfa.dto.RecordVendorBillPaymentRequest;
import com.sfa.dto.VendorBillDto;
import com.sfa.dto.VendorBillPaymentDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.VendorBillPaymentService;
import com.sfa.service.VendorBillService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/finance/vendor-bills")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
@RequiresLicense(LicensedPackage.FINANCE)
public class VendorBillController {

    private final VendorBillService vendorBillService;
    private final VendorBillPaymentService vendorBillPaymentService;

    @GetMapping
    public Page<VendorBillDto> list(
            @RequestParam(required = false) UUID vendorId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return vendorBillService.list(vendorId, status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "billDate")));
    }

    @GetMapping("/{id}")
    public VendorBillDto get(@PathVariable UUID id) {
        return vendorBillService.get(id);
    }

    @PostMapping
    public VendorBillDto create(@RequestBody CreateVendorBillRequest request,
                                 @AuthenticationPrincipal UserDetailsImpl principal) {
        return vendorBillService.create(request, principal.getId());
    }

    @GetMapping("/{id}/payments")
    public List<VendorBillPaymentDto> payments(@PathVariable UUID id) {
        return vendorBillPaymentService.listForBill(id);
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','FINANCE_USER')")
    public VendorBillPaymentDto recordPayment(@PathVariable UUID id,
                                               @RequestBody RecordVendorBillPaymentRequest request,
                                               @AuthenticationPrincipal UserDetailsImpl principal) {
        return vendorBillPaymentService.recordPayment(id, request, principal.getId());
    }
}
