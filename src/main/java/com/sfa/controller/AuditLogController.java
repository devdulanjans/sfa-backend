package com.sfa.controller;

import com.sfa.entity.AuditLog;
import com.sfa.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public Page<AuditLog> list(
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) String entityType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        if (userId != null)     return auditLogRepository.findByUserId(userId, pageable);
        if (entityType != null) return auditLogRepository.findAll(pageable); // filtered in-memory or extend query
        return auditLogRepository.findAll(pageable);
    }
}
