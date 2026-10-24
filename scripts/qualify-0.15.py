#!/usr/bin/env python3
"""Static reliability/performance qualification for the 0.15 milestone."""
from __future__ import annotations
import re, sys
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
errors=[]; warnings=[]

def text(rel): return (ROOT/rel).read_text(encoding='utf-8')
version=(ROOT/'VERSION').read_text().strip()
if version != '0.15.0.0-SNAPSHOT': errors.append(f'VERSION is {version}, expected 0.15.0.0-SNAPSHOT')
app=text('apps/platform-server/src/main/resources/application.yaml')
for needle in ['maximum-pool-size:', 'connection-timeout:', 'max-lifetime:', 'platform:', 'security:']:
    if needle not in app: errors.append(f'application.yaml missing {needle}')
# Production secret validation must fail closed on missing, blank, placeholder, or weak secrets.
guard = text('apps/platform-server/src/main/java/com/universalplatform/security/ProductionSecretGuard.java')
required_guard_contract = [
    'validateProductionSecrets(environment)',
    '"spring.datasource.password"',
    '"platform.recording.playback-secret"',
    '"platform.integrations.secret-key"',
    '"platform.live-learning.livekit.api-key"',
    '"platform.live-learning.livekit.api-secret"',
    'value == null || value.isBlank()',
    'value.equals(devDefault)',
    'value.trim().length() < minimumLength',
]
for required in required_guard_contract:
    if required not in guard:
        errors.append(f'ProductionSecretGuard is missing fail-closed validation: {required}')
if not (ROOT / 'apps/platform-server/src/test/java/com/universalplatform/security/ProductionSecretGuardTest.java').exists():
    errors.append('missing ProductionSecretGuard regression tests')

# Webhook fan-out must be durable, idempotent, and processed independently of the source transaction.
required_webhook_files = [
    'apps/platform-server/src/main/resources/db/migration/V028__durable_webhook_event_outbox.sql',
    'apps/platform-server/src/main/java/com/universalplatform/integration/WebhookEventOutbox.java',
    'apps/platform-server/src/main/java/com/universalplatform/integration/WebhookEventOutboxService.java',
    'apps/platform-server/src/main/java/com/universalplatform/integration/WebhookEventFanoutWorker.java',
    'apps/platform-server/src/test/java/com/universalplatform/integration/WebhookEventOutboxIntegrationTest.java',
]
for rel in required_webhook_files:
    if not (ROOT / rel).exists():
        errors.append(f'missing durable webhook fan-out component: {rel}')
listener = text('apps/platform-server/src/main/java/com/universalplatform/integration/WebhookNotificationListener.java')
if 'WebhookEventOutboxService' not in listener or 'outbox.enqueue(event)' not in listener:
    errors.append('WebhookNotificationListener must persist fan-out intent in the transactional outbox')

# Tenant isolation gate: require tenant-leading indexes for the high-frequency 0.15 query paths.
required_indexes = {
    "ix_tenant_membership_tenant_status", "ix_enrollment_tenant_status_started",
    "ix_audit_event_tenant_occurred", "ix_notification_tenant_member_created",
    "ix_notification_outbox_available", "ix_platform_session_active",
    "ix_recording_lifecycle", "ix_live_class_session_status"
}
v023 = text('apps/platform-server/src/main/resources/db/migration/V023__reliability_performance_indexes.sql')
for index_name in sorted(required_indexes):
    if index_name not in v023:
        errors.append(f'V023 is missing required performance index {index_name}')
# All integration tests that exercise tenant isolation must remain present.
required_tests = [
    'apps/platform-server/src/test/java/com/universalplatform/tenancy/TenantIsolationIntegrationTest.java',
    'apps/platform-server/src/test/java/com/universalplatform/organization/OrganizationIsolationIntegrationTest.java',
]
for rel in required_tests:
    if not (ROOT / rel).exists():
        errors.append(f'missing mandatory isolation test: {rel}')
# PII/secret response anti-patterns in controllers.
for p in (ROOT/'apps/platform-server/src/main/java/com/universalplatform').rglob('*Controller.java'):
    s=p.read_text(encoding='utf-8')
    if re.search(r'(?i)password|clientsecret|apisecret|privatekey', s) and 'return' in s:
        if re.search(r'CredentialView|ProviderView', s):
            continue
        warnings.append(f'{p.relative_to(ROOT)}: inspect controller response for credential-field exposure')
if errors:
    print('0.15 qualification failed:')
    for e in errors: print('  '+e)
    raise SystemExit(1)
print('0.15 qualification checks passed')
if warnings:
    print('qualification warnings:')
    for w in warnings: print('  '+w)
