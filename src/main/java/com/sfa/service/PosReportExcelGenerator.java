package com.sfa.service;

import com.sfa.dto.PosReportRowDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PosReportExcelGenerator {

    private static final String[] HEADERS = {
            "Sale #", "Date", "Cashier", "Customer", "Payment Method",
            "Subtotal", "Discount", "Tax", "Total", "Credit Status", "Status"
    };

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Colombo"));

    public byte[] generate(List<PosReportRowDto> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("POS Sales Report");

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
            for (PosReportRowDto row : rows) {
                Row xRow = sheet.createRow(r++);
                xRow.createCell(0).setCellValue(row.saleNumber());
                xRow.createCell(1).setCellValue(DATE_FMT.format(row.createdAt()));
                xRow.createCell(2).setCellValue(row.cashierName());
                xRow.createCell(3).setCellValue(row.customerName());
                xRow.createCell(4).setCellValue(row.paymentMethod());
                setMoney(xRow.createCell(5), row.subtotal(), moneyStyle);
                setMoney(xRow.createCell(6), row.discountAmount(), moneyStyle);
                setMoney(xRow.createCell(7), row.taxAmount(), moneyStyle);
                setMoney(xRow.createCell(8), row.total(), moneyStyle);
                xRow.createCell(9).setCellValue(row.creditStatus());
                xRow.createCell(10).setCellValue(row.status());
            }

            for (int c = 0; c < HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void setMoney(Cell cell, java.math.BigDecimal value, CellStyle style) {
        cell.setCellValue(value != null ? value.doubleValue() : 0);
        cell.setCellStyle(style);
    }
}
