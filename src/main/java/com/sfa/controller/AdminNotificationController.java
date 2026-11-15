package com.sfa.controller;

import com.sfa.security.UserDetailsImpl;
import com.sfa.service.AdminNotificationService;
import com.sfa.service.AdminNotificationService.Feed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** The admin panel's notification bell (V122): the active channel's alerts for owners and managers. */
@RestController
@RequestMapping("/api/admin-notifications")
@RequiredArgsConstructor
@Tag(name = "Admin Notifications")
@SecurityRequirement(name = "bearerAuth")
@PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','FINANCE_USER')")
public class AdminNotificationController {

    private final AdminNotificationService service;

    @GetMapping
    @Operation(summary = "Latest alerts and the unread count")
    public Feed feed(@RequestParam(defaultValue = "30") int limit, @AuthenticationPrincipal UserDetailsImpl user) {
        return service.feed(user.getId(), limit);
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> read(@PathVariable UUID id, @AuthenticationPrincipal UserDetailsImpl user) {
        service.markRead(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> readAll(@AuthenticationPrincipal UserDetailsImpl user) {
        service.markAllRead(user.getId());
        return ResponseEntity.noContent().build();
    }
}
