package com.sfa.dto.lookup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveCategoryRequest(
    @NotBlank @Size(max = 100) String name,
    String description,
    @Size(max = 10) String code
) {}
