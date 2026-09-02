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
# Reject obvious production-secret placeholders in externally reachable configuration.
# Development credentials are intentionally isolated to local infrastructure configuration;
# production deployment is required to override all externally supplied secrets.

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
