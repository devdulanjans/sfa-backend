package com.sfa.controller;

import com.sfa.dto.invoice.InvoiceSummaryDto;
import com.sfa.entity.Invoice;
import com.sfa.entity.Role;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.InvoiceService;
import com.sfa.service.InvoiceService.InvoiceFilter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices")
@SecurityRequirement(name = "bearerAuth")
@RequiresLicense(LicensedPackage.SFA)
public class InvoiceController {

    private final InvoiceService invoiceService;

    @GetMapping
    @Operation(summary = "List invoices with optional filters")
    public ResponseEntity<Page<InvoiceSummaryDto>> list(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam(required = false) String invoiceNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID salesRepId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @PageableDefault(size = 20, sort = "issuedDate", direction = Sort.Direction.DESC) Pageable pageable) {

        // SALES_REP always sees only their own invoices, regardless of the salesRepId requested
        UUID effectiveSalesRepId = (user != null && Role.SALES_REP.equals(user.getRoleName()))
                ? user.getId()
                : salesRepId;

        InvoiceFilter filter = new InvoiceFilter(
                invoiceNo, orderNo, customerId, effectiveSalesRepId,
                createdFrom, createdTo,
                issuedFrom,  issuedTo,
                dueFrom,     dueTo);

        return ResponseEntity.ok(invoiceService.listInvoices(filter, pageable));
    }

    @PostMapping("/generate/{orderId}")
    @Operation(summary = "Generate invoice PDF from an approved order")
    public ResponseEntity<Invoice> generate(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(invoiceService.generateInvoice(orderId, user.getId()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Invoice> get(@PathVariable UUID id) {
        return ResponseEntity.ok(invoiceService.getInvoice(id));
    }

    @GetMapping("/by-order/{orderId}")
    @Operation(summary = "Get invoice for an order")
    public ResponseEntity<Invoice> getByOrder(@PathVariable UUID orderId) {
        return ResponseEntity.ok(invoiceService.getInvoiceByOrder(orderId));
    }

    @GetMapping("/{id}/pdf")
    @Operation(summary = "Download invoice as A4 PDF")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable UUID id) {
        byte[] bytes   = invoiceService.getPdfBytes(id);
        Invoice invoice = invoiceService.getInvoice(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + invoice.getInvoiceNumber() + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @GetMapping("/{id}/thermal")
    @Operation(summary = "Get ESC/POS bytes for Bluetooth thermal printer")
    public ResponseEntity<byte[]> thermalData(@PathVariable UUID id) {
        invoiceService.recordPrint(id);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(invoiceService.getThermalBytes(id));
    }
}
