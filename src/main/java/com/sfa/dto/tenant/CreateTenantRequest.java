package com.sfa.dto.tenant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateTenantRequest(
        @NotBlank @Size(max = 30)  String code,
        @NotBlank @Size(max = 150) String name
) {}
