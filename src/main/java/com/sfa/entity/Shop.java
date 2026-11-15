package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A real-world shop shared across businesses (V118) — identity only: name, phone, address,
 * GPS, tax number. Each business's terms with the shop live on its own {@link Customer} row
 * ({@code customers.shop_id}). Platform-level, so no tenant filter; only ever exposed as a
 * profile through {@code GET /api/customers/shop-matches}.
 */
@Entity
@Table(name = "shops")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(length = 30)
    private String phone;

    @Column(name = "phone_norm", length = 10)
    private String phoneNorm;

    @Column(name = "whatsapp_norm", length = 10)
    private String whatsappNorm;

    @Column(length = 150)
    private String email;

    @Column(length = 255)
    private String address;

    @Column(length = 150)
    private String area;

    @Column(name = "tax_number", length = 30)
    private String taxNumber;

    @Column(name = "tax_norm", length = 30)
    private String taxNorm;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private Instant updatedAt;
}
