package com.sfa.dto.auth;

import com.sfa.dto.distributor.DistributorDto;

import java.util.List;
import java.util.UUID;

public record LoginResponse(
    String accessToken,
    String refreshToken,
    String username,
    String email,
    String fullName,
    String role,
    UUID customerId,
    List<DistributorDto> distributors,
    List<String> permissions,
    boolean sfaEnabled,
    boolean posEnabled
) {}
