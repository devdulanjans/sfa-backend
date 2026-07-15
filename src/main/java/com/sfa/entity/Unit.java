package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "units")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Unit {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    @Column(nullable = false, unique = true, length = 50)
    private String name;
    @Column(length = 10)
    private String abbreviation;
}
