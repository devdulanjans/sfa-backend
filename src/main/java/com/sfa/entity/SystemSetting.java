package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_settings")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemSetting {

    @Id
    @Column(name = "key", length = 100)
    private String key;

    @Column(name = "value", nullable = false, length = 500)
    private String value;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
