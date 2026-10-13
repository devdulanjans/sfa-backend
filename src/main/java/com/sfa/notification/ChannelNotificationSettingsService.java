package com.sfa.notification;

import com.sfa.entity.ChannelNotificationSettings;
import com.sfa.entity.ChannelNotificationSettings.SmtpSecurity;
import com.sfa.entity.Tenant;
import com.sfa.exception.BusinessException;
import com.sfa.repository.ChannelNotificationSettingsRepository;
import com.sfa.repository.TenantRepository;
import com.sfa.security.TenantContext;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-channel customer notification setup (Settings > Notifications). A channel that leaves its
 * SMTP host / WhatsApp phone-number id blank falls back to the server default account.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ChannelNotificationSettingsService {

    /** Secrets are never returned — only whether one is saved. */
    public record SettingsDto(
            boolean emailEnabled,
            String smtpHost,
            Integer smtpPort,
            String smtpUsername,
            boolean smtpPasswordSet,
            SmtpSecurity smtpSecurity,
            String fromEmail,
            String fromName,
            boolean whatsappEnabled,
            String whatsappPhoneNumberId,
            boolean whatsappAccessTokenSet,
            String whatsappTemplateName,
            String whatsappTemplateLanguage,
            boolean serverEmailAvailable,
            String serverFromEmail,
            boolean serverWhatsappAvailable) {}

    /** Blank smtpPassword / whatsappAccessToken = keep the saved one; the clear* flags remove it. */
    public record UpdateRequest(
            boolean emailEnabled,
            @Size(max = 200) String smtpHost,
            @Min(1) @Max(65535) Integer smtpPort,
            @Size(max = 200) String smtpUsername,
            String smtpPassword,
            boolean clearSmtpPassword,
            SmtpSecurity smtpSecurity,
            @Email @Size(max = 200) String fromEmail,
            @Size(max = 200) String fromName,
            boolean whatsappEnabled,
            @Size(max = 100) String whatsappPhoneNumberId,
            String whatsappAccessToken,
            boolean clearWhatsappAccessToken,
            @Size(max = 100) String whatsappTemplateName,
            @Size(max = 20) String whatsappTemplateLanguage) {}

    /** What a send for one channel actually uses; a config is null when nothing usable exists. */
    public record Resolved(boolean emailEnabled, EmailConfig email, boolean whatsappEnabled, WhatsAppConfig whatsapp) {

        static final Resolved NONE = new Resolved(false, null, false, null);
    }

    private final ChannelNotificationSettingsRepository repo;
    private final TenantRepository tenantRepo;
    private final SecretCipher cipher;
    private final EmailNotifier emailNotifier;
    private final WhatsAppNotifier whatsAppNotifier;

    @Transactional(readOnly = true)
    public SettingsDto getCurrent() {
        return toDto(repo.findByTenant_Id(currentTenantId()).orElseGet(ChannelNotificationSettings::new));
    }

    public SettingsDto update(UpdateRequest req, UUID userId) {
        UUID tenantId = currentTenantId();
        ChannelNotificationSettings s = repo.findByTenant_Id(tenantId).orElseGet(() -> {
            Tenant tenant = tenantRepo.findById(tenantId)
                    .orElseThrow(() -> new BusinessException("Channel not found."));
            return ChannelNotificationSettings.builder().tenant(tenant).build();
        });

        s.setEmailEnabled(req.emailEnabled());
        s.setSmtpHost(trim(req.smtpHost()));
        s.setSmtpPort(req.smtpPort());
        s.setSmtpUsername(trim(req.smtpUsername()));
        s.setSmtpSecurity(req.smtpSecurity() != null ? req.smtpSecurity() : SmtpSecurity.STARTTLS);
        s.setFromEmail(trim(req.fromEmail()));
        s.setFromName(trim(req.fromName()));
        if (req.clearSmtpPassword()) {
            s.setSmtpPasswordEnc(null);
        } else if (req.smtpPassword() != null && !req.smtpPassword().isEmpty()) {
            s.setSmtpPasswordEnc(cipher.encrypt(req.smtpPassword()));
        }

        s.setWhatsappEnabled(req.whatsappEnabled());
        s.setWhatsappPhoneNumberId(trim(req.whatsappPhoneNumberId()));
        s.setWhatsappTemplateName(trim(req.whatsappTemplateName()));
        s.setWhatsappTemplateLanguage(trim(req.whatsappTemplateLanguage()));
        if (req.clearWhatsappAccessToken()) {
            s.setWhatsappAccessTokenEnc(null);
        } else if (req.whatsappAccessToken() != null && !req.whatsappAccessToken().isBlank()) {
            s.setWhatsappAccessTokenEnc(cipher.encrypt(req.whatsappAccessToken().trim()));
        }

        validate(s);
        s.setUpdatedAt(Instant.now());
        s.setUpdatedBy(userId);
        return toDto(repo.save(s));
    }

    /** Resolves a specific channel explicitly — used on async threads where there is no request tenant. */
    @Transactional(readOnly = true)
    public Resolved resolve(UUID tenantId) {
        if (tenantId == null) return Resolved.NONE;
        return repo.findByTenant_Id(tenantId).map(this::resolve).orElse(Resolved.NONE);
    }

    @Transactional(readOnly = true)
    public Resolved resolveCurrent() {
        UUID tenantId = TenantContext.isUnscoped() ? null : TenantContext.getTenantId();
        return resolve(tenantId);
    }

    private Resolved resolve(ChannelNotificationSettings s) {
        return new Resolved(s.isEmailEnabled(), emailConfig(s), s.isWhatsappEnabled(), whatsAppConfig(s));
    }

    private EmailConfig emailConfig(ChannelNotificationSettings s) {
        if (!notBlank(s.getSmtpHost())) {
            EmailConfig def = emailNotifier.serverDefault();
            if (!def.isComplete()) return null;
            // Server mailbox, but the channel may still brand the sender name / reply address.
            return new EmailConfig(def.host(), def.port(), def.username(), def.password(), def.security(),
                    def.fromEmail(), notBlank(s.getFromName()) ? s.getFromName() : def.fromName());
        }
        EmailConfig own = new EmailConfig(s.getSmtpHost(),
                s.getSmtpPort() != null ? s.getSmtpPort() : 0,
                s.getSmtpUsername(),
                cipher.decrypt(s.getSmtpPasswordEnc()),
                s.getSmtpSecurity(),
                notBlank(s.getFromEmail()) ? s.getFromEmail() : s.getSmtpUsername(),
                s.getFromName());
        return own.isComplete() ? own : null;
    }

    private WhatsAppConfig whatsAppConfig(ChannelNotificationSettings s) {
        if (!notBlank(s.getWhatsappPhoneNumberId())) {
            WhatsAppConfig def = whatsAppNotifier.serverDefault();
            return def.isComplete() ? def : null;
        }
        WhatsAppConfig own = new WhatsAppConfig(s.getWhatsappPhoneNumberId(),
                cipher.decrypt(s.getWhatsappAccessTokenEnc()),
                s.getWhatsappTemplateName(), s.getWhatsappTemplateLanguage());
        return own.isComplete() ? own : null;
    }

    private void validate(ChannelNotificationSettings s) {
        if (notBlank(s.getSmtpHost()) && !notBlank(s.getFromEmail()) && !notBlank(s.getSmtpUsername())) {
            throw new BusinessException("Enter a From email (or an SMTP username that is an email address).");
        }
        if (s.isEmailEnabled() && !notBlank(s.getSmtpHost()) && !emailNotifier.serverDefault().isComplete()) {
            throw new BusinessException("Enter this channel's SMTP server details — there is no server default SMTP to fall back to.");
        }
        if (notBlank(s.getWhatsappPhoneNumberId()) && s.getWhatsappAccessTokenEnc() == null) {
            throw new BusinessException("Enter the WhatsApp access token for this phone number ID.");
        }
        if (s.isWhatsappEnabled() && !notBlank(s.getWhatsappPhoneNumberId()) && !whatsAppNotifier.serverDefault().isComplete()) {
            throw new BusinessException("Enter this channel's WhatsApp phone number ID and access token — there is no server default WhatsApp account to fall back to.");
        }
    }

    private SettingsDto toDto(ChannelNotificationSettings s) {
        EmailConfig defEmail = emailNotifier.serverDefault();
        return new SettingsDto(
                s.isEmailEnabled(), s.getSmtpHost(), s.getSmtpPort(), s.getSmtpUsername(),
                s.getSmtpPasswordEnc() != null,
                s.getSmtpSecurity() != null ? s.getSmtpSecurity() : SmtpSecurity.STARTTLS,
                s.getFromEmail(), s.getFromName(),
                s.isWhatsappEnabled(), s.getWhatsappPhoneNumberId(),
                s.getWhatsappAccessTokenEnc() != null,
                s.getWhatsappTemplateName(), s.getWhatsappTemplateLanguage(),
                defEmail.isComplete(), defEmail.isComplete() ? defEmail.fromEmail() : null,
                whatsAppNotifier.serverDefault().isComplete());
    }

    private static UUID currentTenantId() {
        UUID tenantId = TenantContext.isUnscoped() ? null : TenantContext.getTenantId();
        if (tenantId == null) {
            throw new BusinessException("Select a channel first — notification settings are per-channel.");
        }
        return tenantId;
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static String trim(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
