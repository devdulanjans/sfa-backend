package com.sfa.dto.lookup;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SaveUnitRequest(
        @NotBlank @Size(max = 50) String name,
        @Size(max = 10) String abbreviation
) {}
