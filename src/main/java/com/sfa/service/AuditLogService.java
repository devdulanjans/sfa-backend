package com.sfa.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sfa.entity.AuditLog;
import com.sfa.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repo;
    private final ObjectMapper        mapper;

    @Async
    public void log(UUID userId, String action, String entityType, UUID entityId,
                    Object oldValue, Object newValue) {
        try {
            Map<String, Object> changes = Map.of(
                "before", oldValue != null ? oldValue : "null",
                "after",  newValue != null ? newValue : "null"
            );
            AuditLog entry = AuditLog.builder()
                    .userId(userId)
                    .action(action)
                    .entityType(entityType)
                    .entityId(entityId)
                    .changes(mapper.writeValueAsString(changes))
                    .build();
            repo.save(entry);
        } catch (Exception e) {
            log.warn("Audit log failed: {}", e.getMessage());
        }
    }
}
