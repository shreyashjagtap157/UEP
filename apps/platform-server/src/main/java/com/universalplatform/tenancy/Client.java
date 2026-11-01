package com.universalplatform.tenancy;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.time.LocalDate;
import java.time.Period;

@Entity
public class Client {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name; // client display name

    private String businessName; // official business name

    private String businessType; // e.g., "School", "University", "Coaching"

    private LocalDate licensingPeriodStart;

    private LocalDate licensingPeriodEnd; // null means perpetual

    private String deploymentType; // "self-hosted" or "cloud"

    private String logoUrl; // URL to logo image

    // Getters and setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBusinessName() {
        return businessName;
    }

    public void setBusinessName(String businessName) {
        this.businessName = businessName;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    public LocalDate getLicensingPeriodStart() {
        return licensingPeriodStart;
    }

    public void setLicensingPeriodStart(LocalDate licensingPeriodStart) {
        this.licensingPeriodStart = licensingPeriodStart;
    }

    public LocalDate getLicensingPeriodEnd() {
        return licensingPeriodEnd;
    }

    public void setLicensingPeriodEnd(LocalDate licensingPeriodEnd) {
        this.licensingPeriodEnd = licensingPeriodEnd;
    }

    public String getDeploymentType() {
        return deploymentType;
    }

    public void setDeploymentType(String deploymentType) {
        this.deploymentType = deploymentType;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }
}