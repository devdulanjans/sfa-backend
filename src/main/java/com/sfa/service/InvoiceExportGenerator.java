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
import com.sfa.dto.invoice.InvoiceSummaryDto;
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
public class InvoiceExportGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private static final String[] HEADERS = {
            "Invoice #", "Order #", "Customer", "Phone", "Email", "Salesperson",
            "Order Date", "Invoice Date", "Due Date", "Total", "Prints", "Status"
    };

    public byte[] generateExcel(List<InvoiceSummaryDto> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Invoices");

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
            for (InvoiceSummaryDto row : rows) {
                Row xRow = sheet.createRow(r++);
                xRow.createCell(0).setCellValue(row.invoiceNumber());
                xRow.createCell(1).setCellValue(row.orderNumber());
                xRow.createCell(2).setCellValue(row.customer() != null ? row.customer().name() : "");
                xRow.createCell(3).setCellValue(row.customer() != null ? row.customer().phone() : "");
                xRow.createCell(4).setCellValue(row.customer() != null ? row.customer().email() : "");
                xRow.createCell(5).setCellValue(row.salesRepName());
                xRow.createCell(6).setCellValue(fmtInstant(row.orderDate()));
                xRow.createCell(7).setCellValue(fmtDate(row.issuedDate()));
                xRow.createCell(8).setCellValue(fmtDate(row.dueDate()));
                org.apache.poi.ss.usermodel.Cell totalCell = xRow.createCell(9);
                totalCell.setCellValue(row.total() != null ? row.total().doubleValue() : 0);
                totalCell.setCellStyle(moneyStyle);
                xRow.createCell(10).setCellValue(row.printCount() != null ? row.printCount() : 0);
                xRow.createCell(11).setCellValue(row.status());
            }

            for (int c = 0; c < HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateCsv(List<InvoiceSummaryDto> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", HEADERS)).append("\n");
        for (InvoiceSummaryDto row : rows) {
            sb.append(csv(row.invoiceNumber())).append(',')
              .append(csv(row.orderNumber())).append(',')
              .append(csv(row.customer() != null ? row.customer().name() : "")).append(',')
              .append(csv(row.customer() != null ? row.customer().phone() : "")).append(',')
              .append(csv(row.customer() != null ? row.customer().email() : "")).append(',')
              .append(csv(row.salesRepName())).append(',')
              .append(csv(fmtInstant(row.orderDate()))).append(',')
              .append(csv(fmtDate(row.issuedDate()))).append(',')
              .append(csv(fmtDate(row.dueDate()))).append(',')
              .append(csv(row.total() != null ? String.valueOf(row.total()) : "")).append(',')
              .append(csv(row.printCount() != null ? String.valueOf(row.printCount()) : "")).append(',')
              .append(csv(row.status())).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] generatePdf(List<InvoiceSummaryDto> rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(new PdfDocument(new PdfWriter(out)), PageSize.A4.rotate());
        doc.setMargins(24, 24, 24, 24);

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb headerGray = new DeviceRgb(240, 240, 240);
        DeviceRgb darkBlue   = new DeviceRgb(0, 71, 131);

        doc.add(new Paragraph("Invoices").setFont(bold).setFontSize(16).setFontColor(darkBlue).setMarginBottom(10));

        float[] widths = {10, 10, 16, 10, 14, 12, 9, 9, 9, 8, 6, 8};
        Table table = new Table(UnitValue.createPercentArray(widths)).setWidth(UnitValue.createPercentValue(100));
        for (String header : HEADERS) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(header).setFont(bold).setFontSize(8))
                    .setBackgroundColor(headerGray).setPadding(4));
        }

        for (InvoiceSummaryDto row : rows) {
            table.addCell(cell(row.invoiceNumber(), regular));
            table.addCell(cell(row.orderNumber(), regular));
            table.addCell(cell(row.customer() != null ? row.customer().name() : "", regular));
            table.addCell(cell(row.customer() != null ? row.customer().phone() : "", regular));
            table.addCell(cell(row.customer() != null ? row.customer().email() : "", regular));
            table.addCell(cell(row.salesRepName(), regular));
            table.addCell(cell(fmtInstant(row.orderDate()), regular));
            table.addCell(cell(fmtDate(row.issuedDate()), regular));
            table.addCell(cell(fmtDate(row.dueDate()), regular));
            table.addCell(cell(row.total() != null ? String.format("%,.2f", row.total()) : "", regular));
            table.addCell(cell(row.printCount() != null ? String.valueOf(row.printCount()) : "", regular));
            table.addCell(cell(row.status(), regular));
        }

        if (rows.isEmpty()) {
            table.addCell(new Cell(1, HEADERS.length)
                    .add(new Paragraph("No invoices match the selected filters").setFont(regular).setFontSize(9))
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

    private String fmtDate(java.time.LocalDate d) {
        return d != null ? d.format(DATE_FMT) : "";
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
