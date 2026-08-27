package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "reasons")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Reason {

    public enum ReasonType { DAMAGE, RETURN }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ReasonType type;

    @Column(nullable = false, length = 200)
    private String label;

    // When true, picking this reason on mobile reveals a free-text field for the
    // rep to fill in (replaces the old client-side hardcoded match on "Other").
    @Column(name = "allow_free_text", nullable = false)
    @Builder.Default
    private boolean allowFreeText = false;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private int sortOrder = 0;
}
