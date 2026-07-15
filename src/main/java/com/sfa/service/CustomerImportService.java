package com.sfa.service;

import com.sfa.dto.customer.AddressRequest;
import com.sfa.dto.customer.CreateCustomerRequest;
import com.sfa.dto.customer.CustomerImportResultDto;
import com.sfa.dto.customer.CustomerImportRowResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomerImportService {

    private static final String[] HEADERS = {
            "Customer Code", "Customer Name*", "Contact Person", "Phone", "Email",
            "Address Label", "Address*", "Tax Number", "Tax Type", "Credit Limit",
            "Credit Days", "Visibility Rule"
    };

    private static final String[] TAX_TYPES        = {"STANDARD", "EXEMPT", "ZERO_RATED"};
    private static final String[] VISIBILITY_RULES  = {"ALL", "ASSIGNED"};
    private static final int MAX_TEMPLATE_ROWS = 3000;

    private final CustomerService customerService;

    @PersistenceContext
    private EntityManager em;

    // ── Template generation ─────────────────────────────────────────────────

    public byte[] generateTemplate() throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Customers");

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

            addDropdown(sheet, 8, TAX_TYPES);
            addDropdown(sheet, 11, VISIBILITY_RULES);

            addInstructionsSheet(wb);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void addDropdown(Sheet sheet, int columnIndex, String[] options) {
        DataValidationHelper helper = sheet.getDataValidationHelper();
        DataValidationConstraint constraint = helper.createExplicitListConstraint(options);
        CellRangeAddressList range = new CellRangeAddressList(1, MAX_TEMPLATE_ROWS, columnIndex, columnIndex);
        DataValidation validation = helper.createValidation(constraint, range);
        validation.setShowErrorBox(true);
        validation.setSuppressDropDownArrow(true);
        sheet.addValidationData(validation);
    }

    private void addInstructionsSheet(Workbook wb) {
        Sheet sheet = wb.createSheet("Instructions");
        CellStyle boldStyle = wb.createCellStyle();
        Font boldFont = wb.createFont();
        boldFont.setBold(true);
        boldStyle.setFont(boldFont);

        String[][] rows = {
                {"Column", "Required?", "Notes"},
                {"Customer Code", "No", "Leave blank to auto-generate. If you have your own account/reference numbers, you may fill them in — they must be unique."},
                {"Customer Name", "Yes", "Full customer/business name."},
                {"Contact Person", "No", ""},
                {"Phone", "No", ""},
                {"Email", "No", ""},
                {"Address Label", "No", "Defaults to \"Main\" if left blank (e.g. Main, Billing, Warehouse)."},
                {"Address", "Yes", "Full address as one line."},
                {"Tax Number", "No", ""},
                {"Tax Type", "No", "One of: STANDARD, EXEMPT, ZERO_RATED. Defaults to STANDARD."},
                {"Credit Limit", "No", "Numeric amount, e.g. 50000."},
                {"Credit Days", "No", "Whole number of days, e.g. 30."},
                {"Visibility Rule", "No", "One of: ALL, ASSIGNED. Defaults to ALL."},
                {"", "", ""},
                {"Do not modify the header row on the \"Customers\" sheet. One row = one customer.", "", ""},
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

    public CustomerImportResultDto importFromExcel(MultipartFile file) throws IOException {
        List<CustomerImportRowResult> errors = new ArrayList<>();
        int totalRows = 0;
        int successCount = 0;

        try (InputStream in = file.getInputStream(); Workbook wb = WorkbookFactory.create(in)) {
            Sheet sheet = wb.getSheetAt(0);

            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) continue;
                int excelRowNumber = r + 1; // 1-based, matches what the user sees in Excel
                String name = null;

                try {
                    String customerCode   = cellToString(row.getCell(0));
                    name                  = cellToString(row.getCell(1));
                    String contactPerson  = cellToString(row.getCell(2));
                    String phone          = cellToString(row.getCell(3));
                    String email          = cellToString(row.getCell(4));
                    String addressLabel   = cellToString(row.getCell(5));
                    String addressLine    = cellToString(row.getCell(6));
                    String taxNumber      = cellToString(row.getCell(7));
                    String taxType        = cellToString(row.getCell(8));
                    String creditLimitStr = cellToString(row.getCell(9));
                    String creditDaysStr  = cellToString(row.getCell(10));
                    String visibilityRule = cellToString(row.getCell(11));

                    boolean rowIsBlank = isBlank(name) && isBlank(addressLine) && isBlank(customerCode)
                            && isBlank(phone) && isBlank(email);
                    if (rowIsBlank) continue;

                    totalRows++;

                    if (isBlank(name)) {
                        throw new IllegalArgumentException("Customer Name is required");
                    }
                    if (isBlank(addressLine)) {
                        throw new IllegalArgumentException("Address is required");
                    }

                    String code = isBlank(customerCode) ? nextCustomerCode() : customerCode.trim();
                    String label = isBlank(addressLabel) ? "Main" : addressLabel.trim();
                    String resolvedTaxType = isBlank(taxType) ? "STANDARD" : validateEnum(taxType, TAX_TYPES, "Tax Type");
                    String resolvedVisibility = isBlank(visibilityRule) ? "ALL" : validateEnum(visibilityRule, VISIBILITY_RULES, "Visibility Rule");
                    BigDecimal creditLimit = parseDecimal(creditLimitStr, "Credit Limit");
                    Integer creditDays = parseInt(creditDaysStr, "Credit Days");

                    CreateCustomerRequest req = new CreateCustomerRequest(
                            code, name.trim(), blankToNull(contactPerson), blankToNull(phone), blankToNull(email),
                            null, blankToNull(taxNumber), resolvedTaxType, null, null, resolvedVisibility,
                            creditLimit, creditDays,
                            List.of(new AddressRequest(label, addressLine.trim())));

                    customerService.create(req);
                    successCount++;
                } catch (Exception e) {
                    errors.add(new CustomerImportRowResult(excelRowNumber, isBlank(name) ? "—" : name, e.getMessage()));
                }
            }
        }

        return new CustomerImportResultDto(totalRows, successCount, errors.size(), errors);
    }

    private String validateEnum(String value, String[] allowed, String fieldLabel) {
        String v = value.trim().toUpperCase().replace(' ', '_');
        for (String option : allowed) {
            if (option.equals(v)) return option;
        }
        throw new IllegalArgumentException("Invalid " + fieldLabel + ": \"" + value + "\" (allowed: "
                + String.join(", ", allowed) + ")");
    }

    private BigDecimal parseDecimal(String value, String fieldLabel) {
        if (isBlank(value)) return null;
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + fieldLabel + ": \"" + value + "\"");
        }
    }

    private Integer parseInt(String value, String fieldLabel) {
        if (isBlank(value)) return null;
        try {
            return (int) Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid " + fieldLabel + ": \"" + value + "\"");
        }
    }

    private String nextCustomerCode() {
        Number seq = (Number) em.createNativeQuery("SELECT NEXTVAL('customer_code_seq')").getSingleResult();
        return "CUST-" + String.format("%05d", seq.longValue());
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

    private String blankToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
