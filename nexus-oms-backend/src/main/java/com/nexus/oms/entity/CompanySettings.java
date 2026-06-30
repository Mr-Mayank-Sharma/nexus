package com.nexus.oms.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "nx_company_settings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CompanySettings {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @NotNull
    @Column(name = "tenant_id", unique = true)
    private UUID tenantId;

    @NotBlank
    @Column(name = "company_name")
    private String companyName;

    @Column(name = "company_logo")
    private String companyLogo;

    @Column(name = "tax_id")
    private String taxId;

    @Column(name = "registration_number")
    private String registrationNumber;

    @NotBlank
    @Column(name = "default_currency")
    private String defaultCurrency;

    @Column(name = "default_language")
    private String defaultLanguage;

    @Column(name = "default_timezone")
    private String defaultTimezone;

    @Column(name = "date_format")
    private String dateFormat;

    @Column(name = "time_format")
    private String timeFormat;

    @Column(name = "fiscal_year_start")
    private String fiscalYearStart;

    @Column(columnDefinition = "jsonb")
    private String countries;

    @Column(columnDefinition = "jsonb")
    private String regions;

    @Column(columnDefinition = "jsonb")
    private String holidays;

    @Column(name = "feature_flags", columnDefinition = "jsonb")
    private String featureFlags;

    @Column(name = "security_policy", columnDefinition = "jsonb")
    private String securityPolicy;

    @Column(name = "backup_config", columnDefinition = "jsonb")
    private String backupConfig;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
