package com.sfa.service;

import com.sfa.entity.Customer;
import com.sfa.entity.CustomerAddress;
import com.sfa.entity.Distributor;
import com.sfa.entity.Invoice;
import com.sfa.entity.Order;
import com.sfa.entity.OrderItem;
import com.sfa.entity.Product;
import com.sfa.entity.User;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Flat, one-row-per-product-line Excel export of one or many orders/invoices — meant for
 * bulk-importing full order/invoice detail (header + customer/tax info + every product
 * line) into an external ERP, rather than for human reading. Every row repeats the
 * order/invoice header fields, since that's the layout most ERP bulk importers expect
 * (no join needed on their end). Single-record exports (order/invoice detail pages) and
 * bulk exports (order/invoice list pages, filtered) both go through the same row-writing
 * logic below — a bulk export is just a single-order export repeated per matching record
 * into one shared sheet.
 */
@Service
@RequiredArgsConstructor
public class DetailedExportGenerator {

    private final CompanyProfileService companyProfileService;

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final String[] HEADERS = {
            "Order #", "Invoice #", "Order Date", "Invoice Date", "Due Date", "Status",
            "Customer Code", "Customer Name", "Customer TIN", "Customer Phone", "Customer Email",
            "Customer Address", "Place of Supply", "Sales Rep",
            "Product Code", "Product Name", "Quantity", "Unit", "Unit Price",
            "Discount %", "Discount Amount", "Tax %", "Tax Amount", "Line Total", "Price Source",
            "Order Subtotal", "Order Discount Total", "Order Tax Total", "Order Grand Total"
    };

    /**
     * Standard "Secondary Sales Data" template used by external distributor-management
     * integrations — one row per product line. Several columns (RouteCode, RouteName,
     * CommonOutletCode, vol, Method) have no backing data anywhere in this system and are
     * always left blank rather than guessed.
     */
    private static final String[] SECONDARY_SALES_HEADERS = {
            "Date", "Time", "Longitude", "Latitude", "Company",
            "DistributorCode", "DistributorName", "DSRId", "DSRName",
            "RouteCode", "RouteName", "OutletCode", "OutletName", "CommonOutletCode",
            "SerialNo", "Type", "Method", "ItemId", "ItemName", "SalesQty", "vol",
            "DiscountValue", "FreeIssueValue", "NetSalesValue"
    };

    private record Entry(Order order, String invoiceNumber, LocalDate issuedDate, LocalDate dueDate,
                          String status, BigDecimal subtotal, BigDecimal discountTotal,
                          BigDecimal taxTotal, BigDecimal grandTotal) {}

    public byte[] generateOrderExcel(Order order) throws IOException {
        return generateOrdersDetailExcel(List.of(order));
    }

    public byte[] generateInvoiceExcel(Invoice invoice) throws IOException {
        return generateInvoicesDetailExcel(List.of(invoice));
    }

    public byte[] generateOrdersDetailExcel(List<Order> orders) throws IOException {
        return build(orders.stream().map(this::toEntry).toList());
    }

    public byte[] generateInvoicesDetailExcel(List<Invoice> invoices) throws IOException {
        return build(invoices.stream().map(this::toEntry).toList());
    }

    private Entry toEntry(Order order) {
        return new Entry(order, null, null, null, order.getStatus().name(),
                order.getSubtotal(), order.getDiscountAmount(), order.getTaxAmount(), order.getTotal());
    }

    private Entry toEntry(Invoice invoice) {
        return new Entry(invoice.getOrder(), invoice.getInvoiceNumber(), invoice.getIssuedDate(), invoice.getDueDate(),
                invoice.getStatus().name(),
                invoice.getSubtotal(), invoice.getDiscountTotal(), invoice.getTaxTotal(), invoice.getTotal());
    }

    private byte[] build(List<Entry> entries) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Details");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (Entry entry : entries) {
                r = writeEntry(sheet, r, entry, moneyStyle);
            }

            for (int c = 0; c < HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private int writeEntry(Sheet sheet, int startRow, Entry entry, CellStyle moneyStyle) {
        Order order = entry.order();
        Customer customer = order.getCustomer();

        String orderNo       = order.getOrderNumber();
        String orderDateStr  = fmtInstant(order.getOrderDate());
        String issuedDateStr = fmtDate(entry.issuedDate());
        String dueDateStr    = fmtDate(entry.dueDate());
        String custCode      = customer != null ? customer.getCustomerCode() : "";
        String custName      = customer != null ? customer.getName() : "";
        String custTin       = customer != null && customer.getTaxNumber() != null ? customer.getTaxNumber() : "";
        String custPhone     = customer != null && customer.getPhone() != null ? customer.getPhone() : "";
        String custEmail     = customer != null && customer.getEmail() != null ? customer.getEmail() : "";
        String custAddress   = primaryAddressLine(customer);
        String placeOfSupply = customer != null && customer.getPlaceOfSupplier() != null ? customer.getPlaceOfSupplier() : "";
        String salesRepName  = order.getSalesRep() != null ? order.getSalesRep().getFullName() : "";

        int r = startRow;
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            Row row = sheet.createRow(r++);
            int c = 0;
            row.createCell(c++).setCellValue(orderNo);
            row.createCell(c++).setCellValue(entry.invoiceNumber() != null ? entry.invoiceNumber() : "");
            row.createCell(c++).setCellValue(orderDateStr);
            row.createCell(c++).setCellValue(issuedDateStr);
            row.createCell(c++).setCellValue(dueDateStr);
            row.createCell(c++).setCellValue(entry.status());
            row.createCell(c++).setCellValue(custCode);
            row.createCell(c++).setCellValue(custName);
            row.createCell(c++).setCellValue(custTin);
            row.createCell(c++).setCellValue(custPhone);
            row.createCell(c++).setCellValue(custEmail);
            row.createCell(c++).setCellValue(custAddress);
            row.createCell(c++).setCellValue(placeOfSupply);
            row.createCell(c++).setCellValue(salesRepName);
            row.createCell(c++).setCellValue(product != null ? product.getProductCode() : "");
            row.createCell(c++).setCellValue(product != null ? product.getName() : "");
            row.createCell(c++).setCellValue(item.getQuantity() != null ? item.getQuantity().doubleValue() : 0);
            row.createCell(c++).setCellValue(unitLabel(product));
            money(row, c++, item.getUnitPrice(), moneyStyle);
            row.createCell(c++).setCellValue(item.getDiscountPct() != null ? item.getDiscountPct().doubleValue() : 0);
            money(row, c++, item.getDiscountAmount(), moneyStyle);
            row.createCell(c++).setCellValue(item.getTaxPct() != null ? item.getTaxPct().doubleValue() : 0);
            money(row, c++, item.getTaxAmount(), moneyStyle);
            money(row, c++, item.getLineTotal(), moneyStyle);
            row.createCell(c++).setCellValue(item.getPriceSource() != null ? item.getPriceSource() : "");
            money(row, c++, entry.subtotal(), moneyStyle);
            money(row, c++, entry.discountTotal(), moneyStyle);
            money(row, c++, entry.taxTotal(), moneyStyle);
            money(row, c, entry.grandTotal(), moneyStyle);
        }
        return r;
    }

    public byte[] generateSecondarySalesExcel(Invoice invoice) throws IOException {
        return generateSecondarySalesExcel(List.of(invoice));
    }

    public byte[] generateSecondarySalesExcel(List<Invoice> invoices) throws IOException {
        String companyName = companyProfileService.get().companyName();

        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Secondary Sales");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle moneyStyle = wb.createCellStyle();
            moneyStyle.setDataFormat(wb.createDataFormat().getFormat("#,##0.00"));

            Row header = sheet.createRow(0);
            for (int c = 0; c < SECONDARY_SALES_HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(SECONDARY_SALES_HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (Invoice invoice : invoices) {
                r = writeSecondarySalesEntry(sheet, r, invoice, companyName, moneyStyle);
            }

            for (int c = 0; c < SECONDARY_SALES_HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private int writeSecondarySalesEntry(Sheet sheet, int startRow, Invoice invoice,
                                          String companyName, CellStyle moneyStyle) {
        Order order = invoice.getOrder();
        Customer customer = invoice.getCustomer();
        Distributor distributor = order.getDistributor();
        User salesRep = order.getSalesRep();

        String dateStr      = fmtDate(invoice.getIssuedDate());
        String timeStr      = fmtTime(order.getOrderDate());
        String longitude    = customer != null && customer.getLongitude() != null ? customer.getLongitude().toPlainString() : "";
        String latitude     = customer != null && customer.getLatitude()  != null ? customer.getLatitude().toPlainString()  : "";
        String distCode     = distributor != null ? distributor.getCode() : "";
        String distName     = distributor != null ? distributor.getName() : "";
        String dsrId        = salesRep != null ? salesRep.getUsername() : "";
        String dsrName      = salesRep != null ? salesRep.getFullName() : "";
        String outletCode   = customer != null ? customer.getCustomerCode() : "";
        String outletName   = customer != null ? customer.getName() : "";
        String invoiceNumber = invoice.getInvoiceNumber() != null ? invoice.getInvoiceNumber() : "";

        int r = startRow;
        for (OrderItem item : order.getItems()) {
            Product product = item.getProduct();
            Row row = sheet.createRow(r++);
            int c = 0;
            row.createCell(c++).setCellValue(dateStr);
            row.createCell(c++).setCellValue(timeStr);
            row.createCell(c++).setCellValue(longitude);
            row.createCell(c++).setCellValue(latitude);
            row.createCell(c++).setCellValue(companyName != null ? companyName : "");
            row.createCell(c++).setCellValue(distCode);
            row.createCell(c++).setCellValue(distName);
            row.createCell(c++).setCellValue(dsrId);
            row.createCell(c++).setCellValue(dsrName);
            row.createCell(c++).setCellValue(""); // RouteCode — no matching data in this system
            row.createCell(c++).setCellValue(""); // RouteName — no matching data in this system
            row.createCell(c++).setCellValue(outletCode);
            row.createCell(c++).setCellValue(outletName);
            row.createCell(c++).setCellValue(""); // CommonOutletCode — no matching data in this system
            row.createCell(c++).setCellValue(invoiceNumber);
            row.createCell(c++).setCellValue(item.getPriceSource() != null ? item.getPriceSource() : "");
            row.createCell(c++).setCellValue(""); // Method — no line-level payment method in this system
            row.createCell(c++).setCellValue(product != null ? product.getProductCode() : "");
            row.createCell(c++).setCellValue(product != null ? product.getName() : "");
            row.createCell(c++).setCellValue(item.getQuantity() != null ? item.getQuantity().doubleValue() : 0);
            row.createCell(c++).setCellValue(""); // vol — no matching data in this system
            money(row, c++, item.getDiscountAmount(), moneyStyle);
            money(row, c++, freeIssueValue(item), moneyStyle);
            money(row, c, item.getLineTotal(), moneyStyle);
        }
        return r;
    }

    /**
     * FREE_PRODUCT lines are stored with a zero or fully-discounted unitPrice (see
     * PricingEngine.resolveFreeItems), so the actual value of stock given away for free
     * isn't persisted — it's derived here from the product's current default price.
     */
    private BigDecimal freeIssueValue(OrderItem item) {
        if (!"FREE_PRODUCT".equals(item.getPriceSource()) || item.getProduct() == null) return BigDecimal.ZERO;
        BigDecimal defaultPrice = item.getProduct().getDefaultPrice();
        BigDecimal qty = item.getQuantity();
        if (defaultPrice == null || qty == null) return BigDecimal.ZERO;
        return defaultPrice.multiply(qty);
    }

    private void money(Row row, int col, BigDecimal value, CellStyle style) {
        Cell cell = row.createCell(col);
        cell.setCellValue(value != null ? value.doubleValue() : 0);
        cell.setCellStyle(style);
    }

    private String unitLabel(Product product) {
        if (product == null || product.getUnit() == null) return "";
        var unit = product.getUnit();
        return unit.getAbbreviation() != null && !unit.getAbbreviation().isBlank()
                ? unit.getAbbreviation() : unit.getName();
    }

    private String primaryAddressLine(Customer c) {
        if (c == null || c.getAddresses() == null || c.getAddresses().isEmpty()) return "";
        return c.getAddresses().stream()
                .filter(CustomerAddress::isPrimary)
                .findFirst()
                .map(CustomerAddress::getAddressLine)
                .orElseGet(() -> c.getAddresses().get(0).getAddressLine());
    }

    private String fmtDate(LocalDate d) {
        return d != null ? d.format(DATE_FMT) : "";
    }

    private String fmtInstant(Instant instant) {
        return instant != null ? DATE_FMT.format(instant.atZone(ZoneOffset.UTC)) : "";
    }

    private String fmtTime(Instant instant) {
        return instant != null ? TIME_FMT.format(instant.atZone(ZoneOffset.UTC)) : "";
    }
}
