package com.sfa.controller;

import com.sfa.dto.auth.LoginRequest;
import com.sfa.dto.auth.LoginResponse;
import com.sfa.dto.auth.RefreshRequest;
import com.sfa.service.AuthService;
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

    @PostMapping("/login")
    @Operation(summary = "Login and receive JWT tokens")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        System.out.println("CALLED THIS");
        System.out.println(ResponseEntity.ok(authService.login(request)));
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Refresh access token")
    public ResponseEntity<LoginResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
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
