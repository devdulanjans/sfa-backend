package com.sfa.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Self-service — the acting user changes their OWN password. Requires the current
 *  password so a hijacked session (or anyone at an unlocked terminal) can't silently
 *  lock the real owner out. */
public record ChangePasswordRequest(
        @NotBlank String currentPassword,
        @NotBlank @Size(min = 8) String newPassword
) {}
