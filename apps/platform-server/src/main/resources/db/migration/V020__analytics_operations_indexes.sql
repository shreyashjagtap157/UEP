CREATE INDEX ix_attendance_record_tenant_finalized ON attendance_record(tenant_id, finalized_at DESC);
CREATE INDEX ix_attempt_tenant_started_submitted ON attempt(tenant_id, started_at DESC, submitted_at DESC);
CREATE INDEX ix_grade_revision_tenant_status_created ON grade_revision(tenant_id, status, created_at DESC);
CREATE INDEX ix_recording_tenant_requested ON recording(tenant_id, requested_at DESC);
CREATE INDEX ix_invoice_tenant_created ON invoice(tenant_id, created_at DESC);
CREATE INDEX ix_payment_tenant_created ON payment(tenant_id, created_at DESC);
CREATE INDEX ix_platform_notification_tenant_created ON platform_notification(tenant_id, created_at DESC);

INSERT INTO role_permission (role_id, permission_key)
SELECT rd.id, x.permission_key FROM role_definition rd JOIN (VALUES
 ('ORGANIZATION_OWNER','ANALYTICS_VIEW'),('ORGANIZATION_OWNER','ANALYTICS_MANAGE'),('ORGANIZATION_OWNER','REPORTS_EXPORT'),('ORGANIZATION_OWNER','OPERATIONS_VIEW'),('ORGANIZATION_OWNER','OPERATIONS_MANAGE'),
 ('ORGANIZATION_ADMINISTRATOR','ANALYTICS_VIEW'),('ORGANIZATION_ADMINISTRATOR','ANALYTICS_MANAGE'),('ORGANIZATION_ADMINISTRATOR','REPORTS_EXPORT'),('ORGANIZATION_ADMINISTRATOR','OPERATIONS_VIEW'),('ORGANIZATION_ADMINISTRATOR','OPERATIONS_MANAGE'),
 ('BRANCH_ADMINISTRATOR','ANALYTICS_VIEW'),('BRANCH_ADMINISTRATOR','REPORTS_EXPORT'),('BRANCH_ADMINISTRATOR','OPERATIONS_VIEW'),
 ('ACADEMIC_ADMINISTRATOR','ANALYTICS_VIEW'),('ACADEMIC_ADMINISTRATOR','ANALYTICS_MANAGE'),('ACADEMIC_ADMINISTRATOR','REPORTS_EXPORT'),('ACADEMIC_ADMINISTRATOR','OPERATIONS_VIEW'),
 ('FINANCE_ADMINISTRATOR','ANALYTICS_VIEW'),('FINANCE_ADMINISTRATOR','REPORTS_EXPORT'),
 ('SUPPORT_OPERATOR','OPERATIONS_VIEW'),('AUDITOR','ANALYTICS_VIEW'),('AUDITOR','REPORTS_EXPORT'),('AUDITOR','OPERATIONS_VIEW')
) AS x(system_key,permission_key) ON rd.system_key=x.system_key ON CONFLICT DO NOTHING;
