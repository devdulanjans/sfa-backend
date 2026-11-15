package com.sfa.dto.auth;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

/**
 * @param username     username, email, or phone number
 * @param app          optional: which app is signing in — ADMIN, MOBILE or CUSTOMER. Picks a
 *                     workspace (membership) whose role suits that app. Omitted by older clients.
 * @param membershipId optional: sign straight into this workspace
 */
public record LoginRequest(
    @NotBlank(message = "Username is required") String username,
    @NotBlank(message = "Password is required")  String password,
    String app,
    UUID membershipId
) {
    public LoginRequest(String username, String password) {
        this(username, password, null, null);
    }
}
