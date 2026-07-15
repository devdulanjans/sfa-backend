package com.sfa.service;

import com.sfa.entity.SystemSetting;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.SystemSettingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
public class SystemSettingService {

    private final SystemSettingRepository repo;

    @Transactional(readOnly = true)
    public List<SystemSetting> getAll() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public boolean isOrderPrevent() {
        return repo.findById("isOrderPrevent")
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isInventoryEnabled() {
        return repo.findById("inventory_tracking")
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isPosTaxEnabled() {
        return repo.findById("pos_tax_enabled")
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean isShowPromotionAsDiscount() {
        return repo.findById("show_promotion_as_discount")
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(false);
    }

    public SystemSetting update(String key, String value, UUID updatedBy) {
        SystemSetting setting = repo.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Setting not found with key: " + key));
        setting.setValue(value);
        setting.setUpdatedAt(Instant.now());
        setting.setUpdatedBy(updatedBy);
        return repo.save(setting);
    }
}
