package com.sfa.controller;

import com.sfa.dto.CashierSummaryDto;
import com.sfa.dto.DailyReportDto;
import com.sfa.dto.PosReportRowDto;
import com.sfa.exception.BusinessException;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.PosDailyReportService;
import com.sfa.service.PosReportExcelGenerator;
import com.sfa.service.PosReportPdfGenerator;
import com.sfa.service.PosService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/pos/reports")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
@RequiresLicense(LicensedPackage.POS)
public class PosReportController {

    private static final DateTimeFormatter SUMMARY_DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy")
            .withZone(ZoneId.of("Asia/Colombo"));

    private final PosService posService;
    private final PosDailyReportService dailyReportService;
    private final PosReportExcelGenerator excelGenerator;
    private final PosReportPdfGenerator   pdfGenerator;

    @GetMapping("/daily")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
    public DailyReportDto daily(
            @RequestParam LocalDate date,
            @RequestParam(required = false) UUID cashierId) {
        return dailyReportService.getDailyReport(date, cashierId);
    }

    @GetMapping
    public Page<PosReportRowDto> list(
            @RequestParam(required = false) UUID cashierId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return posService.getReport(cashierId, customerId, productId, dateFrom, dateTo, PageRequest.of(page, size));
    }

    @GetMapping("/cashiers")
    public List<CashierSummaryDto> cashiers() {
        return posService.listCashiers();
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam String format,
            @RequestParam(required = false) UUID cashierId,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) UUID productId,
            @RequestParam(required = false) Instant dateFrom,
            @RequestParam(required = false) Instant dateTo,
            @RequestParam(required = false) String cashierLabel,
            @RequestParam(required = false) String customerLabel,
            @RequestParam(required = false) String productLabel) throws IOException {

        List<PosReportRowDto> rows = posService.getReportRowsForExport(cashierId, customerId, productId, dateFrom, dateTo);
        String filterSummary = buildFilterSummary(dateFrom, dateTo, cashierLabel, customerLabel, productLabel);

        byte[] bytes;
        MediaType contentType;
        String filename;

        if ("xlsx".equalsIgnoreCase(format)) {
            bytes = excelGenerator.generate(rows);
            contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            filename = "pos-sales-report.xlsx";
        } else if ("pdf".equalsIgnoreCase(format)) {
            bytes = pdfGenerator.generate(rows, filterSummary);
            contentType = MediaType.APPLICATION_PDF;
            filename = "pos-sales-report.pdf";
        } else {
            throw new BusinessException("Unsupported export format: " + format);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType)
                .body(bytes);
    }

    private String buildFilterSummary(Instant dateFrom, Instant dateTo, String cashierLabel,
                                       String customerLabel, String productLabel) {
        List<String> parts = new ArrayList<>();
        if (dateFrom != null || dateTo != null) {
            String from = dateFrom != null ? SUMMARY_DATE_FMT.format(dateFrom) : "…";
            String to   = dateTo   != null ? SUMMARY_DATE_FMT.format(dateTo)   : "…";
            parts.add("Date: " + from + " to " + to);
        }
        if (cashierLabel  != null && !cashierLabel.isBlank())  parts.add("Cashier: "  + cashierLabel);
        if (customerLabel != null && !customerLabel.isBlank()) parts.add("Customer: " + customerLabel);
        if (productLabel  != null && !productLabel.isBlank())  parts.add("Product: "  + productLabel);
        return String.join("   ·   ", parts);
    }
}
