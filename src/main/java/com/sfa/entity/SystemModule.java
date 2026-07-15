package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_modules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemModule {

    @Id
    private String code;

    @Column(nullable = false)
    private String name;

    private String url;

    private String icon;

    @Column(name = "parent_code")
    private String parentCode;

    @Column(name = "sort_order")
    private int sortOrder;
}
