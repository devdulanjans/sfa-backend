package com.sfa.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.geom.PageSize;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.borders.Border;
import com.itextpdf.layout.borders.SolidBorder;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.sfa.dto.order.OrderResponseDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class OrderExportGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String[] HEADERS = {
            "Order #", "Customer", "Customer Code", "Source", "Sales Rep",
            "Invoice #", "Order Date", "Total", "Status"
    };

    public byte[] generateExcel(List<OrderResponseDto> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Orders");

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
                org.apache.poi.ss.usermodel.Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }

            int r = 1;
            for (OrderResponseDto row : rows) {
                Row xRow = sheet.createRow(r++);
                xRow.createCell(0).setCellValue(row.orderNumber());
                xRow.createCell(1).setCellValue(row.customer() != null ? row.customer().name() : "");
                xRow.createCell(2).setCellValue(row.customer() != null ? row.customer().customerCode() : "");
                xRow.createCell(3).setCellValue(row.orderSource());
                xRow.createCell(4).setCellValue(row.salesRep() != null ? row.salesRep().fullName() : "");
                xRow.createCell(5).setCellValue(row.invoiceNumber() != null ? row.invoiceNumber() : "");
                xRow.createCell(6).setCellValue(fmtInstant(row.orderDate()));
                org.apache.poi.ss.usermodel.Cell totalCell = xRow.createCell(7);
                totalCell.setCellValue(row.total() != null ? row.total().doubleValue() : 0);
                totalCell.setCellStyle(moneyStyle);
                xRow.createCell(8).setCellValue(row.status());
            }

            for (int c = 0; c < HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateCsv(List<OrderResponseDto> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", HEADERS)).append("\n");
        for (OrderResponseDto row : rows) {
            sb.append(csv(row.orderNumber())).append(',')
              .append(csv(row.customer() != null ? row.customer().name() : "")).append(',')
              .append(csv(row.customer() != null ? row.customer().customerCode() : "")).append(',')
              .append(csv(row.orderSource())).append(',')
              .append(csv(row.salesRep() != null ? row.salesRep().fullName() : "")).append(',')
              .append(csv(row.invoiceNumber() != null ? row.invoiceNumber() : "")).append(',')
              .append(csv(fmtInstant(row.orderDate()))).append(',')
              .append(csv(row.total() != null ? String.valueOf(row.total()) : "")).append(',')
              .append(csv(row.status())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] generatePdf(List<OrderResponseDto> rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(new PdfDocument(new PdfWriter(out)), PageSize.A4.rotate());
        doc.setMargins(24, 24, 24, 24);

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb headerGray = new DeviceRgb(240, 240, 240);
        DeviceRgb darkBlue   = new DeviceRgb(0, 71, 131);

        doc.add(new Paragraph("Orders").setFont(bold).setFontSize(16).setFontColor(darkBlue).setMarginBottom(10));

        float[] widths = {12, 16, 12, 10, 14, 12, 10, 8, 8};
        Table table = new Table(UnitValue.createPercentArray(widths)).setWidth(UnitValue.createPercentValue(100));
        for (String header : HEADERS) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(header).setFont(bold).setFontSize(8))
                    .setBackgroundColor(headerGray).setPadding(4));
        }

        for (OrderResponseDto row : rows) {
            table.addCell(cell(row.orderNumber(), regular));
            table.addCell(cell(row.customer() != null ? row.customer().name() : "", regular));
            table.addCell(cell(row.customer() != null ? row.customer().customerCode() : "", regular));
            table.addCell(cell(row.orderSource(), regular));
            table.addCell(cell(row.salesRep() != null ? row.salesRep().fullName() : "", regular));
            table.addCell(cell(row.invoiceNumber() != null ? row.invoiceNumber() : "", regular));
            table.addCell(cell(fmtInstant(row.orderDate()), regular));
            table.addCell(cell(row.total() != null ? String.format("%,.2f", row.total()) : "", regular));
            table.addCell(cell(row.status(), regular));
        }

        if (rows.isEmpty()) {
            table.addCell(new Cell(1, HEADERS.length)
                    .add(new Paragraph("No orders match the selected filters").setFont(regular).setFontSize(9))
                    .setTextAlignment(TextAlignment.CENTER).setPadding(10));
        }

        doc.add(table);
        doc.close();
        return out.toByteArray();
    }

    private Cell cell(String text, PdfFont font) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "").setFont(font).setFontSize(8))
                .setPadding(3).setBorder(Border.NO_BORDER)
                .setBorderBottom(new SolidBorder(0.5f));
    }

    private String fmtInstant(java.time.Instant i) {
        return i != null ? DATE_FMT.format(i.atZone(ZoneOffset.UTC)) : "";
    }

    private String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
