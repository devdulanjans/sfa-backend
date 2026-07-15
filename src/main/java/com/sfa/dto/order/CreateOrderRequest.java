package com.sfa.dto.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record CreateOrderRequest(
    @NotNull(message = "Customer is required") UUID customerId,
    @NotEmpty(message = "Order must have at least one item")
    @Valid List<OrderItemRequest> items,
    String notes,
    UUID distributorId,
    String deliveryAddressLabel,
    String deliveryAddressLine,
    String customerSignature,
    String salespersonSignature
) {}
