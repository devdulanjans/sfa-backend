package com.sfa.controller;

import com.sfa.dto.auth.LoginRequest;
import com.sfa.dto.auth.LoginResponse;
import com.sfa.dto.auth.RefreshRequest;
import com.sfa.dto.auth.SwitchTenantRequest;
import com.sfa.dto.user.ChangePasswordRequest;
import com.sfa.license.LicensedPackage;
import com.sfa.license.RequiresLicense;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.AuthService;
import com.sfa.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "JWT login, refresh, and logout endpoints")
public class AuthController {

    private final AuthService authService;
    private final UserService userService;

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/switch-tenant")
    @Operation(summary = "Switch the active channel for this session")
    @RequiresLicense(LicensedPackage.MULTI_TENANT)
    public ResponseEntity<LoginResponse> switchTenant(
            @RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody SwitchTenantRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        String token = bearerToken.substring(7);
        return ResponseEntity.ok(authService.switchTenant(request.tenantId(), token, principal.getId()));
    }

    @PostMapping("/change-password")
    @Operation(summary = "Change your own password (requires current password)")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        userService.changeOwnPassword(principal.getId(), request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/logout")
    @Operation(summary = "Logout and invalidate tokens")
    public ResponseEntity<Void> logout(
            @RequestHeader("Authorization") String bearerToken,
            @AuthenticationPrincipal UserDetails user) {
        String token = bearerToken.substring(7);
        authService.logout(token, user.getUsername());
        return ResponseEntity.noContent().build();
    }
}
