package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "license_settings")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class LicenseSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sfa_enabled", nullable = false)
    @Builder.Default
    private boolean sfaEnabled = true;

    @Column(name = "pos_enabled", nullable = false)
    @Builder.Default
    private boolean posEnabled = true;

    @Column(name = "client_name", length = 200)
    private String clientName;

    @Column(columnDefinition = "TEXT")
    private String note;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
