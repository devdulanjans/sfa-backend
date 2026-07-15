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
import com.sfa.dto.customer.CustomerDto;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class CustomerExportGenerator {

    private static final String[] HEADERS = {
            "Customer Code", "Name", "Contact Person", "Phone", "Email",
            "Category", "Tax Type", "Credit Limit", "Credit Days", "Status", "Address"
    };

    public byte[] generateExcel(List<CustomerDto> rows) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Customers");

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
            for (CustomerDto row : rows) {
                Row xRow = sheet.createRow(r++);
                xRow.createCell(0).setCellValue(row.customerCode());
                xRow.createCell(1).setCellValue(row.name());
                xRow.createCell(2).setCellValue(row.contactPerson());
                xRow.createCell(3).setCellValue(row.phone());
                xRow.createCell(4).setCellValue(row.email());
                xRow.createCell(5).setCellValue(row.categoryName());
                xRow.createCell(6).setCellValue(row.taxType());
                org.apache.poi.ss.usermodel.Cell creditCell = xRow.createCell(7);
                creditCell.setCellValue(row.creditLimit() != null ? row.creditLimit() : 0);
                creditCell.setCellStyle(moneyStyle);
                xRow.createCell(8).setCellValue(row.creditDays() != null ? row.creditDays() : 0);
                xRow.createCell(9).setCellValue(row.status());
                xRow.createCell(10).setCellValue(primaryAddress(row));
            }

            for (int c = 0; c < HEADERS.length; c++) {
                sheet.autoSizeColumn(c);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    public byte[] generateCsv(List<CustomerDto> rows) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join(",", HEADERS)).append("\n");
        for (CustomerDto row : rows) {
            sb.append(csv(row.customerCode())).append(',')
              .append(csv(row.name())).append(',')
              .append(csv(row.contactPerson())).append(',')
              .append(csv(row.phone())).append(',')
              .append(csv(row.email())).append(',')
              .append(csv(row.categoryName())).append(',')
              .append(csv(row.taxType())).append(',')
              .append(csv(row.creditLimit() != null ? String.valueOf(row.creditLimit()) : "")).append(',')
              .append(csv(row.creditDays() != null ? String.valueOf(row.creditDays()) : "")).append(',')
              .append(csv(row.status())).append(',')
              .append(csv(primaryAddress(row))).append('\n');
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    public byte[] generatePdf(List<CustomerDto> rows) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(new PdfDocument(new PdfWriter(out)), PageSize.A4.rotate());
        doc.setMargins(24, 24, 24, 24);

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb headerGray = new DeviceRgb(240, 240, 240);
        DeviceRgb darkBlue   = new DeviceRgb(0, 71, 131);

        doc.add(new Paragraph("Customers").setFont(bold).setFontSize(16).setFontColor(darkBlue).setMarginBottom(10));

        float[] widths = {10, 16, 12, 10, 14, 10, 8, 8, 6, 8, 18};
        Table table = new Table(UnitValue.createPercentArray(widths)).setWidth(UnitValue.createPercentValue(100));
        for (String header : HEADERS) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(header).setFont(bold).setFontSize(8))
                    .setBackgroundColor(headerGray).setPadding(4));
        }

        for (CustomerDto row : rows) {
            table.addCell(cell(row.customerCode(), regular));
            table.addCell(cell(row.name(), regular));
            table.addCell(cell(row.contactPerson(), regular));
            table.addCell(cell(row.phone(), regular));
            table.addCell(cell(row.email(), regular));
            table.addCell(cell(row.categoryName(), regular));
            table.addCell(cell(row.taxType(), regular));
            table.addCell(cell(row.creditLimit() != null ? String.format("%,.2f", row.creditLimit()) : "", regular));
            table.addCell(cell(row.creditDays() != null ? String.valueOf(row.creditDays()) : "", regular));
            table.addCell(cell(row.status(), regular));
            table.addCell(cell(primaryAddress(row), regular));
        }

        if (rows.isEmpty()) {
            table.addCell(new Cell(1, HEADERS.length)
                    .add(new Paragraph("No customers match the selected filters").setFont(regular).setFontSize(9))
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

    private String primaryAddress(CustomerDto row) {
        return row.addresses().stream()
                .filter(a -> a.isPrimary())
                .findFirst()
                .or(() -> row.addresses().stream().findFirst())
                .map(a -> a.addressLine())
                .orElse("");
    }

    private String csv(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
