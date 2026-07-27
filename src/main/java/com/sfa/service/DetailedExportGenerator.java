package com.sfa.service;

import com.sfa.entity.Customer;
import com.sfa.entity.CustomerAddress;
import com.sfa.entity.Invoice;
import com.sfa.entity.Order;
import com.sfa.entity.OrderItem;
import com.sfa.entity.Product;
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
public class DetailedExportGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String[] HEADERS = {
            "Order #", "Invoice #", "Order Date", "Invoice Date", "Due Date", "Status",
            "Customer Code", "Customer Name", "Customer TIN", "Customer Phone", "Customer Email",
            "Customer Address", "Place of Supply", "Sales Rep",
            "Product Code", "Product Name", "Quantity", "Unit", "Unit Price",
            "Discount %", "Discount Amount", "Tax %", "Tax Amount", "Line Total", "Price Source",
            "Order Subtotal", "Order Discount Total", "Order Tax Total", "Order Grand Total"
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
}
