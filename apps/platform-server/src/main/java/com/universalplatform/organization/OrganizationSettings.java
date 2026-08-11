package com.universalplatform.organization;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "organization_settings")
class OrganizationSettings {
    @Id
    private UUID tenantId;

    @Column(nullable = false, length = 80)
    private String defaultTimezone;

    @Column(nullable = false, length = 35)
    private String defaultLocale;

    @Column(nullable = false)
    private short weekStartsOn;

    @Column(length = 320)
    private String supportEmail;

    @Column(length = 1000)
    private String supportUrl;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected OrganizationSettings() {}

    OrganizationSettings(UUID tenantId, Instant now) {
        this.tenantId = tenantId;
        this.defaultTimezone = "UTC";
        this.defaultLocale = "en";
        this.weekStartsOn = 1;
        this.updatedAt = now;
    }

    UUID tenantId() { return tenantId; }
    String defaultTimezone() { return defaultTimezone; }
    String defaultLocale() { return defaultLocale; }
    short weekStartsOn() { return weekStartsOn; }
    String supportEmail() { return supportEmail; }
    String supportUrl() { return supportUrl; }
    long version() { return version; }

    void update(String defaultTimezone, String defaultLocale, short weekStartsOn,
                String supportEmail, String supportUrl, Instant now) {
        this.defaultTimezone = defaultTimezone;
        this.defaultLocale = defaultLocale;
        this.weekStartsOn = weekStartsOn;
        this.supportEmail = supportEmail;
        this.supportUrl = supportUrl;
        this.updatedAt = now;
    }
}
