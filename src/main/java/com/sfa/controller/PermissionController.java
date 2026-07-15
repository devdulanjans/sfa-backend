package com.sfa.controller;

import com.sfa.entity.AccessLog;
import com.sfa.entity.Permission;
import com.sfa.entity.SystemModule;
import com.sfa.repository.SystemModuleRepository;
import com.sfa.security.UserDetailsImpl;
import com.sfa.service.AccessLogService;
import com.sfa.service.UserPermissionService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
public class PermissionController {

    private final UserPermissionService  permissionService;
    private final AccessLogService       accessLogService;
    private final SystemModuleRepository moduleRepo;

    // ── Module catalog ────────────────────────────────────────────────────────

    public record ModuleNode(
            String code, String name, String url, String icon,
            int sortOrder, List<ModuleNode> children) {}

    @GetMapping("/api/modules")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<ModuleNode> getModules() {
        List<SystemModule> all = moduleRepo.findAllByOrderBySortOrderAsc();

        // Build node map
        Map<String, ModuleNode> map = new LinkedHashMap<>();
        for (SystemModule m : all) {
            map.put(m.getCode(),
                    new ModuleNode(m.getCode(), m.getName(), m.getUrl(),
                            m.getIcon(), m.getSortOrder(), new ArrayList<>()));
        }

        // Link children to parents; collect roots
        List<ModuleNode> roots = new ArrayList<>();
        for (SystemModule m : all) {
            ModuleNode node = map.get(m.getCode());
            if (m.getParentCode() == null) {
                roots.add(node);
            } else {
                ModuleNode parent = map.get(m.getParentCode());
                if (parent != null) parent.children().add(node);
            }
        }
        return roots;
    }

    // ── Permission catalog ────────────────────────────────────────────────────

    @GetMapping("/api/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<Permission> getAllPermissions() {
        return permissionService.getAllDefinedPermissions();
    }

    // ── Per-user permissions ──────────────────────────────────────────────────

    @GetMapping("/api/users/{userId}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public List<String> getUserPermissions(@PathVariable UUID userId) {
        return permissionService.getUserPermissionKeys(userId);
    }

    /** Replace the full permission set for a user. Body: { "permissions": ["KEY1","KEY2",...] } */
    @PutMapping("/api/users/{userId}/permissions")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<Void> setUserPermissions(
            @PathVariable UUID userId,
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal) {

        @SuppressWarnings("unchecked")
        List<String> keys = (List<String>) body.get("permissions");
        permissionService.setPermissions(userId, keys == null ? List.of() : keys, principal.getId());
        return ResponseEntity.noContent().build();
    }

    // ── Access / activity logs ────────────────────────────────────────────────

    /** Called from the frontend to record page access and permission-denied events. */
    @PostMapping("/api/access-logs")
    public ResponseEntity<Void> logAccess(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserDetailsImpl principal,
            HttpServletRequest request) {

        UUID   userId   = principal != null ? principal.getId() : null;
        String username = principal != null ? principal.getUsername() : "anonymous";
        String action   = (String) body.getOrDefault("action", "PAGE_ACCESS");
        String resource = (String) body.get("resource");
        String details  = (String) body.get("details");
        String status   = (String) body.getOrDefault("status", "SUCCESS");
        String ip       = getClientIp(request);

        accessLogService.log(userId, username, action, resource, details, ip, status);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/api/access-logs")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Page<AccessLogDto> getLogs(
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "30") int size,
            @RequestParam(required = false)    String userId,
            @RequestParam(required = false)    String status,
            @RequestParam(required = false)    String action) {

        UUID uid = userId != null && !userId.isBlank() ? UUID.fromString(userId) : null;
        return accessLogService.getLogs(uid, status, action, page, size)
                .map(AccessLogDto::from);
    }

    // ── DTO ───────────────────────────────────────────────────────────────────

    public record AccessLogDto(
            UUID id, UUID userId, String username,
            String action, String resource, String details,
            String ipAddress, String status, Instant createdAt) {
        static AccessLogDto from(AccessLog l) {
            return new AccessLogDto(l.getId(), l.getUserId(), l.getUsername(),
                    l.getAction(), l.getResource(), l.getDetails(),
                    l.getIpAddress(), l.getStatus(), l.getCreatedAt());
        }
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        return (forwarded != null && !forwarded.isBlank())
                ? forwarded.split(",")[0].trim()
                : request.getRemoteAddr();
    }
}
