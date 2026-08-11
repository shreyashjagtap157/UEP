package com.universalplatform.organization;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class OrganizationService {
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final AuthorizationService authorization;
    private final BranchRepository branches;
    private final OrganizationSettingsRepository settings;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    OrganizationService(TenantContext tenantContext, ActorContext actorContext, AuthorizationService authorization,
                        BranchRepository branches, OrganizationSettingsRepository settings, AuditService audit) {
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.authorization = authorization;
        this.branches = branches;
        this.settings = settings;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    OrganizationPage<BranchView> listBranches(int page, int size) {
        authorization.require(PermissionKey.BRANCHES_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        int safePage = Math.max(0, page);
        int safeSize = size < 1 ? 25 : Math.min(size, 100);
        Page<Branch> result = branches.findAllByTenantId(
                tenantId, PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.ASC, "displayName", "id")));
        return new OrganizationPage<>(result.getContent().stream().map(OrganizationService::toView).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional(readOnly = true)
    BranchView branch(UUID branchId) {
        authorization.require(PermissionKey.BRANCHES_VIEW, branchId);
        return toView(branch(tenantContext.requireTenantId(), branchId));
    }

    @Transactional
    BranchView createBranch(String code, String displayName, String timezone) {
        authorization.require(PermissionKey.BRANCHES_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        String normalizedCode = normalizeCode(code);
        if (branches.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) {
            throw new OrganizationConflictException("Branch code already exists");
        }
        Branch branch = branches.save(new Branch(UUID.randomUUID(), tenantId, normalizedCode,
                required(displayName, "Branch display name", 200), timezone(timezone), clock.instant()));
        audit.record(tenantId, actorContext.requireSubject(), "BRANCH_CREATED", "branch", branch.id().toString());
        return toView(branch);
    }

    @Transactional
    BranchView updateBranch(UUID branchId, String code, String displayName, String timezone,
                            BranchStatus status, long expectedVersion) {
        authorization.require(PermissionKey.BRANCHES_MANAGE, branchId);
        UUID tenantId = tenantContext.requireTenantId();
        Branch branch = branch(tenantId, branchId);
        if (branch.version() != expectedVersion) throw new OrganizationConflictException("Branch was modified by another request");
        String normalizedCode = normalizeCode(code);
        if (!branch.code().equalsIgnoreCase(normalizedCode) && branches.existsByTenantIdAndCodeIgnoreCase(tenantId, normalizedCode)) {
            throw new OrganizationConflictException("Branch code already exists");
        }
        branch.update(normalizedCode, required(displayName, "Branch display name", 200), timezone(timezone), status, clock.instant());
        audit.record(tenantId, actorContext.requireSubject(), "BRANCH_UPDATED", "branch", branch.id().toString());
        return toView(branch);
    }

    @Transactional
    SettingsView settings() {
        authorization.require(PermissionKey.ORGANIZATION_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        return toView(settings.findById(tenantId).orElseGet(() -> settings.save(new OrganizationSettings(tenantId, clock.instant()))));
    }

    @Transactional
    SettingsView updateSettings(String defaultTimezone, String defaultLocale, short weekStartsOn,
                                String supportEmail, String supportUrl, long expectedVersion) {
        authorization.require(PermissionKey.ORGANIZATION_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        OrganizationSettings current = settings.findById(tenantId)
                .orElseGet(() -> settings.save(new OrganizationSettings(tenantId, clock.instant())));
        if (current.version() != expectedVersion) throw new OrganizationConflictException("Organization settings were modified by another request");
        if (weekStartsOn < 1 || weekStartsOn > 7) throw new IllegalArgumentException("weekStartsOn must be between 1 and 7");
        current.update(timezone(defaultTimezone), locale(defaultLocale), weekStartsOn,
                normalizeNullable(supportEmail, 320), supportUrl(supportUrl), clock.instant());
        audit.record(tenantId, actorContext.requireSubject(), "ORGANIZATION_SETTINGS_UPDATED", "organization_settings", tenantId.toString());
        return toView(current);
    }

    private Branch branch(UUID tenantId, UUID branchId) {
        return branches.findByTenantIdAndId(tenantId, branchId)
                .orElseThrow(() -> new OrganizationNotFoundException("Branch not found"));
    }

    private static BranchView toView(Branch branch) {
        return new BranchView(branch.id(), branch.code(), branch.displayName(), branch.timezone(), branch.status(),
                branch.createdAt(), branch.version());
    }

    private static SettingsView toView(OrganizationSettings settings) {
        return new SettingsView(settings.tenantId(), settings.defaultTimezone(), settings.defaultLocale(), settings.weekStartsOn(),
                settings.supportEmail(), settings.supportUrl(), settings.version());
    }

    private static String normalizeCode(String value) {
        String normalized = required(value, "Branch code", 64).toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) {
            throw new IllegalArgumentException("Branch code may contain letters, numbers, dot, underscore, and hyphen");
        }
        return normalized;
    }

    private static String timezone(String value) {
        String normalized = required(value, "Timezone", 80);
        try {
            return ZoneId.of(normalized).getId();
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Unknown IANA timezone: " + normalized);
        }
    }

    private static String locale(String value) {
        String normalized = required(value, "Locale", 35);
        Locale parsed = Locale.forLanguageTag(normalized);
        if (parsed.getLanguage().isBlank()) throw new IllegalArgumentException("Invalid BCP 47 locale: " + normalized);
        return parsed.toLanguageTag();
    }

    private static String supportUrl(String value) {
        String normalized = normalizeNullable(value, 1000);
        if (normalized == null) return null;
        try {
            URI uri = new URI(normalized);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http")) || uri.getHost() == null) {
                throw new IllegalArgumentException("Support URL must be an absolute HTTP(S) URL");
            }
            if (uri.getUserInfo() != null) throw new IllegalArgumentException("Support URL must not contain user information");
            return uri.toString();
        } catch (URISyntaxException exception) {
            throw new IllegalArgumentException("Support URL is invalid");
        }
    }

    private static String required(String value, String field, int maxLength) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) throw new IllegalArgumentException(field + " is required");
        if (normalized.length() > maxLength) throw new IllegalArgumentException(field + " exceeds " + maxLength + " characters");
        return normalized;
    }

    private static String normalizeNullable(String value, int maxLength) {
        if (value == null) return null;
        String normalized = value.trim();
        if (normalized.isEmpty()) return null;
        if (normalized.length() > maxLength) throw new IllegalArgumentException("Value exceeds " + maxLength + " characters");
        return normalized;
    }

    record BranchView(UUID id, String code, String displayName, String timezone, BranchStatus status,
                      Instant createdAt, long version) {}
    record SettingsView(UUID tenantId, String defaultTimezone, String defaultLocale, short weekStartsOn,
                        String supportEmail, String supportUrl, long version) {}
}
