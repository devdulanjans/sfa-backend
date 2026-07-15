package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "promotion_edit_logs")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class PromotionEditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "promotion_id", nullable = false)
    private UUID promotionId;

    @Column(name = "promotion_name", nullable = false)
    private String promotionName;

    @Column(name = "edited_by")
    private UUID editedBy;

    @Column(name = "edited_by_name")
    private String editedByName;

    @Column(name = "changes_json", nullable = false, columnDefinition = "text")
    private String changesJson;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
