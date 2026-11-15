package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * "This person, in this channel, as this role" (V119). One account can hold many memberships —
 * owner of one business, sales rep for another, customer of a third, or several roles in the
 * same business. A session runs as exactly one ACTIVE membership (JWT "mid" claim), which
 * supplies its role, customer/distributor link and permissions. Deliberately NOT tenant-
 * filtered: a person's own memberships span channels, and services scope queries explicitly.
 */
@Entity
@Table(name = "memberships", uniqueConstraints = {
    @UniqueConstraint(name = "uk_memberships_user_tenant_role", columnNames = {"user_id", "tenant_id", "role_id"})
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Membership {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "login_distributor_id")
    private Distributor loginDistributor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.ACTIVE;

    @Column(name = "invited_by")
    private UUID invitedBy;

    @Column(name = "invited_at")
    private Instant invitedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Status { ACTIVE, INVITED, DECLINED, REVOKED }

    /** Usable for a session: accepted, and the business itself hasn't been disabled. */
    public boolean isUsable() {
        return status == Status.ACTIVE && tenant != null && tenant.getStatus() == Tenant.TenantStatus.ACTIVE;
    }
}
