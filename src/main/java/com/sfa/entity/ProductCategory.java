package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "product_categories")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ProductCategory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    private String description;

    // Short code used to build invoice numbers (e.g. "IT", "YA") — see
    // InvoiceService.resolveInvoiceCode, which falls back to "IT" when blank.
    @Column(length = 10)
    private String code;
}
