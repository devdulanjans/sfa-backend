package com.sfa.controller;

import com.sfa.dto.product.CreateProductRequest;
import com.sfa.dto.product.CustomerCatalogItemDto;
import com.sfa.dto.product.ProductDto;
import com.sfa.dto.tenant.BulkAssignResultDto;
import com.sfa.dto.tenant.BulkAssignTenantRequest;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public Page<ProductDto> list(
            @RequestParam(defaultValue = "") String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return productService.list(search, PageRequest.of(page, size, Sort.by("name")));
    }

    @GetMapping("/visible")
    public List<ProductDto> visibleToCustomer(@RequestParam UUID customerId) {
        return productService.visibleToCustomer(customerId);
    }

    /** Customer app catalog — the logged-in customer's products, each with its one resolved price. */
    @GetMapping("/customer-catalog")
    @PreAuthorize("hasRole('CUSTOMER')")
    public List<CustomerCatalogItemDto> customerCatalog(@AuthenticationPrincipal UserDetailsImpl user) {
        if (user.getLinkedCustomerId() == null) {
            throw new com.sfa.exception.BusinessException("Your account is not linked to a customer record");
        }
        return productService.customerCatalog(user.getLinkedCustomerId());
    }

    @GetMapping("/{id}")
    public ProductDto getById(@PathVariable UUID id) {
        return productService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<ProductDto> create(@Valid @RequestBody CreateProductRequest req,
                                              @AuthenticationPrincipal UserDetailsImpl principal) {
        ProductDto dto = productService.create(req, principal.getId());
        return ResponseEntity.created(URI.create("/api/products/" + dto.id())).body(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ProductDto update(@PathVariable UUID id, @Valid @RequestBody CreateProductRequest req,
                             @AuthenticationPrincipal UserDetailsImpl principal) {
        return productService.update(id, req, principal.getId());
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk-assign-tenant")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @RequiresLicense(LicensedPackage.MULTI_TENANT)
    public BulkAssignResultDto bulkAssignTenant(@Valid @RequestBody BulkAssignTenantRequest req,
                                                @AuthenticationPrincipal UserDetailsImpl principal) {
        return productService.bulkAssignTenant(req, principal.getId());
    }
}
