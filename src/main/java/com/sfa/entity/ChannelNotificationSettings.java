package com.sfa.entity;

import com.sfa.security.TenantAwareEntityListener;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;

import java.time.Instant;
import java.util.UUID;

/**
 * One row per channel: whether customers get order/invoice details by email / WhatsApp, and the
 * channel's own SMTP mailbox and WhatsApp business number. Secrets are stored encrypted
 * ({@link com.sfa.notification.SecretCipher}); blank SMTP host / phone-number id = server default.
 */
@Entity
@Table(name = "channel_notification_settings", uniqueConstraints = {
    @UniqueConstraint(name = "uk_channel_notification_settings_tenant", columnNames = {"tenant_id"})
})
@EntityListeners(TenantAwareEntityListener.class)
@Filter(name = "tenantFilter", condition = "tenant_id in (:tenantIds)")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChannelNotificationSettings implements TenantScoped {

    public enum SmtpSecurity { STARTTLS, SSL, NONE }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "email_enabled", nullable = false)
    @Builder.Default
    private boolean emailEnabled = false;

    @Column(name = "smtp_host", length = 200)
    private String smtpHost;

    @Column(name = "smtp_port")
    private Integer smtpPort;

    @Column(name = "smtp_username", length = 200)
    private String smtpUsername;

    @Column(name = "smtp_password_enc", columnDefinition = "TEXT")
    private String smtpPasswordEnc;

    @Enumerated(EnumType.STRING)
    @Column(name = "smtp_security", nullable = false, length = 10)
    @Builder.Default
    private SmtpSecurity smtpSecurity = SmtpSecurity.STARTTLS;

    @Column(name = "from_email", length = 200)
    private String fromEmail;

    @Column(name = "from_name", length = 200)
    private String fromName;

    @Column(name = "whatsapp_enabled", nullable = false)
    @Builder.Default
    private boolean whatsappEnabled = false;

    @Column(name = "whatsapp_phone_number_id", length = 100)
    private String whatsappPhoneNumberId;

    @Column(name = "whatsapp_access_token_enc", columnDefinition = "TEXT")
    private String whatsappAccessTokenEnc;

    @Column(name = "whatsapp_template_name", length = 100)
    private String whatsappTemplateName;

    @Column(name = "whatsapp_template_language", length = 20)
    private String whatsappTemplateLanguage;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    @Column(name = "updated_by")
    private UUID updatedBy;
}
