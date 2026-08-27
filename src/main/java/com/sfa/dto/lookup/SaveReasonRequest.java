package com.sfa.dto.lookup;

import com.sfa.entity.Reason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SaveReasonRequest(
    @NotNull Reason.ReasonType type,
    @NotBlank @Size(max = 200) String label,
    Boolean allowFreeText,
    Integer sortOrder
) {}
