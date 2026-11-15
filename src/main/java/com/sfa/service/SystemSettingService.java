package com.sfa.service;

import com.sfa.entity.SystemSetting;
import com.sfa.exception.ResourceNotFoundException;
import com.sfa.repository.SystemSettingRepository;
import com.sfa.security.TenantContext;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * General settings. {@code system_settings} holds the platform defaults; {@code channel_settings}
 * overrides a setting for one channel (V121). Every read resolves against the request's active
 * channel, so a scoped request sees its channel's value and an unscoped one the platform default.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SystemSettingService {

    /** A setting as one channel (or the platform view, channelValue=false) sees it. */
    public record SettingDto(String key, String value, String description, Instant updatedAt, UUID updatedBy,
                             boolean channelValue, String defaultValue) {}

    private final SystemSettingRepository repo;

    @PersistenceContext
    private EntityManager em;

    // ── Admin listing / editing ───────────────────────────────────────────────

    /** Every setting as the active channel sees it. */
    @Transactional(readOnly = true)
    public List<SettingDto> getAll() {
        return getAll(activeTenant());
    }

    /** Every setting as {@code tenantId} sees it; null = platform defaults. */
    @Transactional(readOnly = true)
    public List<SettingDto> getAll(UUID tenantId) {
        Map<String, Object[]> overrides = tenantId == null ? Map.of() : overrides(tenantId);
        return repo.findAll().stream()
                .sorted(Comparator.comparing(SystemSetting::getKey))
                .map(s -> {
                    Object[] o = overrides.get(s.getKey());
                    return o == null
                            ? new SettingDto(s.getKey(), s.getValue(), s.getDescription(), s.getUpdatedAt(),
                                             s.getUpdatedBy(), false, s.getValue())
                            : new SettingDto(s.getKey(), (String) o[1], s.getDescription(), toInstant(o[2]),
                                             (UUID) o[3], true, s.getValue());
                }).toList();
    }

    /** Sets a value for one channel, or the platform default when {@code tenantId} is null. */
    public SettingDto update(String key, String value, UUID tenantId, UUID updatedBy) {
        SystemSetting setting = repo.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("Setting not found with key: " + key));
        if (value == null) throw new com.sfa.exception.BusinessException("A value is required");
        if (tenantId == null) {
            setting.setValue(value);
            setting.setUpdatedAt(Instant.now());
            setting.setUpdatedBy(updatedBy);
            repo.save(setting);
        } else {
            em.createNativeQuery("""
                    INSERT INTO channel_settings (tenant_id, key, value, updated_at, updated_by)
                    VALUES (:t, :k, :v, now(), :u)
                    ON CONFLICT (tenant_id, key) DO UPDATE
                        SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by
                    """)
                    .setParameter("t", tenantId).setParameter("k", key)
                    .setParameter("v", value).setParameter("u", updatedBy)
                    .executeUpdate();
        }
        return find(key, tenantId);
    }

    /** Drops a channel's own value so it follows the platform default again. */
    public SettingDto resetToDefault(String key, UUID tenantId) {
        if (!repo.existsById(key)) throw new ResourceNotFoundException("Setting not found with key: " + key);
        em.createNativeQuery("DELETE FROM channel_settings WHERE tenant_id = :t AND key = :k")
                .setParameter("t", tenantId).setParameter("k", key).executeUpdate();
        return find(key, tenantId);
    }

    private SettingDto find(String key, UUID tenantId) {
        return getAll(tenantId).stream().filter(s -> s.key().equals(key)).findFirst().orElseThrow();
    }

    // ── Typed reads (resolved for the active channel) ─────────────────────────

    /** The raw value for the active channel: its own value, else the platform default. */
    @Transactional(readOnly = true)
    public Optional<String> getValue(String key) {
        UUID tenantId = activeTenant();
        if (tenantId != null) {
            List<?> own = em.createNativeQuery("SELECT value FROM channel_settings WHERE tenant_id = :t AND key = :k")
                    .setParameter("t", tenantId).setParameter("k", key).getResultList();
            if (!own.isEmpty()) return Optional.ofNullable((String) own.get(0));
        }
        return repo.findById(key).map(SystemSetting::getValue);
    }

    /** A boolean setting, or {@code def} when missing. */
    @Transactional(readOnly = true)
    public boolean getBool(String key, boolean def) {
        return getValue(key).map(v -> Boolean.parseBoolean(v.trim())).orElse(def);
    }

    /** An integer setting, or {@code def} when missing or not a number. */
    @Transactional(readOnly = true)
    public int getInt(String key, int def) {
        return getValue(key).map(v -> {
            try { return Integer.parseInt(v.trim()); } catch (NumberFormatException e) { return def; }
        }).orElse(def);
    }

    @Transactional(readOnly = true)
    public boolean isOrderPrevent() { return getBool("isOrderPrevent", false); }

    @Transactional(readOnly = true)
    public boolean isInventoryEnabled() { return getBool("inventory_tracking", false); }

    @Transactional(readOnly = true)
    public boolean isPosTaxEnabled() { return getBool("pos_tax_enabled", true); }

    @Transactional(readOnly = true)
    public boolean isShowPromotionAsDiscount() { return getBool("show_promotion_as_discount", false); }

    @Transactional(readOnly = true)
    public boolean isSalesTargetEnabled() { return getBool("sales_target_enabled", false); }

    @Transactional(readOnly = true)
    public boolean isMileageManagementEnabled() { return getBool("mileage_management_enabled", true); }

    @Transactional(readOnly = true)
    public boolean isPromotionManualSelectionEnabled() { return getBool("promotion_manual_selection", true); }

    @Transactional(readOnly = true)
    public boolean isLeadAutoConvertEnabled() { return getBool("lead_auto_convert_to_customer", false); }

    /** Switch for sales routes — off unless enabled (see V110). */
    @Transactional(readOnly = true)
    public boolean isSalesRoutesEnabled() { return getBool("sales_routes_enabled", false); }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static UUID activeTenant() {
        return TenantContext.isUnscoped() ? null : TenantContext.getTenantId();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object[]> overrides(UUID tenantId) {
        List<Object[]> rows = em.createNativeQuery(
                        "SELECT key, value, updated_at, updated_by FROM channel_settings WHERE tenant_id = :t")
                .setParameter("t", tenantId).getResultList();
        Map<String, Object[]> out = new HashMap<>();
        rows.forEach(r -> out.put((String) r[0], r));
        return out;
    }

    private static Instant toInstant(Object o) {
        if (o == null) return null;
        if (o instanceof Instant i) return i;
        if (o instanceof Timestamp t) return t.toInstant();
        if (o instanceof OffsetDateTime odt) return odt.toInstant();
        return null;
    }
}
