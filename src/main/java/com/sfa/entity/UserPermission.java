package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_permissions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "permission_key"}))
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "permission_key", nullable = false, length = 100)
    private String permissionKey;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @CreatedDate
    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;
}
