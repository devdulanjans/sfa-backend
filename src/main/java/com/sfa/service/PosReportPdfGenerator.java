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
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.sfa.dto.PosReportRowDto;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PosReportPdfGenerator {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Colombo"));

    private static final String[] HEADERS = {
            "Sale #", "Date", "Cashier", "Customer", "Payment", "Subtotal", "Discount", "Tax", "Total", "Credit", "Status"
    };
    private static final float[] WIDTHS = {10, 11, 11, 14, 8, 9, 9, 9, 10, 9, 9};

    public byte[] generate(List<PosReportRowDto> rows, String filterSummary) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(new PdfDocument(new PdfWriter(out)), PageSize.A4.rotate());
        doc.setMargins(24, 24, 24, 24);

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        DeviceRgb headerGray = new DeviceRgb(240, 240, 240);
        DeviceRgb darkBlue   = new DeviceRgb(0, 71, 131);

        doc.add(new Paragraph("POS Sales Report")
                .setFont(bold).setFontSize(16).setFontColor(darkBlue).setMarginBottom(2));
        if (filterSummary != null && !filterSummary.isBlank()) {
            doc.add(new Paragraph(filterSummary).setFont(regular).setFontSize(9).setMarginBottom(10));
        }

        Table table = new Table(UnitValue.createPercentArray(WIDTHS)).setWidth(UnitValue.createPercentValue(100));
        for (int i = 0; i < HEADERS.length; i++) {
            table.addHeaderCell(new Cell()
                    .add(new Paragraph(HEADERS[i]).setFont(bold).setFontSize(8))
                    .setBackgroundColor(headerGray).setPadding(4)
                    .setTextAlignment(i >= 5 && i <= 8 ? TextAlignment.RIGHT : TextAlignment.LEFT));
        }

        BigDecimal totalSum = BigDecimal.ZERO;
        for (PosReportRowDto row : rows) {
            table.addCell(cell(row.saleNumber(), regular, TextAlignment.LEFT));
            table.addCell(cell(DATE_FMT.format(row.createdAt()), regular, TextAlignment.LEFT));
            table.addCell(cell(row.cashierName(), regular, TextAlignment.LEFT));
            table.addCell(cell(row.customerName(), regular, TextAlignment.LEFT));
            table.addCell(cell(row.paymentMethod(), regular, TextAlignment.LEFT));
            table.addCell(cell(fmt(row.subtotal()), regular, TextAlignment.RIGHT));
            table.addCell(cell(fmt(row.discountAmount()), regular, TextAlignment.RIGHT));
            table.addCell(cell(fmt(row.taxAmount()), regular, TextAlignment.RIGHT));
            table.addCell(cell(fmt(row.total()), regular, TextAlignment.RIGHT));
            table.addCell(cell(row.creditStatus(), regular, TextAlignment.LEFT));
            table.addCell(cell(row.status(), regular, TextAlignment.LEFT));
            totalSum = totalSum.add(row.total() != null ? row.total() : BigDecimal.ZERO);
        }

        if (rows.isEmpty()) {
            Cell empty = new Cell(1, HEADERS.length)
                    .add(new Paragraph("No sales match the selected filters").setFont(regular).setFontSize(9))
                    .setTextAlignment(TextAlignment.CENTER).setPadding(10);
            table.addCell(empty);
        } else {
            Cell totalLabel = new Cell(1, 8)
                    .add(new Paragraph("Total").setFont(bold).setFontSize(9))
                    .setTextAlignment(TextAlignment.RIGHT).setPadding(4);
            Cell totalValue = new Cell(1, 3)
                    .add(new Paragraph(fmt(totalSum)).setFont(bold).setFontSize(9))
                    .setTextAlignment(TextAlignment.RIGHT).setPadding(4);
            table.addCell(totalLabel);
            table.addCell(totalValue);
        }

        doc.add(table);
        doc.close();
        return out.toByteArray();
    }

    private Cell cell(String text, PdfFont font, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(text != null ? text : "").setFont(font).setFontSize(8))
                .setTextAlignment(align).setPadding(3).setBorder(Border.NO_BORDER)
                .setBorderBottom(new com.itextpdf.layout.borders.SolidBorder(0.5f));
    }

    private String fmt(BigDecimal v) {
        BigDecimal amt = v != null ? v : BigDecimal.ZERO;
        boolean negative = amt.signum() < 0;
        String formatted = String.format("%,.2f", amt.abs());
        return "LKR " + (negative ? "(" + formatted + ")" : formatted);
    }
}
