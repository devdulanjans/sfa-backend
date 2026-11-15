package com.sfa.controller;

import com.sfa.exception.BusinessException;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.TenantRepository;
import com.sfa.security.TenantContext;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.SystemSettingService;
import com.sfa.service.SystemSettingService.SettingDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Settings > General. Inside a channel, reads and writes that channel's settings (channel owners
 * manage their own). In the platform view, SUPER_ADMIN edits the platform defaults, or a specific
 * channel's settings by passing {@code tenantId}.
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
@Tag(name = "System Settings")
@SecurityRequirement(name = "bearerAuth")
public class SystemSettingController {

    private final SystemSettingService settingService;
    private final TenantRepository tenantRepository;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','SALES_MANAGER','SALES_REP')")
    @Operation(summary = "Settings as the active channel sees them (platform view: defaults, or ?tenantId=)")
    public ResponseEntity<List<SettingDto>> getAll(@RequestParam(required = false) UUID tenantId) {
        return ResponseEntity.ok(settingService.getAll(targetTenant(tenantId)));
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @Operation(summary = "Change a setting for the active channel (platform view: the default, or ?tenantId=)")
    public ResponseEntity<SettingDto> update(
            @PathVariable String key,
            @RequestParam(required = false) UUID tenantId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserDetailsImpl user) {
        return ResponseEntity.ok(settingService.update(key, body.get("value"), targetTenant(tenantId), user.getId()));
    }

    @DeleteMapping("/{key}/channel-value")
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    @Operation(summary = "Make a channel follow the platform default for this setting again")
    public ResponseEntity<SettingDto> reset(@PathVariable String key,
                                            @RequestParam(required = false) UUID tenantId) {
        UUID target = targetTenant(tenantId);
        if (target == null) throw new BusinessException("Choose a channel first");
        return ResponseEntity.ok(settingService.resetToDefault(key, target));
    }

    /**
     * The channel a request acts on. A channel session always acts on its own channel; only the
     * platform view (SUPER_ADMIN/PLATFORM_OWNER, unscoped) may name one, and null there means the
     * platform defaults.
     */
    private UUID targetTenant(UUID requested) {
        if (TenantContext.isUnscoped()) {
            if (requested != null && !tenantRepository.existsById(requested)) {
                throw new ResourceNotFoundException("Channel not found");
            }
            return requested;
        }
        UUID active = TenantContext.getTenantId();
        if (active == null) throw new AccessDeniedException("Select a channel first");
        if (requested != null && !requested.equals(active)) throw new AccessDeniedException("Not your channel");
        return active;
    }
}
