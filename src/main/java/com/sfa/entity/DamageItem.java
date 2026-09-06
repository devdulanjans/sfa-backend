package com.sfa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "damage_items")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class DamageItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "damage_id", nullable = false)
    private Damage damageHeader;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false, precision = 10, scale = 3)
    private BigDecimal quantity;

    // scale 5 (not the usual 2) so a batch price defined to 5 decimal places (see
    // BatchPrice.price) survives onto the line instead of being silently rounded away.
    @Column(name = "unit_price", nullable = false, precision = 15, scale = 5)
    @Builder.Default
    private BigDecimal unitPrice = BigDecimal.ZERO;

    @Column(name = "price_source", length = 20)
    private String priceSource;
}
