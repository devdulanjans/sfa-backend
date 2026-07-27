package com.sfa.controller;

import com.sfa.dto.group.CustomerGroupDto;
import com.sfa.dto.group.SaveCustomerGroupRequest;
import com.sfa.entity.Customer;
import com.sfa.entity.CustomerGroup;
import com.sfa.entity.Product;
import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.BatchPriceRepository;
import com.sfa.repository.CustomerGroupRepository;
import com.sfa.repository.CustomerRepository;
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
@RequestMapping("/api/customer-groups")
@RequiredArgsConstructor
public class CustomerGroupController {

    private final CustomerGroupRepository customerGroupRepository;
    private final CustomerRepository      customerRepository;
    private final ProductRepository       productRepository;
    private final BatchPriceRepository    batchPriceRepository;
    private final PromotionRepository     promotionRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Page<CustomerGroupDto> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<CustomerGroup> groups = customerGroupRepository.findAll(
                PageRequest.of(page, size, Sort.by("name")));

        List<UUID> groupIds = groups.getContent().stream().map(CustomerGroup::getId).toList();
        Map<UUID, Long> memberCounts = new HashMap<>();
        Map<UUID, Long> productCounts = new HashMap<>();
        if (!groupIds.isEmpty()) {
            for (var row : customerGroupRepository.countMembersForGroups(groupIds)) {
                memberCounts.put(row.getGroupId(), row.getMemberCount());
            }
            for (var row : customerGroupRepository.countAssignedProductsForGroups(groupIds)) {
                productCounts.put(row.getGroupId(), row.getProductCount());
            }
        }
        return groups.map(g -> CustomerGroupDto.summary(
                g, memberCounts.getOrDefault(g.getId(), 0L), productCounts.getOrDefault(g.getId(), 0L)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public CustomerGroupDto get(@PathVariable UUID id) {
        return CustomerGroupDto.withMembers(findOrThrow(id));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<CustomerGroupDto> create(@Valid @RequestBody SaveCustomerGroupRequest req) {
        CustomerGroup g = new CustomerGroup();
        applyFields(g, req);
        CustomerGroup saved = customerGroupRepository.save(g);
        return ResponseEntity.created(URI.create("/api/customer-groups/" + saved.getId()))
                .body(CustomerGroupDto.withMembers(saved));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public CustomerGroupDto update(@PathVariable UUID id, @Valid @RequestBody SaveCustomerGroupRequest req) {
        CustomerGroup g = findOrThrow(id);
        applyFields(g, req);
        return CustomerGroupDto.withMembers(customerGroupRepository.save(g));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        findOrThrow(id);
        if (batchPriceRepository.existsByCustomerGroupId(id) || promotionRepository.existsByCustomerGroupId(id)) {
            throw new BusinessException("Cannot delete: this customer group is still used by a batch price or promotion rule");
        }
        customerGroupRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void applyFields(CustomerGroup g, SaveCustomerGroupRequest req) {
        g.setName(req.name());
        g.setDescription(req.description());
        List<UUID> memberIds = req.memberIds() != null ? req.memberIds() : List.of();
        Set<Customer> members = new HashSet<>(customerRepository.findAllById(memberIds));
        g.setMembers(members);
        List<UUID> productIds = req.productIds() != null ? req.productIds() : List.of();
        Set<Product> products = new HashSet<>(productRepository.findAllById(productIds));
        g.setAssignedProducts(products);
    }

    private CustomerGroup findOrThrow(UUID id) {
        return customerGroupRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("CustomerGroup", id));
    }
}
