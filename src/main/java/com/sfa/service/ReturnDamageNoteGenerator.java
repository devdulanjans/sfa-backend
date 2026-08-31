package com.sfa.service;

import com.itextpdf.io.font.constants.StandardFonts;
import com.itextpdf.io.image.ImageData;
import com.itextpdf.io.image.ImageDataFactory;
import com.itextpdf.kernel.colors.ColorConstants;
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
import com.itextpdf.layout.element.Div;
import com.itextpdf.layout.element.Image;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.element.Text;
import com.itextpdf.layout.properties.HorizontalAlignment;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.sfa.dto.CompanyProfileDto;
import com.sfa.entity.Customer;
import com.sfa.entity.Damage;
import com.sfa.entity.Product;
import com.sfa.entity.Return;
import com.sfa.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Prints the Return Note / Damage Note issued when a sales rep records a customer
 * return or a damaged-stock report — a lightweight goods-movement document (product,
 * quantity, unit price, amount), styled after {@link InvoicePdfGenerator}'s A4/ESC-POS
 * layout (same header, ORIGINAL/COPY-N labeling, items table, signatures) but without
 * any tax/payment section since these aren't billing documents. Return/Damage notes
 * are not billing documents, so unit price and amount are always printed as zero
 * regardless of the product's {@link Product#getDefaultPrice()} — only quantity matters.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReturnDamageNoteGenerator {

    private final CompanyProfileService companyProfileService;

    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Colombo"));

    private static final int LOGO_WIDTH_PX = 160;

    private record NoteLine(String productName, String productCode, BigDecimal qty,
                             BigDecimal unitPrice, BigDecimal amount) {}

    private record NoteData(
            String docLabel,
            String noteNumber,
            Instant date,
            String customerName,
            String customerAddress,
            String customerPhone,
            String repName,
            String orderNumber,
            String reasonLabel,
            String reasonText,
            List<NoteLine> lines,
            BigDecimal total,
            int printCount
    ) {}

    // ── Public entry points ──────────────────────────────────────────────────

    public byte[] generateReturnPdf(Return ret) throws IOException {
        return generatePdf(toNoteData(ret));
    }

    public byte[] generateReturnThermal(Return ret) {
        return generateThermal(toNoteData(ret));
    }

    public byte[] generateDamagePdf(Damage damage) throws IOException {
        return generatePdf(toNoteData(damage));
    }

    public byte[] generateDamageThermal(Damage damage) {
        return generateThermal(toNoteData(damage));
    }

    // TEMPORARY — dev/QA aid, remove before production (see InvoicePdfGenerator's
    // matching generateThermalPreview and the notes in InvoiceController/InvoiceService).
    public byte[] generateReturnThermalPreview(Return ret) throws IOException {
        return generateThermalPreview(toNoteData(ret));
    }

    public byte[] generateDamageThermalPreview(Damage damage) throws IOException {
        return generateThermalPreview(toNoteData(damage));
    }

    // ── Entity -> NoteData adapters ──────────────────────────────────────────

    private NoteData toNoteData(Return ret) {
        List<NoteLine> lines = ret.getItems().stream().map(this::toLine).toList();
        BigDecimal total = lines.stream().map(NoteLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int printCount = ret.getPrintCount() == null ? 1 : ret.getPrintCount();
        return new NoteData(
                "RETURN NOTE", ret.getReturnNumber(), ret.getReturnDate(),
                ret.getCustomer().getName(), primaryAddressLine(ret.getCustomer()),
                displayPhone(ret.getCustomer().getPhone()), repName(ret.getSalesRep()),
                ret.getOrder() != null ? ret.getOrder().getOrderNumber() : null,
                "Reason", ret.getReason(), lines, total, printCount);
    }

    private NoteData toNoteData(Damage damage) {
        List<NoteLine> lines = damage.getItems().stream().map(this::toLine).toList();
        BigDecimal total = lines.stream().map(NoteLine::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        int printCount = damage.getPrintCount() == null ? 1 : damage.getPrintCount();
        return new NoteData(
                "DAMAGE NOTE", damage.getDamageNumber(), damage.getDamageDate(),
                damage.getCustomer().getName(), primaryAddressLine(damage.getCustomer()),
                displayPhone(damage.getCustomer().getPhone()), repName(damage.getReportedBy()),
                null, "Description", damage.getDescription(), lines, total, printCount);
    }

    private NoteLine toLine(com.sfa.entity.ReturnItem item) {
        Product p = item.getProduct();
        return new NoteLine(p.getName(), p.getProductCode(), item.getQuantity(),
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private NoteLine toLine(com.sfa.entity.DamageItem item) {
        Product p = item.getProduct();
        return new NoteLine(p.getName(), p.getProductCode(), item.getQuantity(),
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    // ── A4 PDF ────────────────────────────────────────────────────────────────

    private byte[] generatePdf(NoteData note) throws IOException {
        CompanyProfileDto profile = companyProfileService.get();
        byte[] logoBytes = fetchLogoBytes(profile);

        String copyLabel = note.printCount() <= 1 ? "ORIGINAL" : "COPY " + (note.printCount() - 1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(new PdfDocument(new PdfWriter(out)), PageSize.A4);
        doc.setMargins(28, 28, 28, 28);

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);

        DeviceRgb darkBlue   = new DeviceRgb(0, 71, 131);
        DeviceRgb headerGray = new DeviceRgb(242, 242, 242);
        SolidBorder outer = new SolidBorder(ColorConstants.BLACK, 1.2f);
        SolidBorder div   = new SolidBorder(ColorConstants.BLACK, 0.5f);

        Table frame = new Table(UnitValue.createPercentArray(new float[]{100f}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        // ── Header ────────────────────────────────────────────────────────────
        Table hdr = new Table(UnitValue.createPercentArray(new float[]{55, 45}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        Cell brandCell = new Cell().setBorder(Border.NO_BORDER).setBorderRight(div).setPadding(8);
        if (logoBytes != null) {
            try {
                Table brand = new Table(UnitValue.createPercentArray(new float[]{30, 70}))
                        .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);
                Image logo = new Image(ImageDataFactory.create(logoBytes)).setHeight(20);
                brand.addCell(new Cell().add(logo).setBorder(Border.NO_BORDER).setPadding(0)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE));
                brand.addCell(new Cell()
                        .add(new Paragraph(profile.companyName()).setFont(bold).setFontSize(11))
                        .setBorder(Border.NO_BORDER).setPadding(0)
                        .setVerticalAlignment(VerticalAlignment.MIDDLE));
                brandCell.add(brand);
            } catch (Exception e) {
                brandCell.add(new Paragraph(profile.companyName()).setFont(bold).setFontSize(24)
                        .setFontColor(darkBlue).setMarginBottom(1));
            }
        } else {
            brandCell.add(new Paragraph(profile.companyName()).setFont(bold).setFontSize(24)
                    .setFontColor(darkBlue).setMarginBottom(1));
        }
        hdr.addCell(brandCell);

        hdr.addCell(new Cell()
                .add(new Paragraph(copyLabel).setFont(bold).setFontSize(8)
                        .setTextAlignment(TextAlignment.RIGHT).setMarginBottom(2))
                .add(new Paragraph(note.docLabel()).setFont(bold).setFontSize(18)
                        .setFontColor(ColorConstants.WHITE)
                        .setBackgroundColor(darkBlue)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setBorder(new SolidBorder(ColorConstants.BLACK, 1f)).setPadding(4))
                .setBorder(Border.NO_BORDER).setPadding(8));

        frame.addCell(new Cell().add(hdr).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderTop(outer)
                .setBorderBottom(div).setPadding(0));

        // ── Note number / date ────────────────────────────────────────────────
        Table noRow = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);
        noRow.addCell(new Cell()
                .add(kvRow(note.docLabel().equals("RETURN NOTE") ? "Return No :" : "Damage No :",
                        note.noteNumber(), bold, regular))
                .setBorder(Border.NO_BORDER).setPadding(8));
        noRow.addCell(new Cell()
                .add(new Paragraph("Date : " + fmtInstant(note.date())).setFont(regular).setFontSize(9)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setPadding(8));
        frame.addCell(new Cell().add(noRow).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(0));

        // ── Customer / Rep / Order ────────────────────────────────────────────
        Table meta = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);
        meta.addCell(new Cell()
                .add(kvRow("Customer :", note.customerName(), bold, regular))
                .add(kvRow("Address :",  safe(note.customerAddress(), "—"), bold, regular))
                .add(kvRow("Contact No :", note.customerPhone(), bold, regular))
                .setBorder(Border.NO_BORDER).setBorderRight(div).setPadding(8));
        Cell metaRight = new Cell()
                .add(kvRow(note.docLabel().equals("RETURN NOTE") ? "Sales Rep :" : "Reported By :",
                        safe(note.repName(), "—"), bold, regular));
        if (note.orderNumber() != null) {
            metaRight.add(kvRow("Order No :", note.orderNumber(), bold, regular));
        }
        meta.addCell(metaRight.setBorder(Border.NO_BORDER).setPadding(8));
        frame.addCell(new Cell().add(meta).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(0));

        // ── Items table ───────────────────────────────────────────────────────
        Table items = new Table(UnitValue.createPercentArray(new float[]{5, 45, 12, 19, 19}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        String[] hdrs = {"No.", "Product", "Qty", "Unit Price\n(Rs.)", "Amount\n(Rs.)"};
        TextAlignment[] aligns = {TextAlignment.CENTER, TextAlignment.LEFT, TextAlignment.CENTER,
                TextAlignment.RIGHT, TextAlignment.RIGHT};
        for (int i = 0; i < hdrs.length; i++) {
            items.addHeaderCell(new Cell()
                    .add(new Paragraph(hdrs[i]).setFont(bold).setFontSize(8).setTextAlignment(aligns[i]))
                    .setBackgroundColor(headerGray).setPadding(4));
        }

        int no = 1;
        for (NoteLine line : note.lines()) {
            items.addCell(iCell(String.valueOf(no++), regular, TextAlignment.CENTER));
            items.addCell(iCell(line.productName(), regular, TextAlignment.LEFT));
            items.addCell(iCell(line.qty().toPlainString(), regular, TextAlignment.CENTER));
            items.addCell(iCell(fmtAmount(line.unitPrice()), regular, TextAlignment.RIGHT));
            items.addCell(iCell(fmtAmount(line.amount()), regular, TextAlignment.RIGHT));
        }
        int blanks = Math.max(0, 5 - note.lines().size());
        for (int i = 0; i < blanks; i++) {
            for (int j = 0; j < 5; j++)
                items.addCell(new Cell().add(new Paragraph(" ").setFontSize(9)).setHeight(18).setPadding(3));
        }

        frame.addCell(new Cell().add(items).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(0));

        // ── Total ─────────────────────────────────────────────────────────────
        Table totals = new Table(UnitValue.createPercentArray(new float[]{62, 38}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);
        totRow(totals, "Total Value", fmtAmount(note.total()), true, bold);
        frame.addCell(new Cell().add(totals).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(0));

        // ── Reason / Description ─────────────────────────────────────────────
        frame.addCell(new Cell()
                .add(kvRow(note.reasonLabel() + " :", safe(note.reasonText(), "—"), bold, regular))
                .setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(8));

        // ── Signatures ────────────────────────────────────────────────────────
        Table sigs = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        Cell custSigCell = new Cell().setBorder(Border.NO_BORDER).setBorderRight(div).setPadding(8);
        custSigCell.add(new Paragraph("\n\n"));
        custSigCell.add(new Paragraph("________________________").setFont(regular).setFontSize(9));
        custSigCell.add(new Paragraph("Customer Signature")
                .setFont(regular).setFontSize(7).setFontColor(ColorConstants.GRAY));
        sigs.addCell(custSigCell);

        Cell repSigCell = new Cell().setBorder(Border.NO_BORDER).setPadding(8);
        repSigCell.add(new Paragraph("\n\n"));
        repSigCell.add(new Paragraph("________________________").setFont(regular).setFontSize(9));
        repSigCell.add(new Paragraph(note.docLabel().equals("RETURN NOTE") ? "Sales Rep Signature" : "Reported By Signature")
                .setFont(regular).setFontSize(7).setFontColor(ColorConstants.GRAY));
        if (note.repName() != null) {
            repSigCell.add(new Paragraph(note.repName()).setFont(bold).setFontSize(8).setMarginTop(1));
        }
        sigs.addCell(repSigCell);

        frame.addCell(new Cell().add(sigs).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(outer).setPadding(0));

        doc.add(frame);
        doc.close();
        return out.toByteArray();
    }

    // ── ESC/POS thermal (64-char width, same convention as InvoicePdfGenerator) ─

    private byte[] generateThermal(NoteData note) {
        CompanyProfileDto profile = companyProfileService.get();
        final int W = 64;
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        esc(buf, 0x1B, 0x40);
        esc(buf, 0x1B, 0x61, 0x01);

        byte[] logoBytes = fetchLogoBytes(profile);
        if (logoBytes != null) {
            printLogoEscPos(buf, logoBytes, LOGO_WIDTH_PX);
            txt(buf, "\n");
        }

        esc(buf, 0x1B, 0x21, 0x18); // double-height + bold
        txt(buf, trunc(profile.companyName(), W) + "\n");
        esc(buf, 0x1B, 0x21, 0x00);
        txt(buf, "\n");

        String copyLabel = note.printCount() <= 1 ? "**  ORIGINAL  **" : "**  COPY " + (note.printCount() - 1) + "  **";
        txt(buf, copyLabel + "\n");

        esc(buf, 0x1D, 0x42, 0x01); // reverse video on
        esc(buf, 0x1B, 0x21, 0x18); // double-height + bold
        txt(buf, note.docLabel() + "\n");
        esc(buf, 0x1B, 0x21, 0x00);
        esc(buf, 0x1D, 0x42, 0x00); // reverse video off
        txt(buf, "\n");

        esc(buf, 0x1B, 0x61, 0x00); // left align
        txt(buf, "=".repeat(W) + "\n\n");

        String noLabel = note.docLabel().equals("RETURN NOTE") ? "Return No  : " : "Damage No  : ";
        txt(buf, noLabel + note.noteNumber() + "\n");
        txt(buf, "Date       : " + fmtInstant(note.date()) + "\n");
        txt(buf, "=".repeat(W) + "\n\n");

        txt(buf, "Customer   : " + trunc(note.customerName(), 51) + "\n");
        txt(buf, "Address    : " + trunc(safe(note.customerAddress(), "—"), 51) + "\n");
        txt(buf, "Contact No : " + note.customerPhone() + "\n");
        txt(buf, (note.docLabel().equals("RETURN NOTE") ? "Sales Rep  : " : "Reported By: ")
                + trunc(safe(note.repName(), "—"), 51) + "\n");
        if (note.orderNumber() != null) {
            txt(buf, "Order No   : " + note.orderNumber() + "\n");
        }
        txt(buf, "=".repeat(W) + "\n\n");

        txt(buf, pR("No", 2) + " " + pR("Product", 26) + " " + pL("Qty", 6) + " " + pL("Price", 12) + " " + pL("Amount", 15) + "\n");
        txt(buf, "-".repeat(W) + "\n");
        int no = 1;
        for (NoteLine line : note.lines()) {
            txt(buf, pR(String.valueOf(no++), 2) + " " + pR(trunc(line.productName(), 26), 26) + " "
                    + pL(line.qty().toPlainString(), 6) + " " + pL(fmtAmount(line.unitPrice()), 12) + " "
                    + pL(fmtAmount(line.amount()), 15) + "\n");
        }
        txt(buf, "=".repeat(W) + "\n\n");

        int lw = 46, rw = W - lw;
        esc(buf, 0x1B, 0x21, 0x08); // bold
        txt(buf, pR("Total Value", lw) + pL(fmtAmount(note.total()), rw) + "\n");
        esc(buf, 0x1B, 0x21, 0x00);
        txt(buf, "=".repeat(W) + "\n\n");

        txt(buf, wrapLabeledField(note.reasonLabel() + " : ", safe(note.reasonText(), "—"), W));
        txt(buf, "-".repeat(W) + "\n\n");

        txt(buf, dots(30) + "\n");
        txt(buf, "Customer Signature\n\n");
        txt(buf, dots(30) + "\n");
        txt(buf, (note.docLabel().equals("RETURN NOTE") ? "Sales Rep Signature" : "Reported By Signature") + "\n");
        if (note.repName() != null) {
            esc(buf, 0x1B, 0x45, 0x01); // bold on
            txt(buf, trunc(note.repName(), W) + "\n");
            esc(buf, 0x1B, 0x45, 0x00); // bold off
        }

        txt(buf, "\n\n\n\n");
        esc(buf, 0x1D, 0x56, 0x42, 0x03);
        return buf.toByteArray();
    }

    // ── TEMPORARY — dev/QA aid, remove before production ────────────────────
    // Thermal receipt preview (narrow PDF mirroring generateThermal's exact
    // content/layout) — same idea as InvoicePdfGenerator.generateThermalPreview.
    // Remove this whole block (through rWrapped/wrapValue below) plus the matching
    // TEMPORARY markers in DamageController/DamageService/ReturnController/
    // ReturnService and sfa-mobile's print-preview screens before shipping to production.

    private static final float RECEIPT_WIDTH   = 280f; // ~99mm — narrow "receipt" page
    private static final float RECEIPT_MARGIN  = 8f;
    private static final float RECEIPT_FONT_SZ = 6.5f; // fits the same 64-char width as generateThermal's W

    private byte[] generateThermalPreview(NoteData note) throws IOException {
        CompanyProfileDto profile = companyProfileService.get();
        final int W = 64;

        int itemCount = note.lines().size();
        float estimatedHeight = 500f + (itemCount * 20f) + 150f; // fixed content + items + logo
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(new PdfDocument(new PdfWriter(out)),
                new PageSize(RECEIPT_WIDTH, Math.max(500f, estimatedHeight)));
        doc.setMargins(RECEIPT_MARGIN, RECEIPT_MARGIN, RECEIPT_MARGIN, RECEIPT_MARGIN);

        PdfFont mono     = PdfFontFactory.createFont(StandardFonts.COURIER);
        PdfFont monoBold = PdfFontFactory.createFont(StandardFonts.COURIER_BOLD);

        // ── Header ────────────────────────────────────────────────────────────
        byte[] logoBytes = fetchLogoBytes(profile);
        if (logoBytes != null) {
            try {
                ImageData logoData = ImageDataFactory.create(logoBytes);
                float scale = Math.min(28f / logoData.getHeight(), (RECEIPT_WIDTH - 2 * RECEIPT_MARGIN) / logoData.getWidth());
                Image logo = new Image(logoData)
                        .setWidth(logoData.getWidth() * scale)
                        .setHeight(logoData.getHeight() * scale)
                        .setHorizontalAlignment(HorizontalAlignment.CENTER);
                doc.add(logo);
            } catch (Exception e) {
                log.warn("Could not decode logo for {} thermal preview: {}", note.docLabel(), e.toString());
            }
        }
        doc.add(rLine(trunc(profile.companyName(), W), monoBold, RECEIPT_FONT_SZ + 1.5f, TextAlignment.CENTER));
        doc.add(rLine("", mono));

        String copyLabel = note.printCount() <= 1 ? "**  ORIGINAL  **" : "**  COPY " + (note.printCount() - 1) + "  **";
        doc.add(rLine(copyLabel, monoBold, RECEIPT_FONT_SZ, TextAlignment.CENTER));
        doc.add(new Paragraph(note.docLabel()).setFont(monoBold).setFontSize(RECEIPT_FONT_SZ + 2)
                .setTextAlignment(TextAlignment.CENTER).setMultipliedLeading(1.1f).setMarginBottom(2));
        doc.add(rLine("", mono));
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Note number / date ────────────────────────────────────────────────
        String noLabel = note.docLabel().equals("RETURN NOTE") ? "Return No  : " : "Damage No  : ";
        doc.add(rLine(noLabel + note.noteNumber(), mono));
        doc.add(rLine("Date       : " + fmtInstant(note.date()), mono));
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Customer / Rep / Order ────────────────────────────────────────────
        doc.add(rLine("Customer   : " + trunc(note.customerName(), 51), mono));
        doc.add(rLine("Address    : " + trunc(safe(note.customerAddress(), "—"), 51), mono));
        doc.add(rLine("Contact No : " + note.customerPhone(), mono));
        doc.add(rLine((note.docLabel().equals("RETURN NOTE") ? "Sales Rep  : " : "Reported By: ")
                + trunc(safe(note.repName(), "—"), 51), mono));
        if (note.orderNumber() != null) {
            doc.add(rLine("Order No   : " + note.orderNumber(), mono));
        }
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Items ─────────────────────────────────────────────────────────────
        doc.add(rLine(pR("No", 2) + " " + pR("Product", 26) + " " + pL("Qty", 6) + " " + pL("Price", 12) + " " + pL("Amount", 15), monoBold));
        doc.add(rLine("-".repeat(W), mono));
        int no = 1;
        for (NoteLine line : note.lines()) {
            doc.add(rLine(pR(String.valueOf(no++), 2) + " " + pR(trunc(line.productName(), 26), 26) + " "
                    + pL(line.qty().toPlainString(), 6) + " " + pL(fmtAmount(line.unitPrice()), 12) + " "
                    + pL(fmtAmount(line.amount()), 15), mono));
        }
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Total ─────────────────────────────────────────────────────────────
        int lw = 46, rw = W - lw;
        doc.add(rLine(pR("Total Value", lw) + pL(fmtAmount(note.total()), rw), monoBold, RECEIPT_FONT_SZ + 1f, TextAlignment.LEFT));
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        doc.add(rWrapped(note.reasonLabel() + " : ", safe(note.reasonText(), "—"), W, mono));
        doc.add(rLine("-".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Signatures ────────────────────────────────────────────────────────
        doc.add(rLine(dots(30), mono));
        doc.add(rLine("Customer Signature", mono));
        doc.add(rLine("", mono));
        doc.add(rLine(dots(30), mono));
        doc.add(rLine(note.docLabel().equals("RETURN NOTE") ? "Sales Rep Signature" : "Reported By Signature", mono));
        if (note.repName() != null) {
            doc.add(rLine(trunc(note.repName(), W), monoBold));
        }

        doc.close();
        return out.toByteArray();
    }

    private Paragraph rLine(String text, PdfFont font) {
        return rLine(text, font, RECEIPT_FONT_SZ, TextAlignment.LEFT);
    }

    private Paragraph rLine(String text, PdfFont font, float size, TextAlignment align) {
        return new Paragraph(text).setFont(font).setFontSize(size)
                .setTextAlignment(align).setMultipliedLeading(1.1f).setMarginBottom(0);
    }

    /**
     * PDF-preview counterpart to wrapLabeledField — see InvoicePdfGenerator's rWrapped
     * for why continuation lines need a left margin instead of space-indentation here.
     */
    private Div rWrapped(String label, String value, int totalWidth, PdfFont font) {
        List<String> lines = wrapValue(label, value, totalWidth);
        float indentPt = label.length() * 0.6f * RECEIPT_FONT_SZ;
        Div div = new Div().setMargin(0);
        for (int i = 0; i < lines.size(); i++) {
            Paragraph p = new Paragraph((i == 0 ? label : "") + lines.get(i))
                    .setFont(font).setFontSize(RECEIPT_FONT_SZ).setMultipliedLeading(1.1f)
                    .setMarginTop(0).setMarginBottom(0);
            if (i > 0) p.setMarginLeft(indentPt);
            div.add(p);
        }
        return div;
    }

    /**
     * Splits "value" into chunks that each fit within (totalWidth - label.length())
     * chars, breaking on the last space that fits rather than mid-word. Shared by
     * wrapLabeledField (ESC/POS) and rWrapped (PDF preview) so the two renderers'
     * wrap points never drift apart — only how each renders the continuation indent differs.
     */
    private List<String> wrapValue(String label, String value, int totalWidth) {
        int valueWidth = Math.max(10, totalWidth - label.length());
        String remaining = value == null ? "" : value;
        List<String> lines = new ArrayList<>();
        while (remaining.length() > valueWidth) {
            int breakAt = remaining.lastIndexOf(' ', valueWidth);
            if (breakAt <= 0) breakAt = valueWidth;
            lines.add(remaining.substring(0, breakAt).trim());
            remaining = remaining.substring(breakAt).trim();
        }
        lines.add(remaining);
        return lines;
    }

    // ── Shared helpers (mirroring InvoicePdfGenerator's conventions) ────────────

    private Paragraph kvRow(String key, String value, PdfFont bold, PdfFont regular) {
        return new Paragraph()
                .add(new Text(key + " ").setFont(bold).setFontSize(8))
                .add(new Text(value).setFont(regular).setFontSize(8))
                .setMarginBottom(1.5f);
    }

    private Cell iCell(String text, PdfFont font, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(8.5f).setTextAlignment(align))
                .setPadding(3.5f);
    }

    private void totRow(Table t, String label, String value, boolean bold, PdfFont boldFont) {
        t.addCell(new Cell()
                .add(new Paragraph(label).setFont(boldFont).setFontSize(9).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setPaddingRight(8).setPaddingTop(3).setPaddingBottom(3));
        t.addCell(new Cell()
                .add(new Paragraph(value).setFont(boldFont).setFontSize(9).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setPaddingRight(3.5f).setPaddingTop(3).setPaddingBottom(3));
    }

    private String dots(int n) { return ".".repeat(n); }

    private String wrapLabeledField(String label, String value, int totalWidth) {
        List<String> lines = wrapValue(label, value, totalWidth);
        String indent = " ".repeat(label.length());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(i == 0 ? label : indent).append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }

    private String pR(String s, int n) { return String.format("%-" + n + "s", s); }
    private String pL(String s, int n) { return String.format("%" + n + "s", s); }
    private String trunc(String s, int n) { return s != null && s.length() > n ? s.substring(0, n) : s != null ? s : ""; }

    private void esc(ByteArrayOutputStream b, int... bytes) {
        byte[] arr = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) arr[i] = (byte) bytes[i];
        try { b.write(arr); } catch (IOException ignored) {}
    }

    private void txt(ByteArrayOutputStream b, String text) {
        try { b.write(text.getBytes(StandardCharsets.ISO_8859_1)); } catch (IOException ignored) {}
    }

    private boolean printLogoEscPos(ByteArrayOutputStream buf, byte[] logoBytes, int targetWidth) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(logoBytes));
            if (src == null) return false;

            int width  = targetWidth;
            int height = Math.max(1, Math.round(src.getHeight() * (width / (float) src.getWidth())));

            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, width, height);
            g.drawImage(src, 0, 0, width, height, null);
            g.dispose();

            int widthBytes = (width + 7) / 8;
            esc(buf, 0x1D, 0x76, 0x30, 0x00,
                    widthBytes & 0xFF, (widthBytes >> 8) & 0xFF,
                    height & 0xFF, (height >> 8) & 0xFF);

            byte[] row = new byte[widthBytes];
            for (int y = 0; y < height; y++) {
                Arrays.fill(row, (byte) 0);
                for (int x = 0; x < width; x++) {
                    int rgb = scaled.getRGB(x, y);
                    int lum = ((rgb >> 16 & 0xFF) * 299 + (rgb >> 8 & 0xFF) * 587 + (rgb & 0xFF) * 114) / 1000;
                    if (lum < 128) row[x / 8] |= (byte) (0x80 >> (x % 8));
                }
                try { buf.write(row); } catch (IOException ignored) {}
            }
            return true;
        } catch (Exception e) {
            log.warn("ESC/POS raster image printing failed: {}", e.toString());
            return false;
        }
    }

    private byte[] fetchLogoBytes(CompanyProfileDto profile) {
        if (profile.logoUrl() == null) return null;
        return companyProfileService.tryGetLogoBytes();
    }

    private String fmtAmount(BigDecimal v) {
        return v == null ? "0.00" : String.format("%,.2f", v);
    }

    private String fmtInstant(Instant instant) {
        return instant == null ? "—" : DATETIME_FMT.format(instant);
    }

    private String primaryAddressLine(Customer c) {
        if (c == null) return null;
        var addrs = c.getAddresses();
        return addrs.isEmpty() ? null : addrs.get(0).getAddressLine();
    }

    private String displayPhone(String phone) {
        if (phone == null || phone.isBlank() || "0000000000".equals(phone.trim())) return "";
        return phone;
    }

    private String repName(User user) {
        if (user == null) return null;
        return user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getUsername();
    }

    private String safe(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
