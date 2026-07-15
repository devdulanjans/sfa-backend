package com.sfa.service;

import com.sfa.entity.AccessLog;
import com.sfa.repository.AccessLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AccessLogService {

    private final AccessLogRepository repo;

    @Async
    public void log(UUID userId, String username, String action,
                    String resource, String details, String ipAddress, String status) {
        try {
            repo.save(AccessLog.builder()
                    .userId(userId)
                    .username(username)
                    .action(action)
                    .resource(resource)
                    .details(details)
                    .ipAddress(ipAddress)
                    .status(status)
                    .build());
        } catch (Exception e) {
            log.warn("Access log failed: {}", e.getMessage());
        }
    }

    @Transactional(readOnly = true)
    public Page<AccessLog> getLogs(UUID userId, String status, String action, int page, int size) {
        return repo.findFiltered(userId, status, action,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
    }
}
