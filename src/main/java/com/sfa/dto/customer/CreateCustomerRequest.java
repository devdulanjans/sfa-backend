package com.sfa.dto.customer;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record CreateCustomerRequest(
        @NotBlank @Size(max = 30) String customerCode,
        @NotBlank @Size(max = 200) String name,
        String contactPerson,
        String phone,
        String email,
        @Size(max = 100) String location,
        @Size(max = 150) String placeOfSupplier,
        String taxNumber,
        String taxType,
        BigDecimal taxRate,
        java.util.UUID categoryId,
        String visibilityRule,
        BigDecimal creditLimit,
        Integer creditDays,
        String source,
        @NotEmpty @Valid List<AddressRequest> addresses,
        java.util.UUID parentCustomerId
) {}
