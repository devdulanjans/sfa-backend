package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "customer_categories")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CustomerCategory {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 100)
    private String name;
    private String description;
}
