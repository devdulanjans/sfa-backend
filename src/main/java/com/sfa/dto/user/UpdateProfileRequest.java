package com.sfa.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record UpdateProfileRequest(
        @NotBlank String fullName,
        @NotBlank @Email String email
) {}
