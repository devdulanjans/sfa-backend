package com.sfa.dto.distributor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDistributorRequest(
        @NotBlank @Size(max = 30)  String code,
        @NotBlank @Size(max = 200) String name,
        String address,
        @Size(max = 20)  String phone,
        @Size(max = 120) String email
) {}
