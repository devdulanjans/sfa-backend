package com.sfa.controller;

import com.sfa.dto.product.CreateProductRequest;
import com.sfa.dto.product.ProductDto;
import com.sfa.dto.product.ProductImportResultDto;
import com.sfa.service.ProductImportService;
import com.sfa.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;
    private final ProductImportService productImportService;

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

    @GetMapping("/{id}")
    public ProductDto getById(@PathVariable UUID id) {
        return productService.getById(id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<ProductDto> create(@Valid @RequestBody CreateProductRequest req) {
        ProductDto dto = productService.create(req);
        return ResponseEntity.created(URI.create("/api/products/" + dto.id())).body(dto);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ProductDto update(@PathVariable UUID id, @Valid @RequestBody CreateProductRequest req) {
        return productService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> deactivate(@PathVariable UUID id) {
        productService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/import-template")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<byte[]> downloadImportTemplate() throws IOException {
        byte[] bytes = productImportService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"product-import-template.xlsx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ProductImportResultDto importProducts(@RequestParam("file") MultipartFile file) throws IOException {
        return productImportService.importFromExcel(file);
    }
}
