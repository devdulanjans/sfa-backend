package com.sfa.dto.customer;

import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
        @NotBlank String label,
        @NotBlank String addressLine
) {}
