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
if 'enabled: ${EMAIL_NOTIFICATIONS_ENABLED:false}' not in app:
    errors.append('mail health must be tied to EMAIL_NOTIFICATIONS_ENABLED so optional SMTP cannot make disabled mail unhealthy')

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
    'cannot be enabled with the prod/production Spring profile',
]
for required in required_guard_contract:
    if required not in guard:
        errors.append(f'ProductionSecretGuard is missing fail-closed validation: {required}')
if not (ROOT / 'apps/platform-server/src/test/java/com/universalplatform/security/ProductionSecretGuardTest.java').exists():
    errors.append('missing ProductionSecretGuard regression tests')

# The destructive restore drill must protect its source and require explicit disposable-target consent.
restore_script = text('scripts/backup-restore-drill.sh')
required_restore_contract = [
    'RESTORE_TARGET_IS_DISPOSABLE',
    '[[ "$RESTORE_TARGET_IS_DISPOSABLE" != "true" ]]',
    'source_database" == "$restore_database"',
    'source_identity" == "$restore_identity"',
    'if [[ -e "$BACKUP_FILE"',
    'pg_restore --exit-on-error --clean --if-exists',
    "SELECT count(*) FROM flyway_schema_history WHERE success;",
]
for required in required_restore_contract:
    if required not in restore_script:
        errors.append(f'backup/restore drill is missing safety check: {required}')

if not (ROOT / 'scripts/test-backup-restore-drill.sh').exists():
    errors.append('missing backup/restore drill safety regression test')

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

outbox_migration = text('apps/platform-server/src/main/resources/db/migration/V028__durable_webhook_event_outbox.sql')
if 'FOREIGN KEY (tenant_id, source_outbox_id)' not in outbox_migration:
    errors.append('V028 must constrain webhook fan-out to a source notification outbox row in the same tenant')

# Recording retention runs from a scheduled worker over detached JPA snapshots. Require an explicit
# transactional reload/save boundary and regression tests so provider deletion cannot leave READY rows forever.
recording_service = text('apps/platform-server/src/main/java/com/universalplatform/recording/RecordingService.java')
if '@Transactional\n    void purgeDue(Recording candidate)' not in recording_service:
    errors.append('recording retention purge must execute in a transaction')
if 'recordings.findByTenantIdAndId(candidate.tenantId(), candidate.id())' not in recording_service:
    errors.append('recording retention purge must reload its candidate inside the transaction')
if 'recordings.save(recording)' not in recording_service:
    errors.append('recording retention purge must persist the final deleted state')
if not (ROOT / 'apps/platform-server/src/test/java/com/universalplatform/recording/RecordingPurgeLifecycleTest.java').exists():
    errors.append('missing recording purge lifecycle regression tests')
drive_storage = text('apps/platform-server/src/main/java/com/universalplatform/storage/GoogleDriveStorageAdapter.java')
if 'r.statusCode()!=404' not in drive_storage:
    errors.append('Google Drive deletes must treat already-missing objects as idempotently deleted')
for timeout_contract in [
    'connectTimeout(Duration.ofSeconds(5))',
    'timeout(Duration.ofMinutes(2))',
    'timeout(Duration.ofSeconds(15))',
]:
    if timeout_contract not in drive_storage:
        errors.append(f'Google Drive adapter is missing bounded HTTP timeout: {timeout_contract}')

# The smoke helper must use a valid nearest-rank percentile and enforce optional latency bounds.
load_probe = text('scripts/load-smoke.py')
for required in [
    'def percentile(sorted_values: list[float], quantile: float)',
    'math.ceil(quantile * len(sorted_values))',
    'if max_p95 is not None and p95 > max_p95',
    'if max_p99 is not None and p99 > max_p99',
]:
    if required not in load_probe:
        errors.append(f'load-smoke probe is missing a percentile/latency contract: {required}')

if not (ROOT / 'scripts/test-load-smoke.py').exists():
    errors.append('missing load-smoke percentile and HTTP status regression tests')

workflow = text('.github/workflows/ci.yml')
for runtime_contract in [
    'Start packaged server on a fresh database and run health smoke',
    'CREATE DATABASE education_platform_smoke OWNER education',
    'python3 scripts/load-smoke.py http://localhost:8080/actuator/health 30 6 2500 4000',
]:
    if runtime_contract not in workflow:
        errors.append(f'CI is missing packaged-server runtime smoke contract: {runtime_contract}')

# API credentials require explicit least-privilege permission scopes and never treat empty as unrestricted.
api_credential_service = text('apps/platform-server/src/main/java/com/universalplatform/security/ApiCredentialService.java')
authorization_service = text('apps/platform-server/src/main/java/com/universalplatform/identity/AuthorizationService.java')
integration_controller = text('apps/platform-server/src/main/java/com/universalplatform/integration/IntegrationController.java')
api_spec = text('docs/api/openapi.yaml')
web_integration = text('apps/web/src/integration/IntegrationWorkspace.tsx')
for required in [
    'At least one explicit API credential permission scope is required',
    'filter(c -> !scopeSet(c.scopes()).isEmpty())',
]:
    if required not in api_credential_service:
        errors.append(f'API credential service is missing empty-scope rejection: {required}')
if 'if (scopedCredential) result.removeIf' not in authorization_service:
    errors.append('authorization must intersect scoped-credential permissions even when the scope set is empty')
if '@NotEmpty Set<PermissionKey> scopes' not in integration_controller:
    errors.append('API credential endpoint must require a non-empty scope set')
if 'required: [membershipId, name, scopes]' not in api_spec or 'minItems: 1' not in api_spec:
    errors.append('OpenAPI API credential schema must require at least one scope')
if 'Select at least one explicit permission scope.' not in web_integration:
    errors.append('integration UI must prevent empty-scope credential creation')
if not (ROOT / 'apps/platform-server/src/test/java/com/universalplatform/identity/IdentityAuthorizationIntegrationTest.java').exists():
    errors.append('missing API credential explicit-scope regression tests')

# Upload provider side effects must be compensated after rollback, with durable retry when delete fails.
storage_cleanup_migration = text('apps/platform-server/src/main/resources/db/migration/V029__storage_cleanup_outbox.sql')
for required in [
    'CREATE TABLE storage_cleanup_outbox',
    'UNIQUE (tenant_id, provider, locator)',
]:
    if required not in storage_cleanup_migration:
        errors.append(f'storage cleanup migration is missing invariant: {required}')
required_cleanup_files = [
    'apps/platform-server/src/main/java/com/universalplatform/content/StorageCleanupJob.java',
    'apps/platform-server/src/main/java/com/universalplatform/content/StorageCleanupRepository.java',
    'apps/platform-server/src/main/java/com/universalplatform/content/StorageCleanupOutboxService.java',
    'apps/platform-server/src/main/java/com/universalplatform/content/StorageCleanupWorker.java',
    'apps/platform-server/src/test/java/com/universalplatform/content/UploadFinalizationCompensationTest.java',
    'apps/platform-server/src/test/java/com/universalplatform/content/StorageCleanupWorkerIntegrationTest.java',
]
for rel in required_cleanup_files:
    if not (ROOT / rel).exists():
        errors.append(f'missing upload compensation component/test: {rel}')
upload_service = text('apps/platform-server/src/main/java/com/universalplatform/content/UploadService.java')
for required in [
    'registerProviderObjectCompensation',
    'TransactionSynchronizationManager.registerSynchronization',
    'storageCleanupOutbox.enqueue(tenantId, provider, locator, objectKey, cleanupFailure)',
    'STATUS_COMMITTED',
]:
    if required not in upload_service:
        errors.append(f'upload finalization is missing rollback compensation contract: {required}')
if 'UploadStatus.EXPIRED' not in text('apps/platform-server/src/main/java/com/universalplatform/content/UploadCleanupWorker.java'):
    errors.append('staging cleanup must retry files left behind by previously expired sessions')

staging_migration = text('apps/platform-server/src/main/resources/db/migration/V030__upload_staging_cleanup_state.sql')
if 'ADD COLUMN staging_cleaned_at timestamptz' not in staging_migration:
    errors.append('upload staging cleanup needs a durable staging_cleaned_at marker')
upload_repository = text('apps/platform-server/src/main/java/com/universalplatform/content/UploadSessionRepository.java')
for required in [
    'AndStagingCleanedAtIsNullAndExpiresAtBeforeOrderByExpiresAtAsc',
    'AndStagingCleanedAtIsNullAndCompletedAtBeforeOrderByCompletedAtAsc',
]:
    if required not in upload_repository:
        errors.append(f'upload cleanup repository is missing filtered cleanup query: {required}')
staging_service = text('apps/platform-server/src/main/java/com/universalplatform/content/UploadStagingCleanupService.java')
if 'Propagation.REQUIRES_NEW' not in staging_service or 'staging_cleaned_at = CURRENT_TIMESTAMP' not in staging_service:
    errors.append('committed staging cleanup must persist its marker in a new transaction')

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
