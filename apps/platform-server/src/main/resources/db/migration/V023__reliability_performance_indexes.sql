CREATE INDEX IF NOT EXISTS ix_tenant_membership_tenant_status ON tenant_membership(tenant_id, status, id);
CREATE INDEX IF NOT EXISTS ix_enrollment_tenant_status_started ON enrollment(tenant_id, status, enrolled_at DESC);
CREATE INDEX IF NOT EXISTS ix_audit_event_tenant_occurred ON audit_event(tenant_id, occurred_at DESC);
CREATE INDEX IF NOT EXISTS ix_notification_tenant_member_created ON notification(tenant_id, membership_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_notification_outbox_available ON notification_outbox(tenant_id, status, available_at) WHERE processed_at IS NULL AND dead_lettered_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_platform_session_active ON platform_session(tenant_id, expires_at) WHERE revoked_at IS NULL;
CREATE INDEX IF NOT EXISTS ix_recording_lifecycle ON recording(tenant_id, status, ready_at DESC);
CREATE INDEX IF NOT EXISTS ix_live_class_session_status ON live_class(tenant_id, class_session_id, status);
