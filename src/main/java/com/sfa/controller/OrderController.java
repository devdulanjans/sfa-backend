package com.sfa.controller;

import com.sfa.dto.order.CreateOrderRequest;
import com.sfa.dto.order.OrderResponseDto;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders")
@SecurityRequirement(name = "bearerAuth")
@RequiresLicense(LicensedPackage.SFA)
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    @Operation(summary = "List orders (customer sees own; sales rep sees own; manager/admin sees all)")
    public ResponseEntity<Page<OrderResponseDto>> list(
            @AuthenticationPrincipal UserDetailsImpl user,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String invoiceNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 20, sort = "orderDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(
                orderService.getOrders(user.getId(), user.getLinkedCustomerId(), user.getRoleName(),
                        status, source, orderNo, invoiceNo, dateFrom, dateTo, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<OrderResponseDto> get(@PathVariable UUID id) {
        return ResponseEntity.ok(OrderResponseDto.from(orderService.getOrder(id)));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new order (DRAFT status)")
    public ResponseEntity<OrderResponseDto> create(
            @Valid @RequestBody CreateOrderRequest request,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(OrderResponseDto.from(orderService.createOrder(request, user.getId())));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a DRAFT order (sales rep / customer can only delete their own)")
    public ResponseEntity<Void> delete(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl user) {
        orderService.deleteDraftOrder(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/submit")
    @Operation(summary = "Submit order for manager approval")
    public ResponseEntity<OrderResponseDto> submit(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(OrderResponseDto.from(orderService.submitOrder(id, user.getId())));
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "Approve a submitted order")
    public ResponseEntity<OrderResponseDto> approve(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(OrderResponseDto.from(orderService.approveOrder(id, user.getId())));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER')")
    @Operation(summary = "Cancel an order")
    public ResponseEntity<OrderResponseDto> cancel(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(OrderResponseDto.from(orderService.cancelOrder(id, user.getId())));
    }
}
