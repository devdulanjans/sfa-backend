package com.sfa.controller;

import com.sfa.dto.invoice.InvoiceSummaryDto;
import com.sfa.entity.Invoice;
import com.sfa.entity.Role;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.DetailedExportGenerator;
import com.sfa.service.InvoiceExportGenerator;
import com.sfa.service.InvoiceService;
import com.sfa.service.InvoiceService.InvoiceFilter;
import com.sfa.exception.BusinessException;
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

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices")
@SecurityRequirement(name = "bearerAuth")
@RequiresLicense(LicensedPackage.SFA)
public class InvoiceController {

    private final InvoiceService invoiceService;
    private final InvoiceExportGenerator invoiceExportGenerator;
    private final DetailedExportGenerator detailedExportGenerator;

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

        InvoiceFilter filter = buildFilter(user, invoiceNo, orderNo, customerId, salesRepId,
                createdFrom, createdTo, issuedFrom, issuedTo, dueFrom, dueTo);

        return ResponseEntity.ok(invoiceService.listInvoices(filter, pageable));
    }

    @GetMapping("/export")
    @Operation(summary = "Export invoices matching the given filters")
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam String format,
            @RequestParam(required = false) String invoiceNo,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID salesRepId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate createdTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issuedTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo) throws IOException {

        InvoiceFilter filter = buildFilter(user, invoiceNo, orderNo, customerId, salesRepId,
                createdFrom, createdTo, issuedFrom, issuedTo, dueFrom, dueTo);
        List<InvoiceSummaryDto> rows = invoiceService.listInvoices(filter, Pageable.unpaged()).getContent();

        byte[] bytes;
        MediaType contentType;
        String filename;

        switch (format.toLowerCase()) {
            case "xlsx" -> {
                bytes = invoiceExportGenerator.generateExcel(rows);
                contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                filename = "invoices.xlsx";
            }
            case "csv" -> {
                bytes = invoiceExportGenerator.generateCsv(rows);
                contentType = MediaType.parseMediaType("text/csv");
                filename = "invoices.csv";
            }
            case "pdf" -> {
                bytes = invoiceExportGenerator.generatePdf(rows);
                contentType = MediaType.APPLICATION_PDF;
                filename = "invoices.pdf";
            }
            default -> throw new BusinessException("Unsupported export format: " + format);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType)
                .body(bytes);
    }

    private InvoiceFilter buildFilter(
            UserDetailsImpl user, String invoiceNo, String orderNo, UUID customerId, UUID salesRepId,
            LocalDate createdFrom, LocalDate createdTo, LocalDate issuedFrom, LocalDate issuedTo,
            LocalDate dueFrom, LocalDate dueTo) {
        // SALES_REP always sees only their own invoices, regardless of the salesRepId requested
        UUID effectiveSalesRepId = (user != null && Role.SALES_REP.equals(user.getRoleName()))
                ? user.getId()
                : salesRepId;

        return new InvoiceFilter(
                invoiceNo, orderNo, customerId, effectiveSalesRepId,
                createdFrom, createdTo,
                issuedFrom,  issuedTo,
                dueFrom,     dueTo);
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

    @GetMapping("/{id}/export-details")
    @Operation(summary = "Export this invoice's full detail (one row per product line) as Excel — for ERP import")
    public ResponseEntity<byte[]> exportDetails(@PathVariable UUID id) throws IOException {
        byte[] bytes = detailedExportGenerator.generateInvoiceExcel(invoiceService.getInvoice(id));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-details.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @GetMapping("/export-details")
    @Operation(summary = "Bulk-export every invoice matching the given filters, full detail (one row per product line) — for ERP import")
    public ResponseEntity<byte[]> exportDetailsBulk(
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo) throws IOException {

        InvoiceFilter filter = buildFilter(user, invoiceNo, orderNo, customerId, salesRepId,
                createdFrom, createdTo, issuedFrom, issuedTo, dueFrom, dueTo);
        var invoices = invoiceService.getInvoicesForExport(filter);
        byte[] bytes = detailedExportGenerator.generateInvoicesDetailExcel(invoices);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoices-details.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
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
