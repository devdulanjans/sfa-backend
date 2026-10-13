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

    /** A boolean setting, or {@code def} when missing. */
    @Transactional(readOnly = true)
    public boolean getBool(String key, boolean def) {
        return repo.findById(key).map(s -> Boolean.parseBoolean(s.getValue().trim())).orElse(def);
    }

    /** An integer setting, or {@code def} when missing or not a number. */
    @Transactional(readOnly = true)
    public int getInt(String key, int def) {
        return repo.findById(key).map(s -> {
            try { return Integer.parseInt(s.getValue().trim()); } catch (NumberFormatException e) { return def; }
        }).orElse(def);
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

    @Transactional(readOnly = true)
    public boolean isSalesTargetEnabled() {
        return repo.findById("sales_target_enabled")
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isMileageManagementEnabled() {
        return repo.findById("mileage_management_enabled")
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean isPromotionManualSelectionEnabled() {
        return repo.findById("promotion_manual_selection")
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(true);
    }

    @Transactional(readOnly = true)
    public boolean isLeadAutoConvertEnabled() {
        return repo.findById("lead_auto_convert_to_customer")
                .map(s -> Boolean.parseBoolean(s.getValue()))
                .orElse(false);
    }

    /** Global switch for sales routes — off unless enabled (see V110). */
    @Transactional(readOnly = true)
    public boolean isSalesRoutesEnabled() {
        return repo.findById("sales_routes_enabled")
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
