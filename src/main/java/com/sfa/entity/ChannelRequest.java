package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

/**
 * A "Create your SFA" request from the public website (V116). Platform-level — it exists
 * before any channel does, so it carries no tenant filter. On approval the super admin's
 * action creates the channel ({@link #tenant}) and its owner login ({@link #ownerUserId}).
 */
@Entity
@Table(name = "channel_requests")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ChannelRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "reference_no", nullable = false, unique = true, length = 30)
    private String referenceNo;

    @Column(name = "business_name", nullable = false, length = 150)
    private String businessName;

    @Column(name = "business_type", nullable = false, length = 60)
    private String businessType;

    @Column(name = "business_reg_no", length = 50)
    private String businessRegNo;

    @Column(length = 255)
    private String address;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(name = "owner_name", nullable = false, length = 150)
    private String ownerName;

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false, length = 20)
    private String phone;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private SubscriptionPlan plan;

    @Column(name = "user_count", nullable = false)
    private Integer userCount;

    @Column(length = 1000)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(name = "review_note", length = 1000)
    private String reviewNote;

    @Column(name = "reviewed_by")
    private UUID reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(name = "owner_user_id")
    private UUID ownerUserId;

    @Column(name = "source_ip", length = 64)
    private String sourceIp;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;

    public enum Status { PENDING, APPROVED, REJECTED }
}
