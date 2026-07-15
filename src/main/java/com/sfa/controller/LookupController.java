package com.sfa.controller;

import com.sfa.dto.lookup.SaveCategoryRequest;
import com.sfa.dto.lookup.SaveUnitRequest;
import com.sfa.entity.CustomerCategory;
import com.sfa.entity.ProductCategory;
import com.sfa.entity.Unit;
import com.sfa.exception.BusinessException;
import com.sfa.repository.CustomerCategoryRepository;
import com.sfa.repository.ProductCategoryRepository;
import com.sfa.repository.UnitRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LookupController {

    private final ProductCategoryRepository productCategoryRepo;
    private final CustomerCategoryRepository customerCategoryRepo;
    private final UnitRepository unitRepo;

    @GetMapping("/api/product-categories")
    public List<ProductCategory> productCategories() {
        return productCategoryRepo.findAll();
    }

    @PostMapping("/api/product-categories")
    @ResponseStatus(HttpStatus.CREATED)
    public ProductCategory createProductCategory(@RequestBody @Valid SaveCategoryRequest req) {
        ProductCategory cat = new ProductCategory();
        cat.setName(req.name());
        cat.setDescription(req.description());
        cat.setCode(req.code());
        return productCategoryRepo.save(cat);
    }

    @PutMapping("/api/product-categories/{id}")
    public ProductCategory updateProductCategory(@PathVariable UUID id,
                                                  @RequestBody @Valid SaveCategoryRequest req) {
        ProductCategory cat = productCategoryRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Category not found"));
        cat.setName(req.name());
        cat.setDescription(req.description());
        cat.setCode(req.code());
        return productCategoryRepo.save(cat);
    }

    @DeleteMapping("/api/product-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteProductCategory(@PathVariable UUID id) {
        if (!productCategoryRepo.existsById(id)) {
            throw new BusinessException("Category not found");
        }
        productCategoryRepo.deleteById(id);
    }

    @GetMapping("/api/customer-categories")
    public List<CustomerCategory> customerCategories() {
        return customerCategoryRepo.findAll();
    }

    @GetMapping("/api/units")
    public List<Unit> units() {
        return unitRepo.findAll();
    }

    @PostMapping("/api/units")
    @ResponseStatus(HttpStatus.CREATED)
    public Unit createUnit(@RequestBody @Valid SaveUnitRequest req) {
        if (unitRepo.existsByName(req.name())) {
            throw new BusinessException("Unit name already exists: " + req.name());
        }
        Unit unit = new Unit();
        unit.setName(req.name());
        unit.setAbbreviation(req.abbreviation());
        return unitRepo.save(unit);
    }

    @PutMapping("/api/units/{id}")
    public Unit updateUnit(@PathVariable UUID id, @RequestBody @Valid SaveUnitRequest req) {
        Unit unit = unitRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Unit not found"));
        unit.setName(req.name());
        unit.setAbbreviation(req.abbreviation());
        return unitRepo.save(unit);
    }

    @DeleteMapping("/api/units/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUnit(@PathVariable UUID id) {
        if (!unitRepo.existsById(id)) {
            throw new BusinessException("Unit not found");
        }
        unitRepo.deleteById(id);
    }
}
