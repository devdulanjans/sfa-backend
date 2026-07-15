package com.sfa.dto.ret;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

/**
 * orderId is optional — when present, the return's customer is derived from the order
 * (customerId is ignored in that case); when absent, customerId is required. See
 * ReturnService.create() for the enforcement of "orderId or customerId, at least one".
 */
public record CreateReturnRequest(
        UUID orderId,
        UUID customerId,
        @NotEmpty @Valid List<ReturnItemRequest> items,
        @NotBlank String reason
) {}
