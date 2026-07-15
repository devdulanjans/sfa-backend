package com.sfa.dto.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record QuickCreateCustomerRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 20) String phone
) {}
