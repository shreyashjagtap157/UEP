package com.universalplatform.credential;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.ActorContext;
import com.universalplatform.security.TenantContext;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class CredentialService {
    private final TenantContext tenant;
    private final ActorContext actor;
    private final AuthorizationService auth;
    private final AuditService audit;
    private final JdbcTemplate jdbc;
    private final Clock clock = Clock.systemUTC();

    CredentialService(TenantContext tenant, ActorContext actor, AuthorizationService auth, AuditService audit,
            JdbcTemplate jdbc) {
        this.tenant = tenant;
        this.actor = actor;
        this.auth = auth;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    List<CredentialView> credentials(UUID membershipId) {
        auth.require(PermissionKey.CREDENTIALS_VIEW);
        UUID t = tenant.requireTenantId();
        String sql = "select id,template_id,membership_id,verification_code,verification_url,issued_at,status,issuer_subject,reason,qr_payload,version from credential where tenant_id=?";
        if (membershipId != null)
            sql += " and membership_id=?";
        sql += " order by issued_at desc";
        return membershipId == null ? jdbc.query(sql, (rs, n) -> credentialRow(rs), t)
                : jdbc.query(sql, (rs, n) -> credentialRow(rs), t, membershipId);
    }

    @Transactional(readOnly = true)
    List<TemplateView> templates() {
        auth.require(PermissionKey.CREDENTIALS_VIEW);
        UUID t = tenant.requireTenantId();
        return jdbc.query(
                "select id,code,name,description,credential_type,version from credential_template where tenant_id=? order by name",
                (rs, n) -> new TemplateView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getLong(6)),
                t);
    }

    @Transactional
    TemplateView createTemplate(String code, String name, String desc, String type) {
        auth.require(PermissionKey.CREDENTIALS_MANAGE);
        UUID t = tenant.requireTenantId();
        UUID id = UUID.randomUUID();
        jdbc.update(
                "insert into credential_template(id,tenant_id,code,name,description,credential_type,status) values(?,?,?,?,?,?,'ACTIVE')",
                id, t, code.trim(), name.trim(), desc, type);
        audit.record(t, actor.requireSubject(), "CREDENTIAL_TEMPLATE_CREATED", "credential_template", id.toString());
        return jdbc.queryForObject(
                "select id,code,name,description,credential_type,version from credential_template where tenant_id=? and id=?",
                (rs, n) -> new TemplateView(rs.getObject(1, UUID.class), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getLong(6)),
                t, id);
    }

    @Transactional
    CredentialView issue(UUID templateId, UUID membershipId, String reason, UUID sourceId) {
        auth.require(PermissionKey.CREDENTIALS_MANAGE);
        UUID t = tenant.requireTenantId();
        require(t, templateId, "credential_template");
        if (jdbc.queryForObject(
                "select count(*) from credential_template where tenant_id=? and id=? and status='ACTIVE'", Long.class,
                t, templateId) == 0)
            throw new IllegalArgumentException("Credential template is not active");
        require(t, membershipId, "tenant_membership");
        UUID id = UUID.randomUUID();
        String code = UUID.randomUUID().toString();
        String url = "/verify/credentials/" + code;
        Instant now = clock.instant();
        jdbc.update(
                "insert into credential(id,tenant_id,template_id,membership_id,verification_code,verification_url,issued_at,issuer_subject,reason,source_id,status,qr_payload) values(?,?,?,?,?,?,?,?,?,?, 'ISSUED',?)",
                id, t, templateId, membershipId, code, url, now, actor.requireSubject(), reason, sourceId, url);
        audit.record(t, actor.requireSubject(), "CREDENTIAL_ISSUED", "credential", id.toString());
        return credential(id);
    }

    @Transactional
    void revoke(UUID id, String reason, long expectedVersion) {
        auth.require(PermissionKey.CREDENTIALS_MANAGE);
        CredentialView c = credential(id);
        if (c.version() != expectedVersion)
            throw new IllegalStateException("Credential was modified");
        if (!c.status().equals("ISSUED"))
            throw new IllegalStateException("Only issued credentials can be revoked");
        jdbc.update(
                "update credential set status='REVOKED', revoked_at=?, revocation_reason=?, version=version+1 where tenant_id=? and id=?",
                clock.instant(), reason, tenant.requireTenantId(), id);
        audit.record(tenant.requireTenantId(), actor.requireSubject(), "CREDENTIAL_REVOKED", "credential",
                id.toString());
    }

    @Transactional(readOnly = true)
    PublicVerification verify(String code) {
        auth.require(PermissionKey.CREDENTIALS_VERIFY);
        return publicVerify(code);
    }

    @Transactional(readOnly = true)
    PublicVerification publicVerify(String code) {
        return jdbc.queryForObject(
                "select c.verification_code,t.name,u.display_name, c.issued_at,c.status,c.issuer_subject,c.reason from credential c join credential_template t on t.tenant_id=c.tenant_id and t.id=c.template_id join tenant_membership m on m.tenant_id=c.tenant_id and m.id=c.membership_id join user_account u on u.id=m.user_id where c.verification_code=?",
                (rs, n) -> new PublicVerification(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getObject(4, Instant.class), rs.getString(5), rs.getString(6), rs.getString(7)),
                code);
    }

    private CredentialView credentialRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new CredentialView(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getObject(3, UUID.class),
                rs.getString(4), rs.getString(5), rs.getObject(6, Instant.class), rs.getString(7), rs.getString(8),
                rs.getString(9), rs.getString(10), rs.getLong(11));
    }

    private CredentialView credential(UUID id) {
        return jdbc.queryForObject(
                "select id,template_id,membership_id,verification_code,verification_url,issued_at,status,issuer_subject,reason,qr_payload,version from credential where tenant_id=? and id=?",
                (rs, n) -> credentialRow(rs), tenant.requireTenantId(), id);
    }

    private void require(UUID t, UUID id, String table) {
        if (jdbc.queryForObject("select count(*) from " + table + " where tenant_id=? and id=?", Long.class, t,
                id) == 0)
            throw new IllegalArgumentException(table + " not found");
    }

    record TemplateView(UUID id, String code, String name, String description, String credentialType, long version) {
    }

    record CredentialView(UUID id, UUID templateId, UUID membershipId, String verificationCode, String verificationUrl,
            Instant issuedAt, String status, String issuerSubject, String reason, String qrPayload, long version) {
    }

    record PublicVerification(String verificationCode, String templateName, String recipientName, Instant issuedAt,
            String status, String issuerSubject, String reason) {
    }
}
