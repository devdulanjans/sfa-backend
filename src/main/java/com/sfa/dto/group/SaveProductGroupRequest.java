package com.sfa.dto.group;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record SaveProductGroupRequest(
        @NotBlank @Size(max = 100) String name,
        String description,
        List<UUID> memberIds
) {}
