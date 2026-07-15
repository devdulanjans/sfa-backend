package com.sfa.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "customer_addresses")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(nullable = false, length = 100)
    private String label;

    @Column(name = "address_line", nullable = false, columnDefinition = "text")
    private String addressLine;

    @Column(name = "is_primary", nullable = false)
    private boolean isPrimary;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
