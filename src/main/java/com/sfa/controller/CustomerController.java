package com.sfa.controller;

import com.sfa.dto.customer.CreateCustomerRequest;
import com.sfa.dto.customer.CustomerAnalyticsDto;
import com.sfa.dto.customer.CustomerDto;
import com.sfa.dto.customer.CustomerImportResultDto;
import com.sfa.dto.customer.QuickCreateCustomerRequest;
import com.sfa.dto.product.ProductDto;
import com.sfa.entity.Damage;
import com.sfa.entity.Order;
import com.sfa.entity.PosSale;
import com.sfa.entity.Return;
import com.sfa.entity.Role;
import com.sfa.exception.BusinessException;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.CustomerExportGenerator;
import com.sfa.service.CustomerImportService;
import com.sfa.service.CustomerService;
import com.sfa.service.DamageService;
import com.sfa.service.PosService;
import com.sfa.service.ReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerService customerService;
    private final CustomerImportService customerImportService;
    private final CustomerExportGenerator customerExportGenerator;
    private final PosService posService;
    private final ReturnService returnService;
    private final DamageService damageService;

    @GetMapping
    public Page<CustomerDto> list(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Set<UUID> restricted = restrictedIds(user);
        return customerService.list(search, restricted, PageRequest.of(page, size, Sort.by("name")));
    }

    @GetMapping("/export")
    @RequiresLicense(LicensedPackage.SFA)
    public ResponseEntity<byte[]> export(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam String format,
            @RequestParam(defaultValue = "") String search) throws IOException {
        Set<UUID> restricted = restrictedIds(user);
        List<CustomerDto> rows = customerService.list(search, restricted, Pageable.unpaged()).getContent();

        byte[] bytes;
        MediaType contentType;
        String filename;

        switch (format.toLowerCase()) {
            case "xlsx" -> {
                bytes = customerExportGenerator.generateExcel(rows);
                contentType = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
                filename = "customers.xlsx";
            }
            case "csv" -> {
                bytes = customerExportGenerator.generateCsv(rows);
                contentType = MediaType.parseMediaType("text/csv");
                filename = "customers.csv";
            }
            case "pdf" -> {
                bytes = customerExportGenerator.generatePdf(rows);
                contentType = MediaType.APPLICATION_PDF;
                filename = "customers.pdf";
            }
            default -> throw new BusinessException("Unsupported export format: " + format);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(contentType)
                .body(bytes);
    }

    @GetMapping("/{id}")
    public CustomerDto getById(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id) {
        Set<UUID> restricted = restrictedIds(user);
        if (restricted != null && !restricted.contains(id)) {
            throw new AccessDeniedException("Customer not accessible");
        }
        return customerService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @RequiresLicense(LicensedPackage.SFA)
    public ResponseEntity<CustomerDto> create(@Valid @RequestBody CreateCustomerRequest req) {
        CustomerDto dto = customerService.create(req);
        return ResponseEntity.created(URI.create("/api/customers/" + dto.id())).body(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @RequiresLicense(LicensedPackage.SFA)
    public CustomerDto update(@PathVariable UUID id, @Valid @RequestBody CreateCustomerRequest req) {
        return customerService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @RequiresLicense(LicensedPackage.SFA)
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        customerService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @RequiresLicense(LicensedPackage.SFA)
    public ResponseEntity<byte[]> downloadImportTemplate() throws IOException {
        byte[] bytes = customerImportService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"customer-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @RequiresLicense(LicensedPackage.SFA)
    public CustomerImportResultDto importCustomers(@RequestParam("file") MultipartFile file) throws IOException {
        return customerImportService.importFromExcel(file);
    }

    @PostMapping("/pos")
    @PreAuthorize("isAuthenticated()")
    @RequiresLicense(LicensedPackage.POS)
    public ResponseEntity<CustomerDto> quickCreate(@Valid @RequestBody QuickCreateCustomerRequest req) {
        CustomerDto dto = customerService.quickCreate(req);
        return ResponseEntity.created(URI.create("/api/customers/" + dto.id())).body(dto);
    }

    // isAuthenticated (not admin-only): the /pos billing dropdown calls this for every cashier, not just admins
    @GetMapping("/pos")
    @PreAuthorize("isAuthenticated()")
    @RequiresLicense(LicensedPackage.POS)
    public Page<CustomerDto> listPosCustomers(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return customerService.listPosCustomers(search, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }

    @GetMapping("/{id}/products")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @RequiresLicense(LicensedPackage.SFA)
    public List<ProductDto> getAssignedProducts(@PathVariable UUID id) {
        return customerService.getAssignedProducts(id);
    }

    @PutMapping("/{id}/products")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @RequiresLicense(LicensedPackage.SFA)
    public ResponseEntity<Void> setAssignedProducts(
            @PathVariable UUID id,
            @RequestBody Set<UUID> productIds) {
        customerService.setAssignedProducts(id, productIds);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/analytics")
    @RequiresLicense(LicensedPackage.SFA)
    public CustomerAnalyticsDto analytics(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id) {
        checkAccess(user, id);
        return customerService.getAnalytics(id);
    }

    @GetMapping("/{id}/orders")
    @RequiresLicense(LicensedPackage.SFA)
    public Page<Order> customerOrders(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id,
            Pageable pageable) {
        checkAccess(user, id);
        return customerService.getCustomerOrders(id, pageable);
    }

    @GetMapping("/{id}/returns")
    @RequiresLicense(LicensedPackage.SFA)
    public Page<Return> customerReturns(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id,
            Pageable pageable) {
        checkAccess(user, id);
        return returnService.getCustomerReturns(id, pageable);
    }

    @GetMapping("/{id}/damages")
    @RequiresLicense(LicensedPackage.SFA)
    public Page<Damage> customerDamages(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id,
            Pageable pageable) {
        checkAccess(user, id);
        return damageService.getCustomerDamages(id, pageable);
    }

    @PostMapping("/{id}/credit/payments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @RequiresLicense(LicensedPackage.POS)
    public List<PosController.PosSaleResponseDto> recordCreditPayment(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String method = (String) body.get("paymentMethod");
        String notes = (String) body.get("notes");
        List<PosSale> affected = posService.recordBulkPaymentForCustomer(id, amount, method, notes, principal.getId());
        return affected.stream().map(PosController.PosSaleResponseDto::from).toList();
    }

    @GetMapping("/{id}/credit/payments")
    @RequiresLicense(LicensedPackage.POS)
    public Page<PosController.PosSalePaymentDto> creditPayments(
            @AuthenticationPrincipal UserDetailsImpl user,
            @PathVariable UUID id,
            Pageable pageable) {
        checkAccess(user, id);
        return posService.getPaymentsForCustomer(id, pageable).map(PosController.PosSalePaymentDto::from);
    }

    private void checkAccess(UserDetailsImpl user, UUID customerId) {
        Set<UUID> restricted = restrictedIds(user);
        if (restricted != null && !restricted.contains(customerId)) {
            throw new AccessDeniedException("Customer not accessible");
        }
    }

    /** Returns the restricted customer ID set for a SALES_REP with assigned customers, or null for unrestricted access. */
    private Set<UUID> restrictedIds(UserDetailsImpl user) {
        if (user != null
                && Role.SALES_REP.equals(user.getRoleName())
                && !user.getAssignedCustomerIds().isEmpty()) {
            return user.getAssignedCustomerIds();
        }
        return null;
    }
}
