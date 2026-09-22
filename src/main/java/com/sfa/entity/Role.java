package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

@Entity
@Table(name = "roles")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String name;

    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private java.util.Map<String, Object> permissions;

    @Column(name = "is_system", nullable = false)
    @Builder.Default
    private boolean isSystem = false;

    public static final String PLATFORM_OWNER = "PLATFORM_OWNER";
    public static final String SUPER_ADMIN    = "SUPER_ADMIN";
    /** Channel-scoped administrator — everything SALES_MANAGER can do (via the ROLE_ADMIN >
     *  ROLE_SALES_MANAGER hierarchy in SecurityConfig) plus managing users and Company Profile
     *  within its own assigned channel(s). Unlike SUPER_ADMIN, it never sees channels it isn't
     *  a member of and can't create channels or grant channel access to anyone. */
    public static final String ADMIN          = "ADMIN";
    public static final String SALES_MANAGER  = "SALES_MANAGER";
    public static final String SALES_REP      = "SALES_REP";
    public static final String FINANCE_USER   = "FINANCE_USER";
    public static final String CUSTOMER       = "CUSTOMER";
}
