package com.sfa.controller;

import com.sfa.dto.lookup.SaveCategoryRequest;
import com.sfa.dto.lookup.SaveReasonRequest;
import com.sfa.dto.lookup.SaveUnitRequest;
import com.sfa.entity.CustomerCategory;
import com.sfa.entity.ProductCategory;
import com.sfa.entity.Reason;
import com.sfa.entity.Unit;
import com.sfa.exception.BusinessException;
import com.sfa.repository.CustomerCategoryRepository;
import com.sfa.repository.ProductCategoryRepository;
import com.sfa.repository.ReasonRepository;
import com.sfa.repository.UnitRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
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
    private final ReasonRepository reasonRepo;

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

    // ── Damage/Return reasons — admin-managed, synced to the mobile app so the
    //    damage/return forms' reason dropdowns no longer need a client-side
    //    hardcoded list (see ReasonRepository, migration V89__reasons.sql). ────

    @GetMapping("/api/reasons")
    public List<Reason> reasons(@RequestParam(required = false) Reason.ReasonType type) {
        return type != null
                ? reasonRepo.findByTypeOrderBySortOrderAscLabelAsc(type)
                : reasonRepo.findAll(Sort.by(Sort.Order.asc("type"), Sort.Order.asc("sortOrder")));
    }

    @PostMapping("/api/reasons")
    @ResponseStatus(HttpStatus.CREATED)
    public Reason createReason(@RequestBody @Valid SaveReasonRequest req) {
        if (reasonRepo.existsByTypeAndLabelIgnoreCase(req.type(), req.label())) {
            throw new BusinessException("This reason already exists for " + req.type());
        }
        Reason reason = new Reason();
        reason.setType(req.type());
        reason.setLabel(req.label());
        reason.setAllowFreeText(Boolean.TRUE.equals(req.allowFreeText()));
        reason.setSortOrder(req.sortOrder() != null
                ? req.sortOrder()
                : reasonRepo.findByTypeOrderBySortOrderAscLabelAsc(req.type()).size());
        return reasonRepo.save(reason);
    }

    @PutMapping("/api/reasons/{id}")
    public Reason updateReason(@PathVariable UUID id, @RequestBody @Valid SaveReasonRequest req) {
        Reason reason = reasonRepo.findById(id)
                .orElseThrow(() -> new BusinessException("Reason not found"));
        reason.setLabel(req.label());
        reason.setAllowFreeText(Boolean.TRUE.equals(req.allowFreeText()));
        if (req.sortOrder() != null) {
            reason.setSortOrder(req.sortOrder());
        }
        return reasonRepo.save(reason);
    }

    @DeleteMapping("/api/reasons/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteReason(@PathVariable UUID id) {
        if (!reasonRepo.existsById(id)) {
            throw new BusinessException("Reason not found");
        }
        reasonRepo.deleteById(id);
    }
}
