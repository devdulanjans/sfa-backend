package com.sfa.controller;

import com.sfa.entity.StockBatch;
import com.sfa.entity.StockLevel;
import com.sfa.entity.StockMovement;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.repository.ProductRepository;
import com.sfa.repository.StockLevelRepository;
import com.sfa.service.InventoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import com.sfa.security.UserDetailsImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/inventory")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.SFA)
public class InventoryController {

    private final InventoryService     inventoryService;
    private final StockLevelRepository stockLevelRepo;
    private final ProductRepository    productRepo;

    // ── Stock levels ──────────────────────────────────────────────────────────

    @GetMapping("/stock")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Page<StockLevelDto> listStock(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String search) {

        var pageable = PageRequest.of(page, size, Sort.by("name"));
        var productPage = (search != null && !search.isBlank())
                ? productRepo.search(search, pageable)
                : productRepo.findAll(pageable);

        List<UUID> ids = productPage.getContent().stream().map(p -> p.getId()).toList();
        Map<UUID, StockLevel> stockMap = stockLevelRepo.findByProductIdIn(ids)
                .stream().collect(Collectors.toMap(StockLevel::getProductId, s -> s));

        List<StockLevelDto> dtos = productPage.getContent().stream().map(p -> {
            StockLevel s = stockMap.get(p.getId());
            return new StockLevelDto(
                    p.getId(), p.getName(), p.getProductCode(),
                    s != null ? s.getOnHand() : BigDecimal.ZERO,
                    s != null ? s.getReserved() : BigDecimal.ZERO,
                    s != null ? s.getUpdatedAt() : null);
        }).toList();

        return new PageImpl<>(dtos, pageable, productPage.getTotalElements());
    }

    @GetMapping("/stock/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public StockLevelDto getStock(@PathVariable UUID productId) {
        var product = productRepo.findById(productId)
                .orElseThrow(() -> new com.sfa.exception.ResourceNotFoundException("Product", productId));
        StockLevel s = inventoryService.getStock(productId);
        return new StockLevelDto(product.getId(), product.getName(), product.getProductCode(),
                s.getOnHand(), s.getReserved(), s.getUpdatedAt());
    }

    @PostMapping("/stock/adjust")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<StockLevelDto> adjustStock(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID productId = UUID.fromString((String) body.get("productId"));
        BigDecimal qty = new BigDecimal(body.get("quantity").toString());
        String notes   = (String) body.getOrDefault("notes", "");
        UUID userId    = principal.getId();

        StockLevel updated = inventoryService.adjust(productId, qty, notes, userId);
        var product = productRepo.findById(productId).orElseThrow();
        return ResponseEntity.ok(new StockLevelDto(product.getId(), product.getName(),
                product.getProductCode(), updated.getOnHand(), updated.getReserved(), updated.getUpdatedAt()));
    }

    @PostMapping("/stock/receive")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public ResponseEntity<StockLevelDto> receiveStock(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID productId          = UUID.fromString((String) body.get("productId"));
        BigDecimal quantity     = new BigDecimal(body.get("quantity").toString());
        BigDecimal unitCost     = new BigDecimal(body.get("unitCost").toString());
        LocalDate receivedDate  = LocalDate.parse((String) body.get("receivedDate"));
        String notes            = (String) body.getOrDefault("notes", "");

        StockLevel updated = inventoryService.receiveStock(
                productId, quantity, unitCost, receivedDate, notes, principal.getId());
        var product = productRepo.findById(productId).orElseThrow();
        return ResponseEntity.ok(new StockLevelDto(product.getId(), product.getName(),
                product.getProductCode(), updated.getOnHand(), updated.getReserved(), updated.getUpdatedAt()));
    }

    @GetMapping("/batches/{productId}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public List<StockBatchDto> listBatches(@PathVariable UUID productId) {
        return inventoryService.listBatches(productId).stream().map(StockBatchDto::from).toList();
    }

    // ── Movements ─────────────────────────────────────────────────────────────

    @GetMapping("/movements")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public Page<StockMovementDto> listMovements(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String productId,
            @RequestParam(required = false)    String type) {

        UUID pid = productId != null && !productId.isBlank() ? UUID.fromString(productId) : null;
        return inventoryService.getMovements(pid, type, page, size)
                .map(StockMovementDto::from);
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record StockLevelDto(
            UUID id, String name, String productCode,
            BigDecimal onHand, BigDecimal reserved, Instant updatedAt) {}

    public record StockBatchDto(
            UUID id, BigDecimal receivedQty, BigDecimal remainingQty, BigDecimal unitCost,
            LocalDate receivedDate, String notes, Instant createdAt) {

        static StockBatchDto from(StockBatch b) {
            return new StockBatchDto(b.getId(), b.getReceivedQty(), b.getRemainingQty(), b.getUnitCost(),
                    b.getReceivedDate(), b.getNotes(), b.getCreatedAt());
        }
    }

    public record StockMovementDto(
            UUID id, UUID productId, String productName,
            String type, BigDecimal quantity, BigDecimal balanceAfter, BigDecimal totalCost,
            String referenceType, UUID referenceId, String notes, Instant createdAt) {

        static StockMovementDto from(StockMovement m) {
            String productName = m.getProduct() != null ? m.getProduct().getName() : null;
            return new StockMovementDto(m.getId(), m.getProductId(), productName,
                    m.getType().name(), m.getQuantity(), m.getBalanceAfter(), m.getTotalCost(),
                    m.getReferenceType(), m.getReferenceId(), m.getNotes(), m.getCreatedAt());
        }
    }
}
