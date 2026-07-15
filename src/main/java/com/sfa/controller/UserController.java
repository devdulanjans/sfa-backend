package com.sfa.controller;

import com.sfa.dto.user.CreateUserRequest;
import com.sfa.dto.user.UpdateUserRequest;
import com.sfa.dto.user.UserDto;
import com.sfa.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class UserController {

    private final UserService userService;

    @GetMapping
    public Page<UserDto> list(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false)    UUID distributorId) {
        return userService.list(distributorId, PageRequest.of(page, size, Sort.by("username")));
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

    @PostMapping
    public ResponseEntity<UserDto> create(@Valid @RequestBody CreateUserRequest req) {
        UserDto dto = userService.create(req);
        return ResponseEntity.created(URI.create("/api/users/" + dto.id())).body(dto);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUserRequest req) {
        return userService.update(id, req);
    }

    @PatchMapping("/{id}/toggle-status")
    public UserDto toggleStatus(@PathVariable UUID id) {
        return userService.toggleStatus(id);
    }

    @PatchMapping("/{id}/role")
    public UserDto changeRole(@PathVariable UUID id, @RequestParam UUID roleId) {
        return userService.changeRole(id, roleId);
    }
}
