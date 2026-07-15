package com.sfa.service;

import com.sfa.dto.LicenseSettingsDto;
import com.sfa.dto.LicenseSettingsUpdateRequest;
import com.sfa.entity.LicenseSettings;
import com.sfa.repository.LicenseSettingsRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Backs this install's SFA/POS license flags. Checked on nearly every request (via
 * {@link com.sfa.license.LicenseEnforcementAspect}), so the current settings are kept
 * in an in-process cache rather than hitting the DB each time — this is a single
 * backend instance per install, and the cache is refreshed immediately on update()
 * so a toggle takes effect on the very next request.
 */
@Service
@RequiredArgsConstructor
public class LicenseService {

    private final LicenseSettingsRepository repo;

    private volatile LicenseSettings cached;

    @PostConstruct
    void init() {
        refresh();
    }

    public boolean isSfaEnabled() {
        return snapshot().isSfaEnabled();
    }

    public boolean isPosEnabled() {
        return snapshot().isPosEnabled();
    }

    @Transactional(readOnly = true)
    public LicenseSettingsDto get() {
        return LicenseSettingsDto.from(snapshot());
    }

    @Transactional
    public LicenseSettingsDto update(LicenseSettingsUpdateRequest req, UUID updatedBy) {
        LicenseSettings s = getSingleton();
        s.setSfaEnabled(req.sfaEnabled());
        s.setPosEnabled(req.posEnabled());
        s.setClientName(req.clientName());
        s.setNote(req.note());
        s.setUpdatedBy(updatedBy);
        LicenseSettings saved = repo.save(s);
        cached = saved;
        return LicenseSettingsDto.from(saved);
    }

    private LicenseSettings snapshot() {
        LicenseSettings c = cached;
        return c != null ? c : refresh();
    }

    private synchronized LicenseSettings refresh() {
        LicenseSettings s = getSingleton();
        cached = s;
        return s;
    }

    private LicenseSettings getSingleton() {
        return repo.findFirstByOrderByUpdatedAtDesc()
                .orElseGet(() -> repo.save(LicenseSettings.builder().build()));
    }
}
