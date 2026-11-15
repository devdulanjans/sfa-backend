package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
// Unique per (membership, key), or per (account, key) when membership_id is null - partial
// unique indexes in V119, which JPA annotations cannot express.
@Table(name = "user_permissions")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    /** The membership (role in one channel) this grant belongs to; null = account-level fallback. */
    @Column(name = "membership_id")
    private UUID membershipId;

    @Column(name = "permission_key", nullable = false, length = 100)
    private String permissionKey;

    @Column(name = "granted_by", nullable = false)
    private UUID grantedBy;

    @CreatedDate
    @Column(name = "granted_at", nullable = false, updatable = false)
    private Instant grantedAt;
}
