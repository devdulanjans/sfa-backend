package com.sfa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.Filter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import com.sfa.security.TenantAwareEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "company_profile", uniqueConstraints = {
    @UniqueConstraint(name = "uk_company_profile_tenant", columnNames = {"tenant_id"})
})
@EntityListeners({AuditingEntityListener.class, TenantAwareEntityListener.class})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class CompanyProfile implements TenantScoped {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Column(name = "company_name", nullable = false, length = 200)
    private String companyName;

    @Column(name = "logo_object_path", length = 300)
    private String logoObjectPath;

    @Column(name = "logo_content_type", length = 100)
    private String logoContentType;

    @Column(name = "registered_address", columnDefinition = "TEXT")
    private String registeredAddress;

    @Column(name = "operating_address", columnDefinition = "TEXT")
    private String operatingAddress;

    @Column(length = 50)
    private String phone;

    @Column(length = 150)
    private String email;

    @Column(length = 200)
    private String website;

    @Column(name = "tax_id", length = 100)
    private String taxId;

    @Column(name = "registration_number", length = 100)
    private String registrationNumber;

    @Column(name = "vat_registration_number", length = 100)
    private String vatRegistrationNumber;

    @Column(name = "vat_rate_pct", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal vatRatePct = BigDecimal.ZERO;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "bank_account_name", length = 150)
    private String bankAccountName;

    @Column(name = "bank_account_number", length = 100)
    private String bankAccountNumber;

    @Column(name = "bank_branch", length = 150)
    private String bankBranch;

    @Column(name = "bank_swift_code", length = 50)
    private String bankSwiftCode;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by")
    private UUID updatedBy;
}
