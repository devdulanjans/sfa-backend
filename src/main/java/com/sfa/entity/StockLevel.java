package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_levels")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class StockLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", insertable = false, updatable = false)
    private Product product;

    @Column(name = "on_hand", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal onHand = BigDecimal.ZERO;

    @Column(name = "reserved", nullable = false, precision = 12, scale = 3)
    @Builder.Default
    private BigDecimal reserved = BigDecimal.ZERO;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    @PreUpdate
    void touch() { updatedAt = Instant.now(); }
}
