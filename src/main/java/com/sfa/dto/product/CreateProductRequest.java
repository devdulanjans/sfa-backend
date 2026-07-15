package com.sfa.dto.product;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateProductRequest(
        @NotBlank @Size(max = 30) String productCode,
        @Size(max = 64) String barcode,
        @NotBlank @Size(max = 200) String name,
        String description,
        UUID categoryId,
        UUID unitId,
        @NotNull @DecimalMin("0") BigDecimal defaultPrice,
        @NotNull @DecimalMin("0") BigDecimal purchasePrice,
        @DecimalMin("0") @DecimalMax("100") BigDecimal taxRate,
        @DecimalMin("0") BigDecimal maxDiscountAmount
) {}
