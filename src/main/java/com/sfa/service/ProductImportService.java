package com.sfa.service;

import com.sfa.dto.product.CreateProductRequest;
import com.sfa.dto.product.ProductDto;
import com.sfa.dto.product.ProductImportResultDto;
import com.sfa.dto.product.ProductImportRowResult;
import com.sfa.entity.ProductCategory;
import com.sfa.entity.Unit;
import com.sfa.repository.ProductCategoryRepository;
import com.sfa.repository.UnitRepository;
import com.sfa.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bulk product creation from a stock-sheet-style Excel upload — columns
 * No / Product Code / Description / Category / Unit / Stock / Purchase Price /
 * MRP / Max Discount. "Description" is used as the product's name (there's no
 * separate long-form description column). Max Discount is intentionally NOT
 * imported (left at the product's default of 0) — the source sheet's values
 * for that column aren't in the Rs-amount unit the app stores it in.
 *
 * Mirrors {@link CustomerImportService}: reuses {@link ProductService#create}
 * per row (same validation/uniqueness rules as the manual "New Product" form),
 * continues past row-level errors instead of aborting the whole file, and
 * reports a per-row result list rather than a single pass/fail.
 */
@Service
@RequiredArgsConstructor
public class ProductImportService {

    private static final String[] HEADERS = {
            "No", "Product Code*", "Description*", "Category", "Unit",
            "Stock", "Purchase Price", "MRP", "Max Discount"
    };

    // Matches the "New Product" form's own default (ProductDetailPage) so an
    // imported product behaves the same as one created manually with the tax
    // field left untouched — the sheet has no Tax Rate column of its own.
    private static final BigDecimal DEFAULT_TAX_RATE = new BigDecimal("15.00");

    private final ProductService productService;
    private final ProductCategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final InventoryService inventoryService;

    // ── Template generation ─────────────────────────────────────────────────

    public byte[] generateTemplate() throws IOException {
        try (Workbook wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Products");

            CellStyle headerStyle = wb.createCellStyle();
            Font headerFont = wb.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            Row header = sheet.createRow(0);
            for (int c = 0; c < HEADERS.length; c++) {
                Cell cell = header.createCell(c);
                cell.setCellValue(HEADERS[c]);
                cell.setCellStyle(headerStyle);
            }
            for (int c = 0; c < HEADERS.length; c++) {
                sheet.setColumnWidth(c, 22 * 256);
            }

            addInstructionsSheet(wb);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void addInstructionsSheet(Workbook wb) {
        Sheet sheet = wb.createSheet("Instructions");
        CellStyle boldStyle = wb.createCellStyle();
        Font boldFont = wb.createFont();
        boldFont.setBold(true);
        boldStyle.setFont(boldFont);

        String[][] rows = {
                {"Column", "Required?", "Notes"},
                {"No", "No", "Just a row number — ignored on import."},
                {"Product Code", "Yes", "Must be unique. A row is skipped with an error if the code already exists."},
                {"Description", "Yes", "Used as the product's name."},
                {"Category", "No", "Matched by name (case-insensitive). Created automatically if it doesn't exist yet."},
                {"Unit", "No", "Matched by name (case-insensitive). Created automatically if it doesn't exist yet."},
                {"Stock", "No", "Initial stock quantity. Only recorded if inventory tracking is enabled (Settings > General)."},
                {"Purchase Price", "No", "Numeric, e.g. 120.50. Defaults to 0 if left blank — price it later from the Products page."},
                {"MRP", "No", "Numeric — the product's selling price. Defaults to 0 if left blank — price it later from the Products page."},
                {"Max Discount", "No", "Not imported — new products get the default of 0. Set it manually afterwards if needed."},
                {"", "", ""},
                {"Do not modify the header row on the \"Products\" sheet. One row = one product.", "", ""},
                {"Save the file as .xlsx before uploading it back.", "", ""},
        };
        for (int r = 0; r < rows.length; r++) {
            Row row = sheet.createRow(r);
            for (int c = 0; c < rows[r].length; c++) {
                Cell cell = row.createCell(c);
                cell.setCellValue(rows[r][c]);
                if (r == 0) cell.setCellStyle(boldStyle);
            }
        }
        for (int c = 0; c < 3; c++) sheet.setColumnWidth(c, 40 * 256);
    }

    // ── Import ───────────────────────────────────────────────────────────────

    public ProductImportResultDto importFromExcel(MultipartFile file) throws IOException {
        List<ProductImportRowResult> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;
        boolean stockTrackingEnabled = inventoryService.isEnabled();
        UUID userId = currentUserId();

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                int excelRowNumber = r + 1; // 1-based, matches what the user sees in Excel
                String productCode = null;

                try {
                    productCode              = cellToString(row.getCell(1));
                    String description       = cellToString(row.getCell(2));
                    String categoryName      = cellToString(row.getCell(3));
                    String unitName          = cellToString(row.getCell(4));
                    String stockStr          = cellToString(row.getCell(5));
                    String purchasePriceStr  = cellToString(row.getCell(6));
                    String mrpStr            = cellToString(row.getCell(7));
                    // Column 8 ("Max Discount") is intentionally not read — see class Javadoc.

                    boolean rowIsBlank = isBlank(productCode) && isBlank(description)
                            && isBlank(purchasePriceStr) && isBlank(mrpStr);
                    if (rowIsBlank) continue;

                    totalRows++;

                    if (isBlank(productCode)) {
                        throw new IllegalArgumentException("Product Code is required");
                    }
                    if (isBlank(description)) {
                        throw new IllegalArgumentException("Description is required");
                    }

                    // Purchase Price/MRP are optional on the sheet — a catalog/stock upload commonly
                    // arrives before pricing is finalized. Missing values default to 0 rather than
                    // rejecting the row; the product can be priced afterward from the Products page.
                    BigDecimal purchasePrice = parseOptionalDecimal(purchasePriceStr, "Purchase Price");
                    BigDecimal mrp           = parseOptionalDecimal(mrpStr, "MRP");
                    BigDecimal stockQty      = parseDecimal(stockStr, "Stock");

                    UUID categoryId = isBlank(categoryName) ? null : resolveOrCreateCategory(categoryName.trim()).getId();
                    UUID unitId     = isBlank(unitName) ? null : resolveOrCreateUnit(unitName.trim()).getId();

                    CreateProductRequest req = new CreateProductRequest(
                            productCode.trim(), null, description.trim(), null,
                            categoryId, unitId, mrp, purchasePrice,
                            DEFAULT_TAX_RATE, BigDecimal.ZERO);

                    ProductDto created = productService.create(req);
                    successCount++;

                    if (stockQty != null && stockQty.compareTo(BigDecimal.ZERO) > 0) {
                        if (stockTrackingEnabled) {
                            try {
                                inventoryService.receiveStock(created.id(), stockQty, purchasePrice,
                                        LocalDate.now(), "Initial stock import", userId);
                            } catch (Exception stockEx) {
                                errors.add(new ProductImportRowResult(excelRowNumber, productCode.trim(),
                                        "Product created, but initial stock could not be recorded: " + stockEx.getMessage()));
                            }
                        }
                    }
                } catch (Exception e) {
                    errors.add(new ProductImportRowResult(excelRowNumber, isBlank(productCode) ? "—" : productCode, e.getMessage()));
                }
            }
        }

        String note = stockTrackingEnabled ? null
                : "Inventory tracking is disabled, so Stock quantities were not recorded — products were still created. "
                + "Enable it in Settings > General, then use Inventory > Receive Stock to add opening stock.";

        return new ProductImportResultDto(totalRows, successCount, errors.size(), errors, stockTrackingEnabled, note);
    }

    private ProductCategory resolveOrCreateCategory(String name) {
        return categoryRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    ProductCategory cat = new ProductCategory();
                    cat.setName(name);
                    return categoryRepository.save(cat);
                });
    }

    private Unit resolveOrCreateUnit(String name) {
        return unitRepository.findByNameIgnoreCase(name)
                .orElseGet(() -> {
                    Unit unit = new Unit();
                    unit.setName(name);
                    return unitRepository.save(unit);
                });
    }

    private BigDecimal parseOptionalDecimal(String value, String fieldLabel) {
        BigDecimal parsed = parseDecimal(value, fieldLabel);
        return parsed != null ? parsed : BigDecimal.ZERO;
    }

    private BigDecimal parseDecimal(String value, String fieldLabel) {
        if (isBlank(value)) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + fieldLabel + ": \"" + value + "\"");
        }
    }

    private UUID currentUserId() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        return principal instanceof UserDetailsImpl u ? u.getId() : null;
    }

    private String cellToString(Cell cell) {
        if (cell == null) return null;
        switch (cell.getCellType()) {
            case STRING -> {
                return cell.getStringCellValue().trim();
            }
            case NUMERIC -> {
                double d = cell.getNumericCellValue();
                return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            }
            case BOOLEAN -> {
                return String.valueOf(cell.getBooleanCellValue());
            }
            case FORMULA -> {
                return cell.getCellFormula();
            }
            default -> {
                return null;
            }
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
