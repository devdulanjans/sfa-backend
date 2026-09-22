package com.sfa.controller;

import com.sfa.dto.user.AdminResetPasswordRequest;
import com.sfa.dto.user.CreateUserRequest;
import com.sfa.dto.user.UpdateUserRequest;
import com.sfa.dto.user.UserDto;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;
import java.util.UUID;

// ADMIN is channel-scoped (see UserService's rejectOutOfScope*/actorTenantIds helpers) —
// it only ever sees/manages users sharing one of its own channels, and can't touch or
// create SUPER_ADMIN/PLATFORM_OWNER/other ADMIN accounts. Only SUPER_ADMIN sees everyone.
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public Page<UserDto> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    UUID distributorId,
            @AuthenticationPrincipal UserDetailsImpl principal) {
        return userService.list(distributorId, principal.getId(), PageRequest.of(page, size, Sort.by("username")));
    }

    @GetMapping("/{id}")
    public UserDto getById(@PathVariable UUID id) {
        return userService.getById(id);
    }

    @GetMapping("/by-customer/{customerId}")
    public ResponseEntity<UserDto> findByCustomer(@PathVariable UUID customerId) {
        return userService.findByCustomerId(customerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Read-only reveal of the 'platformowner' / 'superadmin' recovery accounts —
    // never returned by list(), never editable through this or any other endpoint.
    @GetMapping("/recovery")
    public List<UserDto> recoveryAccounts(@RequestParam String key) {
        return userService.getRecoveryAccounts(key);
    }

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest req,
                                           @AuthenticationPrincipal UserDetailsImpl principal) {
        UserDto dto = userService.create(req, principal.getId());
        return ResponseEntity.created(URI.create("/api/users/" + dto.id())).body(dto);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req,
                           @AuthenticationPrincipal UserDetailsImpl principal) {
        return userService.update(id, req, principal.getId());
    }

    @PostMapping("/{id}/reset-password")
    @Operation(summary = "Reset another user's password (SUPER_ADMIN/ADMIN only; can't target your own account)")
    public ResponseEntity<Void> resetPassword(@PathVariable UUID id, @Valid @RequestBody AdminResetPasswordRequest req,
                                               @AuthenticationPrincipal UserDetailsImpl principal) {
        userService.resetPassword(id, req, principal.getId());
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/toggle-status")
    public UserDto toggleStatus(@PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl principal) {
        return userService.toggleStatus(id, principal.getId());
    }

    @PatchMapping("/{id}/role")
    public UserDto changeRole(@PathVariable UUID id, @RequestParam UUID roleId,
                               @AuthenticationPrincipal UserDetailsImpl principal) {
        return userService.changeRole(id, roleId, principal.getId());
    }
}
