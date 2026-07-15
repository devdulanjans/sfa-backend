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
import com.itextpdf.layout.element.*;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import com.itextpdf.layout.properties.VerticalAlignment;
import com.sfa.dto.CompanyProfileDto;
import com.sfa.entity.Customer;
import com.sfa.entity.Invoice;
import com.sfa.entity.Order;
import com.sfa.entity.OrderItem;
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
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoicePdfGenerator {

    private final CompanyProfileService companyProfileService;

    private static final DateTimeFormatter DATE_FMT     = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Colombo"));

    // Suffix the ERP appends to every supplier TIN that isn't part of the actual
    // 9-digit registration number — stripped so only the real TIN prints.
    private static final String SUPPLIER_TIN_SUFFIX = "-7000";

    // Conservative print-dot width for the ESC/POS logo raster — comfortably
    // under most thermal printers' physical width (384-576 dots) even though
    // the exact dot width of the target printer isn't known. Kept small so the
    // logo prints compact rather than as a full-width banner.
    private static final int LOGO_WIDTH_PX = 160;

    // Signature images are captured on a ~full-width x 150px mobile canvas but
    // must print small on the invoice — cap both the PDF box and the thermal
    // raster width well below the logo's so they read as a signature, not a banner.
    private static final float PDF_SIGNATURE_MAX_WIDTH  = 130f;
    private static final float PDF_SIGNATURE_MAX_HEIGHT = 36f;
    private static final int SIGNATURE_WIDTH_PX = 180;

    private static final String[] UNITS = {"", "One", "Two", "Three", "Four", "Five", "Six",
            "Seven", "Eight", "Nine", "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen",
            "Fifteen", "Sixteen", "Seventeen", "Eighteen", "Nineteen"};
    private static final String[] TENS  = {"", "", "Twenty", "Thirty", "Forty", "Fifty",
            "Sixty", "Seventy", "Eighty", "Ninety"};

    // ── A4 PDF (Tax Invoice) ─────────────────────────────────────────────────

    public byte[] generate(Invoice invoice, Order order) throws IOException {
        CompanyProfileDto profile  = companyProfileService.get();
        byte[]            logoBytes = fetchLogoBytes(profile);

        // "Tax Invoice" only when VAT was actually charged on this invoice — same rule
        // as the ESC/POS generator's isVatInvoice, kept in sync so both formats agree.
        boolean isVatInvoice = invoice.getTaxTotal() != null
                && invoice.getTaxTotal().compareTo(BigDecimal.ZERO) > 0;
        String invoiceTypeLabel = isVatInvoice ? "TAX INVOICE" : "INVOICE";
        int printCount = invoice.getPrintCount() == null ? 1 : invoice.getPrintCount();
        String copyLabel = printCount <= 1 ? "ORIGINAL" : "COPY " + (printCount - 1);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(new PdfDocument(new PdfWriter(out)), PageSize.A4);
        doc.setMargins(28, 28, 28, 28);

        PdfFont bold    = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD);
        PdfFont regular = PdfFontFactory.createFont(StandardFonts.HELVETICA);
        PdfFont italic  = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE);

        DeviceRgb darkBlue  = new DeviceRgb(0, 71, 131);
        DeviceRgb headerGray = new DeviceRgb(242, 242, 242);
        SolidBorder outer = new SolidBorder(ColorConstants.BLACK, 1.2f);
        SolidBorder div   = new SolidBorder(ColorConstants.BLACK, 0.5f);

        // Outer container — 1-column table; each row = one invoice section
        Table frame = new Table(UnitValue.createPercentArray(new float[]{100f}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        // ── 1. HEADER ────────────────────────────────────────────────────────
        Table hdr = new Table(UnitValue.createPercentArray(new float[]{55, 45}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        Cell brandCell = new Cell().setBorder(Border.NO_BORDER).setBorderRight(div).setPadding(8);
        if (logoBytes != null) {
            try {
                // Logo (left) and company name (right), side by side on one line.
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
                brandCell.add(new Paragraph(profile.companyName()).setFont(bold).setFontSize(32)
                        .setFontColor(darkBlue).setMarginBottom(1));
            }
        } else {
            brandCell.add(new Paragraph(profile.companyName()).setFont(bold).setFontSize(32)
                    .setFontColor(darkBlue).setMarginBottom(1));
        }
        hdr.addCell(brandCell);

        hdr.addCell(new Cell()
                .add(new Paragraph(copyLabel).setFont(bold).setFontSize(8)
                        .setTextAlignment(TextAlignment.RIGHT).setMarginBottom(2))
                .add(new Paragraph(invoiceTypeLabel).setFont(bold).setFontSize(20)
                        .setFontColor(ColorConstants.WHITE)
                        .setBackgroundColor(darkBlue)
                        .setTextAlignment(TextAlignment.RIGHT)
                        .setBorder(new SolidBorder(ColorConstants.BLACK, 1f)).setPadding(4).setMarginBottom(4))
                .add(new Paragraph("E-mail : " + safe(profile.email(), "—"))
                        .setFont(regular).setFontSize(7.5f).setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setPadding(8));

        frame.addCell(new Cell().add(hdr).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderTop(outer)
                .setBorderBottom(div).setPadding(0));

        // ── 2. DATE OF INVOICE ────────────────────────────────────────────────
        frame.addCell(new Cell()
                .add(kvRow("Date Of Invoice :", invoice.getIssuedDate().format(DATE_FMT), bold, regular))
                .setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(8));

        // ── 3. TAX INVOICE NO ─────────────────────────────────────────────────
        Table invNoRow = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);
        invNoRow.addCell(new Cell()
                .add(new Paragraph("Tax Invoice No :").setFont(bold).setFontSize(10))
                .setBorder(Border.NO_BORDER).setPadding(6));
        invNoRow.addCell(new Cell()
                .add(new Paragraph(invoice.getInvoiceNumber()).setFont(bold).setFontSize(13)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setPadding(6));

        frame.addCell(new Cell().add(invNoRow).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(0));

        // ── 4. SUPPLIER | PURCHASER ──────────────────────────────────────────
        String custTin     = safe(invoice.getCustomer().getTaxNumber(), "N/A");
        String custAddress = safe(primaryAddressLine(invoice.getCustomer()), "—");
        String custPhone   = safe(invoice.getCustomer().getPhone(), "—");

        Table parties = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        parties.addCell(new Cell()
                .add(kvRow("Supplier's TIN :",      safe(stripTinSuffix(profile.taxId()), "—"), bold, regular))
                .add(kvRow("Supplier's Name :",     profile.companyName(),                  bold, regular))
                .add(kvRow("Reg. Address :",        safe(profile.registeredAddress(), "—"), bold, regular))
                .add(kvRow("Operating Address :",   safe(profile.operatingAddress(), "—"),  bold, regular))
                .add(kvRow("Contact No / Fax No :", safe(profile.phone(), "—"),             bold, regular))
                .add(kvRow("Place of Supply :",     dots(25),                              bold, regular))
                .setBorder(Border.NO_BORDER).setBorderRight(div).setPadding(8));

        parties.addCell(new Cell()
                .add(kvRow("Purchaser's TIN :",  custTin,                           bold, regular))
                .add(kvRow("Purchaser's Name :", invoice.getCustomer().getName(),   bold, regular))
                .add(kvRow("Address :",          custAddress,                       bold, regular))
                .add(kvRow("Contact No :",       custPhone,                         bold, regular))
                .setBorder(Border.NO_BORDER).setPadding(8));

        frame.addCell(new Cell().add(parties).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(0));

        // ── 5. PURCHASE ORDER NO ──────────────────────────────────────────────
        frame.addCell(new Cell()
                .add(kvRow("Purchase Order No :", dots(20), bold, regular))
                .add(kvRow("Date :",               dots(20), bold, regular))
                .setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(8));

        // ── 6. DELIVERY DATE ──────────────────────────────────────────────────
        Table meta = new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        meta.addCell(metaBox("Delivery Date", order.getOrderDate() != null
                ? fmtInstant(order.getOrderDate()) : "—", bold, regular, false));

        frame.addCell(new Cell().add(meta).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(0));

        // ── 7. ITEMS TABLE ───────────────────────────────────────────────────
        Table items = new Table(UnitValue.createPercentArray(new float[]{5, 47, 10, 19, 19}))
                .setWidth(UnitValue.createPercentValue(100))
                .setBorder(Border.NO_BORDER);

        String[] hdrs   = {"No.", "Description of Goods", "Qty", "Unit Price\n(Rs.)", "Amount Excl.\nVAT (Rs.)"};
        TextAlignment[] aligns = {TextAlignment.CENTER, TextAlignment.LEFT, TextAlignment.CENTER,
                TextAlignment.RIGHT, TextAlignment.RIGHT};
        for (int i = 0; i < hdrs.length; i++) {
            items.addHeaderCell(new Cell()
                    .add(new Paragraph(hdrs[i]).setFont(bold).setFontSize(8)
                            .setTextAlignment(aligns[i]))
                    .setBackgroundColor(headerGray).setPadding(4));
        }

        int no = 1;
        for (OrderItem item : order.getItems()) {
            boolean isFree = "FREE_PRODUCT".equals(item.getPriceSource());
            // A free line's discount always fully offsets its price, so lineTotal/taxAmount
            // net to zero by design — printing that would show "0.00" for the Amount column
            // regardless of the item's real value. Print the retail value (price x qty)
            // instead: the invoice totals below are unaffected since they come from the
            // invoice's own subtotal/tax/total fields, not from summing these printed rows.
            BigDecimal displayUnitPrice = (isFree && item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0)
                    ? item.getProduct().getDefaultPrice()
                    : item.getUnitPrice();
            BigDecimal amountExclVat = isFree
                    ? displayUnitPrice.multiply(item.getQuantity())
                    : item.getLineTotal().subtract(item.getTaxAmount());
            String description = item.getProduct().getName()
                    + (isFree && item.getPromotionName() != null
                        ? " (Free — " + item.getPromotionName() + ")" : "");
            items.addCell(iCell(String.valueOf(no++),                  regular, TextAlignment.CENTER));
            items.addCell(iCell(description,                           regular, TextAlignment.LEFT));
            items.addCell(iCell(item.getQuantity().toPlainString(),    regular, TextAlignment.CENTER));
            items.addCell(iCell(fmtAmount(displayUnitPrice),           regular, TextAlignment.RIGHT));
            items.addCell(iCell(fmtAmount(amountExclVat),              regular, TextAlignment.RIGHT));
        }
        int blanks = Math.max(0, 5 - order.getItems().size());
        for (int i = 0; i < blanks; i++) {
            for (int j = 0; j < 5; j++)
                items.addCell(new Cell().add(new Paragraph(" ").setFontSize(9)).setHeight(18).setPadding(3));
        }

        frame.addCell(new Cell().add(items).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(0));

        // ── 8. TOTALS ────────────────────────────────────────────────────────
        Table totals = new Table(UnitValue.createPercentArray(new float[]{62, 38}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        BigDecimal discountTotalPdf = invoice.getDiscountTotal() != null ? invoice.getDiscountTotal() : BigDecimal.ZERO;
        boolean hasDiscountPdf = discountTotalPdf.compareTo(BigDecimal.ZERO) > 0;

        totRow(totals, "Total Value of Supply",
                fmtAmount(invoice.getSubtotal()), false, bold, regular);
        if (hasDiscountPdf) {
            totRow(totals, "Discount",
                    "(" + fmtAmount(discountTotalPdf) + ")", false, bold, regular);
            // Amount VAT is actually calculated on — makes explicit that VAT applies
            // after the discount, not on the pre-discount Total Value of Supply.
            totRow(totals, "Net Amount",
                    fmtAmount(invoice.getSubtotal().subtract(discountTotalPdf)), false, bold, regular);
        }
        totRow(totals, "VAT Amount (" + (hasDiscountPdf ? "Net Amount" : "Total Value of Supply")
                        + " @ " + formatRate(effectiveTaxPct(invoice)) + "%)",
                fmtAmount(invoice.getTaxTotal()), false, bold, regular);
        totRow(totals, "Total Amount Including VAT",
                fmtAmount(invoice.getTotal()), true, bold, bold);

        frame.addCell(new Cell().add(totals).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(0));

        // ── 9. AMOUNT IN WORDS ───────────────────────────────────────────────
        frame.addCell(new Cell()
                .add(new Paragraph()
                        .add(new Text("Total Amount in Words :  ").setFont(bold).setFontSize(8.5f))
                        .add(new Text(amountInWords(invoice.getTotal())).setFont(italic).setFontSize(8.5f)))
                .setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div)
                .setPadding(8));

        // ── 10/11. MODE OF PAYMENT (above) / PAYMENT INSTRUCTIONS | PAYMENT & BANK ──
        Table paymentRow = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        paymentRow.addCell(new Cell()
                .add(kvRow("Mode of Payment :", dots(30), bold, regular).setMarginBottom(6))
                .add(new Paragraph("Payment Instructions:").setFont(bold).setFontSize(8))
                .add(new Paragraph("Cheque should be drawn in favor of")
                        .setFont(regular).setFontSize(8))
                .add(new Paragraph("\"" + profile.companyName() + "\"")
                        .setFont(bold).setFontSize(8))
                .setBorder(Border.NO_BORDER).setBorderRight(div).setPadding(8));

        paymentRow.addCell(new Cell()
                .add(new Paragraph("Payment :  ☐ Cash    ☐ Cheques    ☐ Online")
                        .setFont(regular).setFontSize(8.5f).setMarginBottom(4))
                .add(kvRow("Account Name:", safe(profile.bankAccountName(), "—"), bold, regular))
                .add(kvRow("Account No.:",  safe(profile.bankAccountNumber(), "—"), bold, regular))
                .add(kvRow("Bank Name :",   safe(profile.bankName(), "—"), bold, regular))
                .add(kvRow("Branch Name :", safe(profile.bankBranch(), "—"), bold, regular))
                .setBorder(Border.NO_BORDER).setPadding(8));

        frame.addCell(new Cell().add(paymentRow).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(0));

        // ── 12. SIGNATURES — Customer first (left), Sales Rep second (right),
        //       matching the paper template's order ─────────────────────────
        Table sigs = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        Cell custSigCell = new Cell().setBorder(Border.NO_BORDER).setBorderRight(div).setPadding(8);
        custSigCell.add(new Paragraph("Received the goods in good condition")
                .setFont(bold).setFontSize(8.5f).setMarginBottom(6));
        addSignatureBlock(custSigCell, order.getCustomerSignature(), regular);
        custSigCell.add(new Paragraph("Customer Name / NIC No. & Sign")
                .setFont(regular).setFontSize(7).setFontColor(ColorConstants.GRAY));
        sigs.addCell(custSigCell);

        Cell repSigCell = new Cell().setBorder(Border.NO_BORDER).setPadding(8);
        addSignatureBlock(repSigCell, order.getSalespersonSignature(), regular);
        repSigCell.add(new Paragraph("Sales Rep Signature")
                .setFont(regular).setFontSize(7).setFontColor(ColorConstants.GRAY));
        sigs.addCell(repSigCell);

        frame.addCell(new Cell().add(sigs).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(outer).setPadding(0));

        doc.add(frame);
        doc.close();
        return out.toByteArray();
    }

    // ── ESC/POS (thermal, 64-char safe width) ─────────────────────────────────
    // NOTE: 72 chars/line wrapped on the actual physical printer (confirmed by
    // a real test print — addresses, divider lines and the item row all broke
    // onto a second line around column ~70). 64 leaves a safe margin.

    public byte[] generateEscPos(Invoice invoice, Order order) {
        CompanyProfileDto profile = companyProfileService.get();

        final int W = 64;
        final int ADDR_WIDTH = 43; // label is ~21 chars; keeps label+value on one line within W
        ByteArrayOutputStream buf = new ByteArrayOutputStream();

        // "Tax Invoice" only when VAT was actually charged on this invoice —
        // i.e. the customer is VAT-registered (has a tax number) and isn't
        // EXEMPT/ZERO_RATED. Checking the actual tax total (rather than just
        // tax-number presence) keeps this correct even if a customer has a
        // tax number on file but is still marked exempt.
        boolean isVatInvoice = invoice.getTaxTotal() != null
                && invoice.getTaxTotal().compareTo(BigDecimal.ZERO) > 0;
        String invoiceNoLabel = isVatInvoice ? "Tax Invoice No :" : "Invoice No :";

        // ── Header — hardware center-alignment only (no manual padding: doing
        // both drifts the text off true center) ─────────────────────────────
        esc(buf, 0x1B, 0x40);
        esc(buf, 0x1B, 0x61, 0x01);

        byte[] logoBytes = fetchLogoBytes(profile);
        if (logoBytes != null) {
            printLogoEscPos(buf, logoBytes, LOGO_WIDTH_PX);
            txt(buf, "\n");
        }

        // Double-HEIGHT only (not double-width) — double-width halves the
        // characters-per-line budget and was why the company name wrapped to
        // a second line on longer names.
        esc(buf, 0x1B, 0x21, 0x18); // double-height + bold
        txt(buf, trunc(profile.companyName(), W) + "\n");
        esc(buf, 0x1B, 0x21, 0x00);
        txt(buf, "\n");
        txt(buf, "E-mail : " + safe(profile.email(), "—") + "\n");
        txt(buf, "\n");

        // ORIGINAL / COPY label — printCount is already incremented before this runs
        int pc = invoice.getPrintCount() == null ? 1 : invoice.getPrintCount();
        String copyLabel = pc <= 1 ? "**  ORIGINAL  **" : "**  COPY " + (pc - 1) + "  **";
        txt(buf, copyLabel + "\n");

        // TAX INVOICE / INVOICE banner, right after Original/Copy — reverse video
        // (white-on-black) + double-height + bold to stand out like a highlighted
        // box, mirroring the PDF header's highlighted "TAX INVOICE" box.
        String invoiceTypeLabel = isVatInvoice ? "TAX INVOICE" : "INVOICE";
        esc(buf, 0x1D, 0x42, 0x01); // reverse video on
        esc(buf, 0x1B, 0x21, 0x18); // double-height + bold
        txt(buf, invoiceTypeLabel + "\n");
        esc(buf, 0x1B, 0x21, 0x00); // reset text size/weight
        esc(buf, 0x1D, 0x42, 0x00); // reverse video off
        txt(buf, "\n");

        esc(buf, 0x1B, 0x61, 0x00); // left alignment for everything from here on
        txt(buf, "=".repeat(W) + "\n\n");

        // ── Date of Invoice ───────────────────────────────────────────────────
        txt(buf, "Date Of Invoice : " + fmtInstant(invoice.getCreatedAt()) + "\n");
        txt(buf, "=".repeat(W) + "\n\n");

        // ── Tax Invoice No / Invoice No — label depends on whether the
        // customer has a TIN; the number itself is bigger + bold ────────────
        esc(buf, 0x1B, 0x21, 0x00);
        txt(buf, invoiceNoLabel + " ");
        esc(buf, 0x1B, 0x21, 0x18); // double-height + bold
        txt(buf, invoice.getInvoiceNumber() + "\n");
        esc(buf, 0x1B, 0x21, 0x00);
        txt(buf, "=".repeat(W) + "\n\n");

        // ── Supplier block ────────────────────────────────────────────────────
        txt(buf, "Supplier's TIN    : " + safe(stripTinSuffix(profile.taxId()), "N/A") + "\n");
        txt(buf, "Supplier's Name   : " + trunc(profile.companyName(), ADDR_WIDTH) + "\n");
        txt(buf, wrapLabeledField("Reg. Address      : ", safe(profile.registeredAddress(), "—"), W));
        txt(buf, wrapLabeledField("Operating Address : ", safe(profile.operatingAddress(), "—"), W));
        txt(buf, "Contact No/Fax No : " + safe(profile.phone(), "—") + "\n");
        txt(buf, "Place of Supply   : " + dots(20) + "\n");
        txt(buf, "-".repeat(W) + "\n\n");

        // ── Purchaser block ───────────────────────────────────────────────────
        txt(buf, "Purchaser's TIN   : " + safe(invoice.getCustomer().getTaxNumber(), "N/A") + "\n");
        txt(buf, "Purchaser's Name  : " + trunc(invoice.getCustomer().getName(), ADDR_WIDTH) + "\n");
        txt(buf, "Address           : " + trunc(safe(primaryAddressLine(invoice.getCustomer()), "—"), ADDR_WIDTH) + "\n");
        txt(buf, "Contact No        : " + safe(invoice.getCustomer().getPhone(), "—") + "\n");
        txt(buf, "=".repeat(W) + "\n\n");

        // ── Purchase Order No (blank, filled by hand) ────────────────────────
        txt(buf, "Purchase Order No : " + dots(15) + " Date : " + dots(8) + "\n");
        txt(buf, "=".repeat(W) + "\n\n");

        // ── Delivery date ─────────────────────────────────────────────────────
        txt(buf, "Delivery Date     : " + (order.getOrderDate() != null ? fmtInstant(order.getOrderDate()) : "—") + "\n");
        txt(buf, "=".repeat(W) + "\n\n");

        // ── Items — must never wrap to a second line ─────────────────────────
        // No(2) + " " + Name(24) + " " + Qty(6) + " " + UnitPr(11) + " " + Amt(11) = 58 (<= 64)
        txt(buf, pR("No", 2) + " " + pR("Description", 24) + " " + pL("Qty", 6) + " " + pL("Price", 11) + " " + pL("Amount", 11) + "\n");
        txt(buf, "-".repeat(W) + "\n");

        int no = 1;
        for (OrderItem item : order.getItems()) {
            boolean isFree = "FREE_PRODUCT".equals(item.getPriceSource());
            String name = trunc(item.getProduct().getName() + (isFree ? " (FREE)" : ""), 24);
            String qty  = trunc(item.getQuantity().toPlainString(), 6);
            // See the PDF item loop above for why free lines print price x qty here
            // instead of the (always-zero) discounted lineTotal/taxAmount.
            BigDecimal displayUnitPrice = (isFree && item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0)
                    ? item.getProduct().getDefaultPrice()
                    : item.getUnitPrice();
            BigDecimal amountExclVat = isFree
                    ? displayUnitPrice.multiply(item.getQuantity())
                    : item.getLineTotal().subtract(item.getTaxAmount());
            String up   = fmtAmount(displayUnitPrice);
            String amt  = fmtAmount(amountExclVat);
            txt(buf, pR(String.valueOf(no++), 2) + " " + pR(name, 24) + " " + pL(qty, 6) + " " + pL(up, 11) + " " + pL(amt, 11) + "\n");
            if (isFree && item.getPromotionName() != null) {
                txt(buf, "   " + trunc(item.getPromotionName(), W - 3) + "\n");
            } else {
                txt(buf, "\n"); // space between line items
            }
        }
        txt(buf, "=".repeat(W) + "\n\n");

        // ── Totals ────────────────────────────────────────────────────────────
        int lw = 46, rw = W - lw;
        BigDecimal discountTotalEsc = invoice.getDiscountTotal() != null ? invoice.getDiscountTotal() : BigDecimal.ZERO;
        boolean hasDiscountEsc = discountTotalEsc.compareTo(BigDecimal.ZERO) > 0;

        txt(buf, pR("Total Value of Supply", lw) + pL(fmtAmount(invoice.getSubtotal()), rw) + "\n");
        if (hasDiscountEsc) {
            txt(buf, pR("Discount", lw) + pL("(" + fmtAmount(discountTotalEsc) + ")", rw) + "\n");
            // Amount VAT is actually calculated on — makes explicit that VAT applies
            // after the discount, not on the pre-discount Total Value of Supply.
            txt(buf, pR("Net Amount", lw) + pL(fmtAmount(invoice.getSubtotal().subtract(discountTotalEsc)), rw) + "\n");
        }
        txt(buf, pR("VAT Amount (@ " + formatRate(effectiveTaxPct(invoice)) + "%)", lw) + pL(fmtAmount(invoice.getTaxTotal()),  rw) + "\n");
        esc(buf, 0x1B, 0x21, 0x08);
        txt(buf, pR("Total Amount Including VAT", lw) + pL(fmtAmount(invoice.getTotal()), rw) + "\n");
        esc(buf, 0x1B, 0x21, 0x00);
        txt(buf, "=".repeat(W) + "\n\n");

        txt(buf, wrapLabeledField("Total Amount in Words : ", amountInWords(invoice.getTotal()), W));
        txt(buf, "=".repeat(W) + "\n\n");

        // ── Mode of payment (above Payment Instructions) / bank details ──────
        txt(buf, "Mode of Payment : " + dots(20) + "\n\n");
        txt(buf, "Payment Instructions:\n");
        txt(buf, "Cheque should be drawn in favor of\n");
        txt(buf, "\"" + trunc(profile.companyName(), W - 2) + "\"\n\n");

        txt(buf, "Payment      : [ ] Cash   [ ] Chq   [ ] Online\n");
        txt(buf, "Account Name : " + trunc(safe(profile.bankAccountName(), "—"), 45) + "\n");
        txt(buf, "Account No.  : " + trunc(safe(profile.bankAccountNumber(), "—"), 45) + "\n");
        txt(buf, "Bank Name    : " + trunc(safe(profile.bankName(), "—"), 45) + "\n");
        txt(buf, "Branch Name  : " + trunc(safe(profile.bankBranch(), "—"), 45) + "\n");
        txt(buf, "-".repeat(W) + "\n\n");

        // ── Signatures — Customer first, then Sales Rep (matches template) ───
        txt(buf, "Received the goods in good condition\n\n");
        printSignatureEscPos(buf, order.getCustomerSignature(), SIGNATURE_WIDTH_PX);
        txt(buf, "Customer Name / NIC No. & Sign\n\n");
        printSignatureEscPos(buf, order.getSalespersonSignature(), SIGNATURE_WIDTH_PX);
        txt(buf, "Sales Rep Signature\n");

        // Feed + partial cut
        txt(buf, "\n\n\n\n");
        esc(buf, 0x1D, 0x56, 0x42, 0x03);
        return buf.toByteArray();
    }

    // ── PDF cell helpers ──────────────────────────────────────────────────────

    private Paragraph kvRow(String key, String value, PdfFont bold, PdfFont regular) {
        return new Paragraph()
                .add(new Text(key + " ").setFont(bold).setFontSize(8))
                .add(new Text(value).setFont(regular).setFontSize(8))
                .setMarginBottom(1.5f);
    }

    private Cell metaBox(String label, String value, PdfFont bold, PdfFont regular, boolean rightBorder) {
        Cell c = new Cell()
                .add(new Paragraph(label).setFont(bold).setFontSize(7)
                        .setFontColor(ColorConstants.GRAY).setMarginBottom(2))
                .add(new Paragraph(value).setFont(regular).setFontSize(8.5f))
                .setBorder(Border.NO_BORDER).setPadding(6);
        if (rightBorder) c.setBorderRight(new SolidBorder(0.5f));
        return c;
    }

    /**
     * Blank dotted placeholder for fields the paper template leaves for the
     * sales rep to fill in by hand (Place of Supply, Purchase Order No)
     * — this system doesn't collect that data digitally.
     */
    private String dots(int n) {
        return ".".repeat(n);
    }

    /**
     * Strips the trailing "-7000" suffix the ERP appends to the supplier's TIN
     * so only the real 9-digit registration number prints on the invoice.
     */
    private String stripTinSuffix(String tin) {
        if (tin == null) return null;
        String t = tin.trim();
        return t.endsWith(SUPPLIER_TIN_SUFFIX)
                ? t.substring(0, t.length() - SUPPLIER_TIN_SUFFIX.length())
                : t;
    }

    /**
     * Word-wraps "label + value" into one or more lines that each fit within
     * totalWidth chars, continuation lines indented to align under the value
     * (not the label) — used so long addresses/amounts-in-words never run past
     * the thermal printer's line width instead of being hard-truncated.
     */
    private String wrapLabeledField(String label, String value, int totalWidth) {
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

        String indent = " ".repeat(label.length());
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            sb.append(i == 0 ? label : indent).append(lines.get(i)).append("\n");
        }
        return sb.toString();
    }

    /**
     * Adds the customer/sales-rep signature to a PDF signature cell — a captured
     * PNG (base64) scaled proportionally to fit within a small box, or the
     * original blank hand-sign line when no signature was captured/decodable.
     */
    private void addSignatureBlock(Cell cell, String base64Signature, PdfFont regular) {
        if (base64Signature != null && !base64Signature.isBlank()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64Signature);
                ImageData data = ImageDataFactory.create(bytes);
                float scale = Math.min(PDF_SIGNATURE_MAX_WIDTH / data.getWidth(),
                        PDF_SIGNATURE_MAX_HEIGHT / data.getHeight());
                Image img = new Image(data)
                        .setWidth(data.getWidth() * scale)
                        .setHeight(data.getHeight() * scale)
                        .setMarginBottom(2);
                cell.add(img);
                return;
            } catch (Exception e) {
                log.warn("Could not decode/render signature image for PDF ({} bytes base64): {}",
                        base64Signature.length(), e.toString());
                // fall through to the blank hand-sign line
            }
        } else {
            log.debug("No signature captured for this order — printing blank hand-sign line");
        }
        cell.add(new Paragraph("\n\n"));
        cell.add(new Paragraph("________________________").setFont(regular).setFontSize(9));
    }

    private Cell iCell(String text, PdfFont font, TextAlignment align) {
        return new Cell()
                .add(new Paragraph(text).setFont(font).setFontSize(8.5f).setTextAlignment(align))
                .setPadding(3.5f);
    }

    private void totRow(Table t, String label, String value, boolean bold,
                        PdfFont boldFont, PdfFont regularFont) {
        PdfFont lf = bold ? boldFont : regularFont;
        PdfFont vf = boldFont;
        t.addCell(new Cell()
                .add(new Paragraph(label).setFont(lf).setFontSize(8.5f)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setPaddingRight(8).setPaddingTop(3).setPaddingBottom(3));
        t.addCell(new Cell()
                .add(new Paragraph(value).setFont(vf).setFontSize(8.5f)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setPaddingRight(8).setPaddingTop(3).setPaddingBottom(3));
    }

    // ── Amount in words ───────────────────────────────────────────────────────

    private String amountInWords(BigDecimal amount) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) return "Zero Rupees Only";
        long rupees = amount.toBigInteger().longValue();
        int cents   = amount.subtract(BigDecimal.valueOf(rupees))
                .multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.HALF_UP).intValue();
        StringBuilder sb = new StringBuilder("Sri Lanka Rupees ").append(toWords(rupees));
        if (cents > 0) sb.append(" and ").append(toWords(cents)).append(" Cents");
        return sb.append(" Only").toString();
    }

    private String toWords(long n) {
        if (n == 0) return "Zero";
        if (n < 20)  return UNITS[(int) n];
        if (n < 100) return TENS[(int)(n / 10)] + (n % 10 != 0 ? " " + UNITS[(int)(n % 10)] : "");
        if (n < 1_000)
            return UNITS[(int)(n / 100)] + " Hundred" + (n % 100 != 0 ? " " + toWords(n % 100) : "");
        if (n < 1_000_000)
            return toWords(n / 1_000) + " Thousand" + (n % 1_000 != 0 ? " " + toWords(n % 1_000) : "");
        if (n < 1_000_000_000)
            return toWords(n / 1_000_000) + " Million" + (n % 1_000_000 != 0 ? " " + toWords(n % 1_000_000) : "");
        return toWords(n / 1_000_000_000) + " Billion"
                + (n % 1_000_000_000 != 0 ? " " + toWords(n % 1_000_000_000) : "");
    }

    // ── ESC/POS helpers ───────────────────────────────────────────────────────

    private String pR(String s, int n) { return String.format("%-" + n + "s", s); }
    private String pL(String s, int n)  { return String.format("%" + n + "s", s); }
    private String trunc(String s, int n) { return s != null && s.length() > n ? s.substring(0, n) : s != null ? s : ""; }

    private void esc(ByteArrayOutputStream b, int... bytes) {
        byte[] arr = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) arr[i] = (byte) bytes[i];
        try { b.write(arr); } catch (IOException ignored) {}
    }

    private void txt(ByteArrayOutputStream b, String text) {
        try { b.write(text.getBytes(StandardCharsets.ISO_8859_1)); } catch (IOException ignored) {}
    }

    /**
     * Rasterizes an image (logo or signature) and emits it via the ESC/POS "GS v 0"
     * raster bit image command. Scales to [targetWidth] dots (preserving
     * aspect ratio) and thresholds to 1-bit black/white — thermal printers
     * have no grayscale, so a simple luminance threshold is used rather than
     * dithering. Never throws: a broken/unreadable image just prints nothing
     * rather than failing the whole invoice — returns false so callers with a
     * text fallback (e.g. the signature blank line) know to use it.
     */
    private boolean printLogoEscPos(ByteArrayOutputStream buf, byte[] logoBytes, int targetWidth) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(logoBytes));
            if (src == null) {
                log.warn("ESC/POS raster decode returned null — unsupported/corrupt image bytes ({} bytes)",
                        logoBytes.length);
                return false;
            }

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

    /**
     * Prints the customer/sales-rep signature as a small ESC/POS raster image
     * (reusing the logo's decode/scale/threshold pipeline), or the original
     * dotted blank line when no signature was captured/decodable.
     */
    private void printSignatureEscPos(ByteArrayOutputStream buf, String base64Signature, int targetWidth) {
        if (base64Signature != null && !base64Signature.isBlank()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64Signature);
                if (printLogoEscPos(buf, bytes, targetWidth)) return;
                log.warn("Signature raster printing failed — falling back to blank dotted line");
            } catch (Exception e) {
                log.warn("Could not decode signature base64 for ESC/POS printing ({} bytes): {}",
                        base64Signature.length(), e.toString());
            }
        } else {
            log.debug("No signature captured for this order — printing blank dotted line (ESC/POS)");
        }
        txt(buf, dots(45) + "\n");
    }

    // ── Common helpers ────────────────────────────────────────────────────────

    private byte[] fetchLogoBytes(CompanyProfileDto profile) {
        if (profile.logoUrl() == null) return null;
        try {
            return companyProfileService.getLogoBytes();
        } catch (Exception e) {
            return null; // fall back to a text-only header rather than fail the whole invoice
        }
    }

    private String formatRate(BigDecimal rate) {
        if (rate == null) return "0";
        return rate.stripTrailingZeros().toPlainString();
    }

    /**
     * The rate actually applied to this invoice, derived from its own amounts rather than
     * the unrelated global company-profile VAT rate — so the printed "@ X%" label is always
     * self-consistent with the printed tax amount, regardless of which customer/rate produced
     * it (tax is now resolved per-customer, see PricingEngine.resolveTaxPct).
     */
    private BigDecimal effectiveTaxPct(Invoice invoice) {
        BigDecimal subtotal = invoice.getSubtotal() != null ? invoice.getSubtotal() : BigDecimal.ZERO;
        BigDecimal discount = invoice.getDiscountTotal() != null ? invoice.getDiscountTotal() : BigDecimal.ZERO;
        BigDecimal taxTotal = invoice.getTaxTotal() != null ? invoice.getTaxTotal() : BigDecimal.ZERO;
        BigDecimal taxableBase = subtotal.subtract(discount);
        return taxableBase.compareTo(BigDecimal.ZERO) > 0
                ? taxTotal.divide(taxableBase, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
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

    private String safe(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
