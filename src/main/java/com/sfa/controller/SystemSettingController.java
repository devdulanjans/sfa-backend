package com.sfa.controller;

import com.sfa.entity.SystemSetting;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.SystemSettingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "System Settings")
@SecurityRequirement(name = "bearerAuth")
public class SystemSettingController {

    private final SystemSettingService settingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_REP')")
    @Operation(summary = "Get all system settings")
    public ResponseEntity<List<SystemSetting>> getAll() {
        return ResponseEntity.ok(settingService.getAll());
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @Operation(summary = "Update a system setting (SUPER_ADMIN only)")
    public ResponseEntity<SystemSetting> update(
            @PathVariable String key,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(settingService.update(key, body.get("value"), user.getId()));
    }
}
