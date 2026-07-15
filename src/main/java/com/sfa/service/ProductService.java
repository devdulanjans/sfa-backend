package com.sfa.service;

import com.sfa.dto.product.CreateProductRequest;
import com.sfa.dto.product.ProductDto;
import com.sfa.entity.Product;
import com.sfa.entity.ProductCategory;
import com.sfa.entity.StockLevel;
import com.sfa.entity.Unit;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.CustomerRepository;
import com.sfa.repository.ProductCategoryRepository;
import com.sfa.repository.ProductRepository;
import com.sfa.repository.StockLevelRepository;
import com.sfa.repository.UnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final ProductCategoryRepository categoryRepository;
    private final UnitRepository unitRepository;
    private final CustomerRepository customerRepository;
    private final StockLevelRepository stockLevelRepository;
    private final AuditLogService auditLogService;

    public Page<ProductDto> list(String search, Pageable pageable) {
        Page<Product> page = productRepository.search(search == null ? "" : search, pageable);

        List<UUID> ids = page.getContent().stream().map(Product::getId).toList();
        Map<UUID, StockLevel> stockByProductId = stockLevelRepository.findByProductIdIn(ids).stream()
                .collect(Collectors.toMap(StockLevel::getProductId, s -> s));

        return page.map(p -> ProductDto.from(p, stockByProductId.get(p.getId())));
    }

    @Transactional(readOnly = true)
    public List<ProductDto> visibleToCustomer(UUID customerId) {
        if (!customerRepository.existsById(customerId)) {
            throw new ResourceNotFoundException("Customer", customerId);
        }

        // Assignment itself is the signal that restricts the catalog — not the
        // visibilityRule flag, which can drift out of sync with the actual
        // assignment list (e.g. products assigned without also flipping the
        // flag). A customer with no assigned products always sees the full
        // active catalog; any assignment narrows it to just those products.
        List<Product> assigned = productRepository.findAssignedToCustomer(customerId);
        List<Product> products = assigned.isEmpty()
                ? productRepository.findByStatusOrderByName(Product.ProductStatus.ACTIVE)
                : assigned;

        return products.stream().map(ProductDto::from).toList();
    }

    public ProductDto getById(UUID id) {
        return ProductDto.from(findOrThrow(id));
    }

    @Transactional
    public ProductDto create(CreateProductRequest req) {
        if (productRepository.findByProductCode(req.productCode()).isPresent()) {
            throw new BusinessException("Product code already exists: " + req.productCode());
        }
        checkBarcodeUnique(req.barcode(), null);
        Product p = new Product();
        applyFields(p, req);
        Product saved = productRepository.save(p);
        auditLogService.log(null, "CREATE", "Product", saved.getId(), null, saved);
        return ProductDto.from(saved);
    }

    @Transactional
    public ProductDto update(UUID id, CreateProductRequest req) {
        Product p = findOrThrow(id);
        checkBarcodeUnique(req.barcode(), id);
        Object before = ProductDto.from(p);
        applyFields(p, req);
        Product saved = productRepository.save(p);
        auditLogService.log(null, "UPDATE", "Product", saved.getId(), before, ProductDto.from(saved));
        return ProductDto.from(saved);
    }

    private void checkBarcodeUnique(String barcode, UUID excludeId) {
        if (barcode == null || barcode.isBlank()) return;
        productRepository.findByBarcode(barcode.trim()).ifPresent(existing -> {
            if (!existing.getId().equals(excludeId)) {
                throw new BusinessException("Barcode already assigned to another product: " + barcode);
            }
        });
    }

    @Transactional
    public void deactivate(UUID id) {
        Product p = findOrThrow(id);
        p.setStatus(Product.ProductStatus.INACTIVE);
        productRepository.save(p);
    }

    private void applyFields(Product p, CreateProductRequest req) {
        if (req.maxDiscountAmount() != null && req.maxDiscountAmount().compareTo(req.defaultPrice()) >= 0) {
            throw new BusinessException("Max discount must be less than the product's selling price");
        }

        p.setProductCode(req.productCode());
        p.setBarcode(req.barcode() != null && !req.barcode().isBlank() ? req.barcode().trim() : null);
        p.setName(req.name());
        p.setDescription(req.description());
        if (req.categoryId() != null) {
            ProductCategory cat = categoryRepository.findById(req.categoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("ProductCategory", req.categoryId()));
            p.setCategory(cat);
        }
        if (req.unitId() != null) {
            Unit unit = unitRepository.findById(req.unitId())
                    .orElseThrow(() -> new ResourceNotFoundException("Unit", req.unitId()));
            p.setUnit(unit);
        }
        p.setDefaultPrice(req.defaultPrice());
        p.setPurchasePrice(req.purchasePrice());
        p.setTaxRate(req.taxRate());
        p.setMaxDiscountAmount(req.maxDiscountAmount());
    }

    private Product findOrThrow(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", id));
    }
}
