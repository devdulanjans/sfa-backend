package com.sfa.controller;

import com.sfa.dto.group.ProductGroupDto;
import com.sfa.dto.group.SaveProductGroupRequest;
import com.sfa.entity.Product;
import com.sfa.entity.ProductGroup;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.BatchPriceRepository;
import com.sfa.repository.ProductGroupRepository;
import com.sfa.repository.ProductRepository;
import com.sfa.repository.PromotionRepository;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping("/api/product-groups")
@RequiredArgsConstructor
public class ProductGroupController {

    private final ProductGroupRepository productGroupRepository;
    private final ProductRepository      productRepository;
    private final BatchPriceRepository   batchPriceRepository;
    private final PromotionRepository    promotionRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Page<ProductGroupDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ProductGroup> groups = productGroupRepository.findAll(
                PageRequest.of(page, size, Sort.by("name")));

        List<UUID> groupIds = groups.getContent().stream().map(ProductGroup::getId).toList();
        Map<UUID, Long> countsByGroup = new HashMap<>();
        if (!groupIds.isEmpty()) {
            for (var row : productGroupRepository.countMembersForGroups(groupIds)) {
                countsByGroup.put(row.getGroupId(), row.getMemberCount());
            }
        }
        return groups.map(g -> ProductGroupDto.summary(g, countsByGroup.getOrDefault(g.getId(), 0L)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ProductGroupDto get(@PathVariable UUID id) {
        return ProductGroupDto.withMembers(findOrThrow(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<ProductGroupDto> create(@Valid @RequestBody SaveProductGroupRequest req) {
        ProductGroup g = new ProductGroup();
        applyFields(g, req);
        ProductGroup saved = productGroupRepository.save(g);
        return ResponseEntity.created(URI.create("/api/product-groups/" + saved.getId()))
                .body(ProductGroupDto.withMembers(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ProductGroupDto update(@PathVariable UUID id, @Valid @RequestBody SaveProductGroupRequest req) {
        ProductGroup g = findOrThrow(id);
        applyFields(g, req);
        return ProductGroupDto.withMembers(productGroupRepository.save(g));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        findOrThrow(id);
        if (batchPriceRepository.existsByProductGroupId(id) || promotionRepository.existsByProductGroupId(id)) {
            throw new BusinessException("Cannot delete: this product group is still used by a batch price or promotion rule");
        }
        productGroupRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    /** Groups currently containing this product — backs the reverse "Product Groups"
     *  checklist on the product edit page. */
    @GetMapping("/by-product/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public List<GroupRef> byProduct(@PathVariable UUID productId) {
        return productGroupRepository.findAllContainingProduct(productId).stream()
                .map(g -> new GroupRef(g.getId(), g.getName()))
                .toList();
    }

    /** Sets this product's membership across all product groups to exactly the given set —
     *  the necessary inverse of create/update's full-replace-from-the-group's-side, since
     *  ProductGroup owns the join table and there's no per-product edit path otherwise. */
    @PutMapping("/by-product/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public void setForProduct(@PathVariable UUID productId, @RequestBody SetGroupsForProductRequest req) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product", productId));
        Set<UUID> desiredGroupIds = req.groupIds() != null ? new HashSet<>(req.groupIds()) : Set.of();

        List<ProductGroup> currentGroups = productGroupRepository.findAllContainingProduct(productId);
        for (ProductGroup g : currentGroups) {
            if (!desiredGroupIds.contains(g.getId())) {
                g.getMembers().remove(product);
                productGroupRepository.save(g);
            }
        }

        Set<UUID> alreadyIn = currentGroups.stream().map(ProductGroup::getId)
                .filter(desiredGroupIds::contains).collect(java.util.stream.Collectors.toSet());
        for (UUID groupId : desiredGroupIds) {
            if (alreadyIn.contains(groupId)) continue;
            ProductGroup g = findOrThrow(groupId);
            g.getMembers().add(product);
            productGroupRepository.save(g);
        }
    }

    public record GroupRef(UUID id, String name) {}
    public record SetGroupsForProductRequest(List<UUID> groupIds) {}

    private void applyFields(ProductGroup g, SaveProductGroupRequest req) {
        g.setName(req.name());
        g.setDescription(req.description());
        List<UUID> memberIds = req.memberIds() != null ? req.memberIds() : List.of();
        Set<Product> members = new HashSet<>(productRepository.findAllById(memberIds));
        g.setMembers(members);
    }

    private ProductGroup findOrThrow(UUID id) {
        return productGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("ProductGroup", id));
    }
}
