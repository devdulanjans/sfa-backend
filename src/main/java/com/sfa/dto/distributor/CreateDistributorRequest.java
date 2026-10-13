package com.sfa.dto.distributor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

public record CreateDistributorRequest(
        @NotBlank @Size(max = 30)  String code,
        @NotBlank @Size(max = 200) String name,
        String address,
        @Size(max = 20)  String phone,
        @Size(max = 120) String email,
        /** Which channel(s) this distributor operates under — a distributor can belong to
         *  several at once (unlike Product/Customer's single tenant). Required on create;
         *  on update, an empty/null list leaves the existing channels untouched. */
        List<UUID> tenantIds
) {}
