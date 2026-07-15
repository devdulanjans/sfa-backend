package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "customer_visits", indexes = {
    @Index(name = "idx_visit_rep_date",  columnList = "sales_rep_id,check_in"),
    @Index(name = "idx_visit_customer",  columnList = "customer_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CustomerVisit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sales_rep_id", nullable = false)
    private User salesRep;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(name = "check_in")
    private Instant checkIn;

    @Column(name = "check_out")
    private Instant checkOut;

    @Column(name = "checkout_latitude", precision = 10, scale = 7)
    private BigDecimal checkoutLatitude;

    @Column(name = "checkout_longitude", precision = 10, scale = 7)
    private BigDecimal checkoutLongitude;

    @Column(columnDefinition = "text")
    private String purpose;

    @Column(name = "geo_fenced")
    @Builder.Default
    private Boolean geoFenced = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
