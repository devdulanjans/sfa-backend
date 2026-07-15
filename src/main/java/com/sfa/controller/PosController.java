package com.sfa.controller;

import com.sfa.entity.PosSale;
import com.sfa.entity.PosSalePayment;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.service.PosService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import com.sfa.security.UserDetailsImpl;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/pos/sales")
@RequiredArgsConstructor
@RequiresLicense(LicensedPackage.POS)
public class PosController {

    private final PosService posService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PosSaleResponseDto> createSale(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        UUID userId = principal.getId();

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawItems = (List<Map<String, Object>>) body.get("items");

        List<PosService.PosItemRequest> items = rawItems.stream().map(i -> {
            Object pidObj = i.get("productId");
            UUID pid = (pidObj instanceof String s && !s.isBlank()) ? UUID.fromString(s) : null;
            return new PosService.PosItemRequest(
                    pid,
                    (String) i.get("productName"),
                    new BigDecimal(i.get("quantity").toString()),
                    new BigDecimal(i.get("unitPrice").toString()),
                    i.get("discountPct") != null ? new BigDecimal(i.get("discountPct").toString()) : BigDecimal.ZERO,
                    i.get("taxPct")      != null ? new BigDecimal(i.get("taxPct").toString())      : BigDecimal.ZERO
            );
        }).toList();

        Object custIdObj = body.get("customerId");
        UUID customerId  = custIdObj instanceof String s && !s.isBlank() ? UUID.fromString(s) : null;
        Object tenderedObj = body.get("amountTendered");
        Object paidObj     = body.get("amountPaid");

        var req = new PosService.CreatePosSaleRequest(
                customerId,
                (String) body.get("customerName"),
                (String) body.get("paymentMethod"),
                tenderedObj != null ? new BigDecimal(tenderedObj.toString()) : null,
                paidObj != null ? new BigDecimal(paidObj.toString()) : null,
                (String) body.get("cardLast4"),
                (String) body.get("notes"),
                items);

        PosSale saved = posService.createSale(req, userId);
        return ResponseEntity
                .created(URI.create("/api/pos/sales/" + saved.getId()))
                .body(PosSaleResponseDto.from(saved));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public Page<PosSaleResponseDto> listSales(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    String status) {
        return posService.listSales(status, page, size).map(PosSaleResponseDto::from);
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    public PosSaleResponseDto getSale(@PathVariable UUID id) {
        return PosSaleResponseDto.from(posService.getSale(id));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public PosSaleResponseDto voidSale(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        UUID userId = principal.getId();
        return PosSaleResponseDto.from(posService.voidSale(id, userId));
    }

    @PostMapping("/{id}/payments")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    public PosSaleResponseDto recordPayment(
            @PathVariable UUID id,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        BigDecimal amount = new BigDecimal(body.get("amount").toString());
        String method = (String) body.get("paymentMethod");
        String notes = (String) body.get("notes");
        return PosSaleResponseDto.from(posService.recordPaymentForSale(id, amount, method, notes, principal.getId()));
    }

    @GetMapping("/{id}/payments")
    @PreAuthorize("isAuthenticated()")
    public List<PosSalePaymentDto> getPayments(@PathVariable UUID id) {
        return posService.getPaymentsForSale(id).stream().map(PosSalePaymentDto::from).toList();
    }

    // ── DTOs ──────────────────────────────────────────────────────────────────

    public record PosSaleResponseDto(
            UUID id, String saleNumber,
            UUID customerId, String customerName,
            String paymentMethod, String status,
            BigDecimal subtotal, BigDecimal discountAmount, BigDecimal taxAmount, BigDecimal total,
            BigDecimal amountTendered, BigDecimal changeAmount, String cardLast4,
            BigDecimal amountPaid, BigDecimal balanceDue, String creditStatus,
            String notes, UUID createdBy,
            Instant createdAt, Instant updatedAt,
            List<ItemDto> items) {

        public record ItemDto(
                UUID id, UUID productId, String productName,
                BigDecimal quantity, BigDecimal unitPrice,
                BigDecimal discountPct, BigDecimal discountAmount,
                BigDecimal taxPct, BigDecimal taxAmount, BigDecimal lineTotal) {}

        static PosSaleResponseDto from(PosSale s) {
            List<ItemDto> itemDtos = s.getItems() == null ? List.of()
                    : s.getItems().stream().map(i -> new ItemDto(
                            i.getId(), i.getProductId(), i.getProductName(),
                            i.getQuantity(), i.getUnitPrice(),
                            i.getDiscountPct(), i.getDiscountAmount(),
                            i.getTaxPct(), i.getTaxAmount(), i.getLineTotal())).toList();

            return new PosSaleResponseDto(
                    s.getId(), s.getSaleNumber(),
                    s.getCustomer() != null ? s.getCustomer().getId() : null,
                    s.getCustomerName(),
                    s.getPaymentMethod().name(), s.getStatus().name(),
                    s.getSubtotal(), s.getDiscountAmount(), s.getTaxAmount(), s.getTotal(),
                    s.getAmountTendered(), s.getChangeAmount(), s.getCardLast4(),
                    s.getAmountPaid(), s.getBalanceDue(), s.getCreditStatus().name(),
                    s.getNotes(), s.getCreatedBy(),
                    s.getCreatedAt(), s.getUpdatedAt(),
                    itemDtos);
        }
    }

    public record PosSalePaymentDto(
            UUID id, UUID saleId, String saleNumber, UUID customerId, String customerName,
            BigDecimal amount, String paymentMethod, String paymentType, BigDecimal balanceAfter,
            String notes, UUID recordedBy, Instant createdAt) {

        static PosSalePaymentDto from(PosSalePayment p) {
            return new PosSalePaymentDto(
                    p.getId(),
                    p.getSale().getId(), p.getSale().getSaleNumber(),
                    p.getCustomer().getId(), p.getCustomer().getName(),
                    p.getAmount(), p.getPaymentMethod().name(), p.getPaymentType().name(), p.getBalanceAfter(),
                    p.getNotes(), p.getRecordedBy(), p.getCreatedAt());
        }
    }
}
