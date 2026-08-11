package com.universalplatform.identity;

import com.universalplatform.audit.AuditService;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class SessionManagementService {
    private static final String FALLBACK_PREFIX = "token:";

    private final PlatformSessionRepository sessions;
    private final TenantContext tenantContext;
    private final ActorContext actorContext;
    private final IdentitySecurityService identitySecurity;
    private final AuthorizationService authorization;
    private final AuditService audit;
    private final Clock clock = Clock.systemUTC();

    SessionManagementService(PlatformSessionRepository sessions, TenantContext tenantContext, ActorContext actorContext,
                             IdentitySecurityService identitySecurity, AuthorizationService authorization, AuditService audit) {
        this.sessions = sessions;
        this.tenantContext = tenantContext;
        this.actorContext = actorContext;
        this.identitySecurity = identitySecurity;
        this.authorization = authorization;
        this.audit = audit;
    }

    @Transactional
    void observeCurrentSession(ActiveIdentity identity) {
        String oidcSessionId = currentSessionIdentifier();
        if (oidcSessionId == null) return;
        UUID tenantId = tenantContext.requireTenantId();
        Instant now = clock.instant();
        Instant expiresAt = actorContext.currentTokenExpiry().orElse(null);
        PlatformSession session = sessions.findByTenantIdAndMembershipIdAndOidcSessionId(
                        tenantId, identity.membershipId(), oidcSessionId)
                .orElseGet(() -> sessions.save(new PlatformSession(
                        UUID.randomUUID(), tenantId, identity.membershipId(), oidcSessionId, now, expiresAt)));
        if (session.revoked()) {
            throw new IdentityAccessDeniedException("This platform session has been revoked");
        }
        session.touch(now, expiresAt);
    }

    @Transactional(readOnly = true)
    IdentityPage<SessionView> currentUserSessions(int page, int size) {
        ActiveIdentity identity = identitySecurity.requireCurrentIdentity();
        String current = currentSessionIdentifier();
        Page<PlatformSession> result = sessions.findAllByTenantIdAndMembershipId(
                tenantContext.requireTenantId(), identity.membershipId(), pageRequest(page, size));
        return toPage(result, session -> toView(session, session.oidcSessionId().equals(current)));
    }

    @Transactional(readOnly = true)
    IdentityPage<SessionView> memberSessions(UUID membershipId, int page, int size) {
        authorization.require(PermissionKey.SESSIONS_VIEW);
        UUID tenantId = tenantContext.requireTenantId();
        Page<PlatformSession> result = sessions.findAllByTenantIdAndMembershipId(tenantId, membershipId, pageRequest(page, size));
        return toPage(result, session -> toView(session, false));
    }

    @Transactional
    void revokeOwn(UUID sessionId, String reason) {
        ActiveIdentity identity = identitySecurity.requireCurrentIdentity();
        UUID tenantId = tenantContext.requireTenantId();
        PlatformSession session = sessions.findByTenantIdAndId(tenantId, sessionId)
                .filter(value -> value.membershipId().equals(identity.membershipId()))
                .orElseThrow(() -> new IdentityNotFoundException("Session not found"));
        revoke(session, actorContext.requireSubject(), reason == null ? "User revoked session" : reason, tenantId);
    }

    @Transactional
    void revokeForAdministrator(UUID sessionId, String reason) {
        authorization.require(PermissionKey.SESSIONS_MANAGE);
        UUID tenantId = tenantContext.requireTenantId();
        PlatformSession session = sessions.findByTenantIdAndId(tenantId, sessionId)
                .orElseThrow(() -> new IdentityNotFoundException("Session not found"));
        revoke(session, actorContext.requireSubject(), reason == null ? "Administrator revoked session" : reason, tenantId);
    }


    private static PageRequest pageRequest(int page, int size) {
        int safePage = Math.max(0, page);
        int safeSize = size < 1 ? 25 : Math.min(size, 100);
        return PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "lastSeenAt"));
    }

    private static IdentityPage<SessionView> toPage(Page<PlatformSession> result, java.util.function.Function<PlatformSession, SessionView> mapper) {
        return new IdentityPage<>(result.getContent().stream().map(mapper).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    private void revoke(PlatformSession session, String actor, String reason, UUID tenantId) {
        session.revoke(actor, normalizeReason(reason), clock.instant());
        audit.record(tenantId, actor, "SESSION_REVOKED", "platform_session", session.id().toString());
    }

    private String currentSessionIdentifier() {
        return actorContext.currentSessionId()
                .or(() -> actorContext.currentTokenId().map(id -> FALLBACK_PREFIX + id))
                .orElse(null);
    }

    private static String normalizeReason(String reason) {
        String normalized = reason == null ? "Session revoked" : reason.trim();
        if (normalized.isEmpty()) normalized = "Session revoked";
        if (normalized.length() > 500) throw new IllegalArgumentException("Revocation reason exceeds 500 characters");
        return normalized;
    }

    private static SessionView toView(PlatformSession session, boolean current) {
        return new SessionView(session.id(), session.startedAt(), session.lastSeenAt(), session.expiresAt(),
                session.revokedAt(), session.revocationReason(), current);
    }

    record SessionView(UUID id, Instant startedAt, Instant lastSeenAt, Instant expiresAt,
                       Instant revokedAt, String revocationReason, boolean current) {}
}
