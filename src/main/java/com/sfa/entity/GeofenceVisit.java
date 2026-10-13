package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * A stay inside a customer's geofence, derived server-side from a rep's location points (V114).
 * OPEN while the rep is still (as far as the latest point shows) inside; CLOSED once they left.
 * Stays shorter than the minimum dwell time are discarded when they close (a drive-by).
 */
@Entity
@Table(name = "geofence_visits")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class GeofenceVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "sales_rep_id", nullable = false)
    private UUID salesRepId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "tenant_id")
    private UUID tenantId;

    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt;

    @Column(name = "departed_at", nullable = false)
    private Instant departedAt;

    @Column(nullable = false, length = 10)
    @Builder.Default
    private String status = "OPEN";

    @Column(name = "min_distance_m")
    private Float minDistanceM;

    @Column(name = "point_count", nullable = false)
    @Builder.Default
    private int pointCount = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();
}
