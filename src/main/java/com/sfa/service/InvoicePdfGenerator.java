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
    // Caps the raster height regardless of the captured signature's aspect ratio —
    // without this, a signature cropped tight to a tall/narrow stroke box would
    // scale to targetWidth and print at whatever height that implies, sometimes
    // filling much more of the receipt than a signature should. Matches the PDF
    // signature's ~3.6:1 width:height ratio (PDF_SIGNATURE_MAX_WIDTH/_MAX_HEIGHT)
    // so both outputs read the same size.
    private static final int SIGNATURE_MAX_HEIGHT_PX = 50;

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
        // Billed name/address is the head office's when the customer is a branch
        // (has a parentCustomer) — TIN/phone/Place of Supply stay the branch's own,
        // since those legitimately identify this specific branch/outlet. Place of
        // Supply sits with the purchaser (after Contact No), not the supplier —
        // it identifies which branch/outlet this invoice is for, so it belongs
        // next to the customer's own contact info. Placeholder "0000000000" phone
        // numbers from bulk-imported data print as blank rather than the literal digits.
        Customer billingParty = billingParty(invoice.getCustomer());
        String custTin     = safe(invoice.getCustomer().getTaxNumber(), "N/A");
        String custAddress = safe(primaryAddressLine(billingParty), "—");
        String custPhone   = displayPhone(invoice.getCustomer().getPhone());

        Table parties = new Table(UnitValue.createPercentArray(new float[]{50, 50}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        parties.addCell(new Cell()
                .add(kvRow("Supplier's TIN :",      safe(stripTinSuffix(profile.taxId()), "—"), bold, regular))
                .add(kvRow("Supplier's Name :",     profile.companyName(),                  bold, regular))
                .add(kvRow("Reg. Address :",        safe(profile.registeredAddress(), "—"), bold, regular))
                .add(kvRow("Operating Address :",   safe(profile.operatingAddress(), "—"),  bold, regular))
                .add(kvRow("Contact No / Fax No :", safe(profile.phone(), "—"),             bold, regular))
                .setBorder(Border.NO_BORDER).setBorderRight(div).setPadding(8));

        parties.addCell(new Cell()
                .add(kvRow("Purchaser's TIN :",  custTin,                bold, regular))
                .add(kvRow("Purchaser's Name :", billingParty.getName(), bold, regular))
                .add(kvRow("Address :",          custAddress,            bold, regular))
                .add(kvRow("Contact No :",       custPhone,              bold, regular))
                .add(kvRow("Place of Supply :",  safe(invoice.getCustomer().getPlaceOfSupplier(), dots(25)), bold, regular))
                .setBorder(Border.NO_BORDER).setPadding(8));

        frame.addCell(new Cell().add(parties).setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(0));

        // ── 5. PURCHASE ORDER NO ──────────────────────────────────────────────
        frame.addCell(new Cell()
                .add(kvRow("Purchase Order No :", safe(order.getPoNumber(), dots(20)), bold, regular))
                .add(kvRow("Date :",               dots(20), bold, regular))
                .setBorder(Border.NO_BORDER)
                .setBorderLeft(outer).setBorderRight(outer).setBorderBottom(div).setPadding(8));

        // ── 6. DATE OF SUPPLY ────────────────────────────────────────────────
        Table meta = new Table(UnitValue.createPercentArray(new float[]{100}))
                .setWidth(UnitValue.createPercentValue(100)).setBorder(Border.NO_BORDER);

        meta.addCell(metaBox("Date of Supply", order.getOrderDate() != null
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
        String repName = repName(order);
        if (repName != null) {
            repSigCell.add(new Paragraph(repName).setFont(bold).setFontSize(8).setMarginTop(1));
        }
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

        // TAX INVOICE / INVOICE banner, right after Original/Copy — double-height
        // + bold (no reverse video) so it stands out without a black background.
        String invoiceTypeLabel = isVatInvoice ? "TAX INVOICE" : "INVOICE";
        esc(buf, 0x1B, 0x21, 0x18); // double-height + bold
        txt(buf, invoiceTypeLabel + "\n");
        esc(buf, 0x1B, 0x21, 0x00); // reset text size/weight
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
        txt(buf, "-".repeat(W) + "\n\n");

        // ── Purchaser block ───────────────────────────────────────────────────
        // Billed name/address is the head office's when the customer is a branch —
        // see the matching comment in generate() above for why TIN/Place of Supply
        // are deliberately left as the branch's own. Place of Supply sits after
        // Contact No here (not with the supplier) since it identifies which
        // branch/outlet this invoice is for. Placeholder "0000000000" phone
        // numbers print as blank rather than the literal digits.
        Customer billingParty = billingParty(invoice.getCustomer());
        txt(buf, "Purchaser's TIN   : " + safe(invoice.getCustomer().getTaxNumber(), "N/A") + "\n");
        txt(buf, "Purchaser's Name  : " + trunc(billingParty.getName(), ADDR_WIDTH) + "\n");
        txt(buf, wrapLabeledField("Address           : ", safe(primaryAddressLine(billingParty), "—"), W));
        txt(buf, "Contact No        : " + displayPhone(invoice.getCustomer().getPhone()) + "\n");
        txt(buf, "Place of Supply   : " + safe(invoice.getCustomer().getPlaceOfSupplier(), dots(20)) + "\n");
        txt(buf, "=".repeat(W) + "\n\n");

        // ── Purchase Order No — printed when the rep entered one, else blank dots ──
        txt(buf, "Purchase Order No : " + safe(order.getPoNumber(), dots(15)) + " Date : " + dots(8) + "\n");
        txt(buf, "=".repeat(W) + "\n\n");

        // ── Date of supply ────────────────────────────────────────────────────
        txt(buf, "Date of Supply    : " + (order.getOrderDate() != null ? fmtInstant(order.getOrderDate()) : "—") + "\n");
        txt(buf, "=".repeat(W) + "\n\n");

        // ── Items — must never wrap to a second line ─────────────────────────
        // No(2) + " " + Name(24) + " " + Qty(6) + " " + UnitPr(11) + " " + Amt(17) = 64 (== W),
        // so the Amount column's right edge lines up exactly with the totals section below
        // (lw=46/rw=18 there also sums to 64) instead of ending 6 chars short of it.
        txt(buf, pR("No", 2) + " " + pR("Description", 24) + " " + pL("Qty", 6) + " " + pL("Price", 11) + " " + pL("Amount", 17) + "\n");
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
            txt(buf, pR(String.valueOf(no++), 2) + " " + pR(name, 24) + " " + pL(qty, 6) + " " + pL(up, 11) + " " + pL(amt, 17) + "\n");
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
        String repNameEsc = repName(order);
        if (repNameEsc != null) {
            esc(buf, 0x1B, 0x45, 0x01); // bold on
            txt(buf, trunc(repNameEsc, W) + "\n");
            esc(buf, 0x1B, 0x45, 0x00); // bold off
        }

        // Feed + partial cut
        txt(buf, "\n\n\n\n");
        esc(buf, 0x1D, 0x56, 0x42, 0x03);
        return buf.toByteArray();
    }

    // ── TEMPORARY — dev/QA aid, remove before production ────────────────────
    // Thermal receipt preview (narrow PDF mirroring generateEscPos's exact
    // content/layout) — lets the mobile app show what will actually print on
    // the Bluetooth thermal printer, instead of the unrelated A4 tax-invoice
    // design. Reuses the same line content/order/padding (pR/pL/trunc/
    // wrapLabeledField/amountInWords/billingParty) as generateEscPos() so the
    // two can't silently drift apart — only the renderer differs (monospace
    // PDF text instead of raw ESC/POS bytes).
    // Remove this whole block (through addReceiptSignature below) plus the
    // matching TEMPORARY markers in InvoiceController/InvoiceService and
    // sfa-mobile's InvoicePrintPreviewScreen before shipping to production.

    // Courier is exactly 0.6em per character, so 64 chars at RECEIPT_FONT_SZ need
    // 64 * 0.6 * RECEIPT_FONT_SZ = 249.6pt of content width. RECEIPT_WIDTH must leave
    // at least that much after margins — the "=".repeat(64)/"-".repeat(64) divider
    // lines have no spaces to wrap on, so an overly-tight width here isn't just a
    // cosmetic overflow, it's unlayoutable and throws at PDF-generation time.
    private static final float RECEIPT_WIDTH   = 280f; // ~99mm — narrow "receipt" page
    private static final float RECEIPT_MARGIN  = 8f;
    private static final float RECEIPT_FONT_SZ = 6.5f; // fits the same 64-char width as generateEscPos's W

    public byte[] generateThermalPreview(Invoice invoice, Order order) throws IOException {
        CompanyProfileDto profile = companyProfileService.get();
        final int W = 64;

        boolean isVatInvoice = invoice.getTaxTotal() != null
                && invoice.getTaxTotal().compareTo(BigDecimal.ZERO) > 0;
        String invoiceNoLabel = isVatInvoice ? "Tax Invoice No :" : "Invoice No :";
        int pc = invoice.getPrintCount() == null ? 1 : invoice.getPrintCount();
        String copyLabel = pc <= 1 ? "**  ORIGINAL  **" : "**  COPY " + (pc - 1) + "  **";
        String invoiceTypeLabel = isVatInvoice ? "TAX INVOICE" : "INVOICE";

        int itemCount = order.getItems().size();
        float estimatedHeight = 900f + (itemCount * 20f) + 260f; // fixed content + items + logo/signatures
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Document doc = new Document(new PdfDocument(new PdfWriter(out)),
                new PageSize(RECEIPT_WIDTH, Math.max(900f, estimatedHeight)));
        doc.setMargins(RECEIPT_MARGIN, RECEIPT_MARGIN, RECEIPT_MARGIN, RECEIPT_MARGIN);

        PdfFont mono     = PdfFontFactory.createFont(StandardFonts.COURIER);
        PdfFont monoBold = PdfFontFactory.createFont(StandardFonts.COURIER_BOLD);

        // ── Header ────────────────────────────────────────────────────────────
        byte[] logoBytes = fetchLogoBytes(profile);
        if (logoBytes != null) {
            try {
                ImageData logoData = ImageDataFactory.create(logoBytes);
                // Constrain by whichever dimension is tighter — a wide logo scaled only
                // by height could overflow the narrow receipt width, which (like the
                // divider lines above) has no way to wrap/break and would fail to lay out.
                float scale = Math.min(28f / logoData.getHeight(), (RECEIPT_WIDTH - 2 * RECEIPT_MARGIN) / logoData.getWidth());
                Image logo = new Image(logoData)
                        .setWidth(logoData.getWidth() * scale)
                        .setHeight(logoData.getHeight() * scale)
                        .setHorizontalAlignment(com.itextpdf.layout.properties.HorizontalAlignment.CENTER);
                doc.add(logo);
            } catch (Exception e) {
                log.warn("Could not decode logo for thermal preview: {}", e.toString());
            }
        }
        doc.add(rLine(trunc(profile.companyName(), W), monoBold, RECEIPT_FONT_SZ + 1.5f, TextAlignment.CENTER));
        doc.add(rLine("", mono));
        doc.add(rLine("E-mail : " + safe(profile.email(), "—"), mono));
        doc.add(rLine("", mono));
        doc.add(rLine(copyLabel, monoBold, RECEIPT_FONT_SZ, TextAlignment.CENTER));
        doc.add(new Paragraph(invoiceTypeLabel).setFont(monoBold).setFontSize(RECEIPT_FONT_SZ + 2)
                .setTextAlignment(TextAlignment.CENTER).setMultipliedLeading(1.1f).setMarginBottom(2));
        doc.add(rLine("", mono));
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Date of Invoice ───────────────────────────────────────────────────
        doc.add(rLine("Date Of Invoice : " + fmtInstant(invoice.getCreatedAt()), mono));
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Tax Invoice No / Invoice No ───────────────────────────────────────
        doc.add(new Paragraph()
                .add(new Text(invoiceNoLabel + " ").setFont(mono).setFontSize(RECEIPT_FONT_SZ))
                .add(new Text(invoice.getInvoiceNumber()).setFont(monoBold).setFontSize(RECEIPT_FONT_SZ + 1.5f))
                .setMultipliedLeading(1.1f).setMarginBottom(0));
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Supplier block ────────────────────────────────────────────────────
        doc.add(rLine("Supplier's TIN    : " + safe(stripTinSuffix(profile.taxId()), "N/A"), mono));
        doc.add(rLine("Supplier's Name   : " + trunc(profile.companyName(), 43), mono));
        doc.add(rWrapped("Reg. Address      : ", safe(profile.registeredAddress(), "—"), W, mono));
        doc.add(rWrapped("Operating Address : ", safe(profile.operatingAddress(), "—"), W, mono));
        doc.add(rLine("Contact No/Fax No : " + safe(profile.phone(), "—"), mono));
        doc.add(rLine("-".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Purchaser block — billingParty resolves to head office when the
        //    customer is a branch, exactly as generateEscPos/generate do. Place
        //    of Supply sits after Contact No (identifies the branch/outlet);
        //    placeholder "0000000000" phone numbers print blank. ─────────────
        Customer billingParty = billingParty(invoice.getCustomer());
        doc.add(rLine("Purchaser's TIN   : " + safe(invoice.getCustomer().getTaxNumber(), "N/A"), mono));
        doc.add(rLine("Purchaser's Name  : " + trunc(billingParty.getName(), 43), mono));
        doc.add(rWrapped("Address           : ", safe(primaryAddressLine(billingParty), "—"), W, mono));
        doc.add(rLine("Contact No        : " + displayPhone(invoice.getCustomer().getPhone()), mono));
        doc.add(rLine("Place of Supply   : " + safe(invoice.getCustomer().getPlaceOfSupplier(), dots(20)), mono));
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Purchase Order No / Date of Supply — printed when the rep entered one ──
        doc.add(rLine("Purchase Order No : " + safe(order.getPoNumber(), dots(15)) + " Date : " + dots(8), mono));
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));
        doc.add(rLine("Date of Supply    : " + (order.getOrderDate() != null ? fmtInstant(order.getOrderDate()) : "—"), mono));
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Items ─────────────────────────────────────────────────────────────
        doc.add(rLine(pR("No", 2) + " " + pR("Description", 24) + " " + pL("Qty", 6) + " " + pL("Price", 11) + " " + pL("Amount", 17), monoBold));
        doc.add(rLine("-".repeat(W), mono));

        int no = 1;
        for (OrderItem item : order.getItems()) {
            boolean isFree = "FREE_PRODUCT".equals(item.getPriceSource());
            String name = trunc(item.getProduct().getName() + (isFree ? " (FREE)" : ""), 24);
            String qty  = trunc(item.getQuantity().toPlainString(), 6);
            BigDecimal displayUnitPrice = (isFree && item.getUnitPrice().compareTo(BigDecimal.ZERO) == 0)
                    ? item.getProduct().getDefaultPrice()
                    : item.getUnitPrice();
            BigDecimal amountExclVat = isFree
                    ? displayUnitPrice.multiply(item.getQuantity())
                    : item.getLineTotal().subtract(item.getTaxAmount());
            String up  = fmtAmount(displayUnitPrice);
            String amt = fmtAmount(amountExclVat);
            doc.add(rLine(pR(String.valueOf(no++), 2) + " " + pR(name, 24) + " " + pL(qty, 6) + " " + pL(up, 11) + " " + pL(amt, 17), mono));
            if (isFree && item.getPromotionName() != null) {
                doc.add(rLine("   " + trunc(item.getPromotionName(), W - 3), mono));
            }
        }
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Totals ────────────────────────────────────────────────────────────
        int lw = 46, rw = W - lw;
        BigDecimal discountTotal = invoice.getDiscountTotal() != null ? invoice.getDiscountTotal() : BigDecimal.ZERO;
        boolean hasDiscount = discountTotal.compareTo(BigDecimal.ZERO) > 0;

        doc.add(rLine(pR("Total Value of Supply", lw) + pL(fmtAmount(invoice.getSubtotal()), rw), mono));
        if (hasDiscount) {
            doc.add(rLine(pR("Discount", lw) + pL("(" + fmtAmount(discountTotal) + ")", rw), mono));
            doc.add(rLine(pR("Net Amount", lw) + pL(fmtAmount(invoice.getSubtotal().subtract(discountTotal)), rw), mono));
        }
        doc.add(rLine(pR("VAT Amount (@ " + formatRate(effectiveTaxPct(invoice)) + "%)", lw) + pL(fmtAmount(invoice.getTaxTotal()), rw), mono));
        doc.add(rLine(pR("Total Amount Including VAT", lw) + pL(fmtAmount(invoice.getTotal()), rw), monoBold, RECEIPT_FONT_SZ + 1f, TextAlignment.LEFT));
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        doc.add(rWrapped("Total Amount in Words : ", amountInWords(invoice.getTotal()), W, mono));
        doc.add(rLine("=".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Payment ───────────────────────────────────────────────────────────
        doc.add(rLine("Mode of Payment : " + dots(20), mono));
        doc.add(rLine("", mono));
        doc.add(rLine("Payment Instructions:", mono));
        doc.add(rLine("Cheque should be drawn in favor of", mono));
        doc.add(rLine("\"" + trunc(profile.companyName(), W - 2) + "\"", mono));
        doc.add(rLine("", mono));
        doc.add(rLine("Payment      : [ ] Cash   [ ] Chq   [ ] Online", mono));
        doc.add(rLine("Account Name : " + trunc(safe(profile.bankAccountName(), "—"), 45), mono));
        doc.add(rLine("Account No.  : " + trunc(safe(profile.bankAccountNumber(), "—"), 45), mono));
        doc.add(rLine("Bank Name    : " + trunc(safe(profile.bankName(), "—"), 45), mono));
        doc.add(rLine("Branch Name  : " + trunc(safe(profile.bankBranch(), "—"), 45), mono));
        doc.add(rLine("-".repeat(W), mono));
        doc.add(rLine("", mono));

        // ── Signatures ────────────────────────────────────────────────────────
        doc.add(rLine("Received the goods in good condition", mono));
        doc.add(rLine("", mono));
        addReceiptSignature(doc, order.getCustomerSignature(), mono);
        doc.add(rLine("Customer Name / NIC No. & Sign", mono));
        doc.add(rLine("", mono));
        addReceiptSignature(doc, order.getSalespersonSignature(), mono);
        doc.add(rLine("Sales Rep Signature", mono));
        String repNamePreview = repName(order);
        if (repNamePreview != null) {
            doc.add(rLine(trunc(repNamePreview, W), monoBold));
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
     * PDF-preview counterpart to wrapLabeledField. iText's line renderer trims
     * leading whitespace at the start of every line — including a continuation
     * line produced by an explicit "\n" — so building this as one Paragraph with
     * space-character indentation (like the ESC/POS text version) silently loses
     * the indent and the wrapped text collapses back under the label instead of
     * lining up under the value. A left margin isn't whitespace, so it survives:
     * each continuation line is its own zero-top-margin Paragraph indented by
     * label.length() character-widths (Courier is exactly 0.6em/char).
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

    /** Real (not 1-bit rasterized) signature image for the receipt preview — a
     *  crisp preview image is more useful here than replicating the printer's
     *  actual dot-matrix dithering, which addSignatureBlock/printSignatureEscPos
     *  already handle for the real PDF/thermal outputs. */
    private void addReceiptSignature(Document doc, String base64Signature, PdfFont font) {
        if (base64Signature != null && !base64Signature.isBlank()) {
            try {
                byte[] bytes = Base64.getDecoder().decode(base64Signature);
                ImageData data = ImageDataFactory.create(bytes);
                float scale = Math.min((RECEIPT_WIDTH - 2 * RECEIPT_MARGIN) / data.getWidth(), 60f / data.getHeight());
                doc.add(new Image(data).setWidth(data.getWidth() * scale).setHeight(data.getHeight() * scale));
                return;
            } catch (Exception e) {
                log.warn("Could not decode/render signature image for thermal preview: {}", e.toString());
            }
        }
        doc.add(rLine(dots(30), font));
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
     * sales rep to fill in by hand — Place of Supply (never collected digitally),
     * Purchase Order No when the rep left it blank (optional field, see
     * order.getPoNumber()), and the invoice Date field.
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
     * Splits "value" into chunks that each fit within (totalWidth - label.length())
     * chars, breaking on the last space that fits rather than mid-word. Shared by
     * wrapLabeledField (ESC/POS) and rWrapped (PDF preview) so the two renderers'
     * wrap points never drift apart — only how each renders the continuation
     * indent differs.
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

    /**
     * Word-wraps "label + value" into one or more lines that each fit within
     * totalWidth chars, continuation lines indented with plain spaces to align
     * under the value (not the label) — safe here since raw ESC/POS text bytes
     * print literally, unlike the PDF preview (see rWrapped).
     */
    private String wrapLabeledField(String label, String value, int totalWidth) {
        List<String> lines = wrapValue(label, value, totalWidth);
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
        // Right padding matches iCell's (3.5f, used by the items table's Amount column)
        // so the totals values' right edge lines up exactly with the item amounts above.
        t.addCell(new Cell()
                .add(new Paragraph(value).setFont(vf).setFontSize(8.5f)
                        .setTextAlignment(TextAlignment.RIGHT))
                .setBorder(Border.NO_BORDER).setPaddingRight(3.5f).setPaddingTop(3).setPaddingBottom(3));
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
     * Rasterizes an image (logo) and emits it via the ESC/POS "GS v 0" raster
     * bit image command, scaled to [targetWidth] dots with no height limit.
     */
    private boolean printLogoEscPos(ByteArrayOutputStream buf, byte[] logoBytes, int targetWidth) {
        return printRasterEscPos(buf, logoBytes, targetWidth, Integer.MAX_VALUE);
    }

    /**
     * Rasterizes an image (logo or signature) and emits it via the ESC/POS "GS v 0"
     * raster bit image command. Scales to [targetWidth] dots preserving aspect
     * ratio, then — if that would make it taller than [maxHeight] — scales down
     * further by height instead so it never prints taller than intended
     * regardless of the source image's aspect ratio. Thresholds to 1-bit
     * black/white — thermal printers have no grayscale, so a simple luminance
     * threshold is used rather than dithering. Never throws: a broken/unreadable
     * image just prints nothing rather than failing the whole invoice — returns
     * false so callers with a text fallback (e.g. the signature blank line) know
     * to use it.
     */
    private boolean printRasterEscPos(ByteArrayOutputStream buf, byte[] imageBytes, int targetWidth, int maxHeight) {
        try {
            BufferedImage src = ImageIO.read(new ByteArrayInputStream(imageBytes));
            if (src == null) {
                log.warn("ESC/POS raster decode returned null — unsupported/corrupt image bytes ({} bytes)",
                        imageBytes.length);
                return false;
            }

            int width  = targetWidth;
            int height = Math.max(1, Math.round(src.getHeight() * (width / (float) src.getWidth())));
            if (height > maxHeight) {
                height = maxHeight;
                width  = Math.max(1, Math.round(src.getWidth() * (height / (float) src.getHeight())));
            }

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
                if (printRasterEscPos(buf, bytes, targetWidth, SIGNATURE_MAX_HEIGHT_PX)) return;
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
        // tryGetLogoBytes() (not getLogoBytes()) — its own catch lives inside the
        // @Transactional method, so a storage failure here can't poison the invoice
        // generation transaction this runs inside of. See its Javadoc for why catching
        // the exception out here instead wouldn't be enough.
        return companyProfileService.tryGetLogoBytes();
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

    /**
     * The party billed on the invoice's Purchaser Name/Address — the head office's
     * when the customer is a branch (has a parentCustomer), otherwise the customer
     * itself unchanged. Only name/address switch; TIN, phone, and Place of Supply
     * stay the branch's own since they legitimately identify this specific outlet.
     */
    private Customer billingParty(Customer customer) {
        return customer.getParentCustomer() != null ? customer.getParentCustomer() : customer;
    }

    /**
     * Bulk-imported/legacy customer data often carries a literal "0000000000"
     * placeholder phone number rather than leaving it genuinely blank — printed
     * as empty rather than the placeholder digits, same as a truly-missing phone.
     */
    private String displayPhone(String phone) {
        if (phone == null || phone.isBlank() || "0000000000".equals(phone.trim())) return "";
        return phone;
    }

    /** The sales rep's printable name for under their signature — full name, falling
     *  back to username, matching the convention used elsewhere (e.g. InvoiceSummaryDto). */
    private String repName(Order order) {
        User rep = order.getSalesRep();
        if (rep == null) return null;
        return rep.getFullName() != null && !rep.getFullName().isBlank() ? rep.getFullName() : rep.getUsername();
    }

    private String safe(String s, String fallback) {
        return (s == null || s.isBlank()) ? fallback : s;
    }
}
