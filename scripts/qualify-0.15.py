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

# Flyway requires one migration per version; catch collisions before application startup.
migration_dir = ROOT / 'apps/platform-server/src/main/resources/db/migration'
migration_versions: dict[str, list[str]] = {}
for migration_path in migration_dir.glob('V*__*.sql'):
    version_prefix = migration_path.name.split('__', 1)[0]
    if not version_prefix.startswith('V') or not version_prefix[1:].isdigit() or not migration_path.name.endswith('.sql'):
        errors.append(f'invalid Flyway migration filename: {migration_path.name}')
        continue
    migration_versions.setdefault(version_prefix[1:], []).append(migration_path.name)
for migration_version, migration_names in sorted(migration_versions.items()):
    if len(migration_names) > 1:
        errors.append(f'duplicate Flyway migration version {migration_version}: {", ".join(sorted(migration_names))}')
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
    'timeout(Duration.ofMinutes(30))',
    'timeout(Duration.ofSeconds(15))',
]:
    if timeout_contract not in drive_storage:
        errors.append(f'Google Drive adapter is missing bounded HTTP timeout: {timeout_contract}')

s3_storage = text('apps/platform-server/src/main/java/com/universalplatform/storage/S3StorageAdapter.java')
for timeout_contract in [
    'connectionTimeout(Duration.ofSeconds(5))',
    'socketTimeout(Duration.ofMinutes(5))',
    'apiCallAttemptTimeout(Duration.ofMinutes(10))',
    'apiCallTimeout(Duration.ofMinutes(30))',
    '@PreDestroy',
    'client.close()',
]:
    if timeout_contract not in s3_storage:
        errors.append(f'S3 storage adapter is missing bounded lifecycle contract: {timeout_contract}')
if '<artifactId>apache-client</artifactId>' not in text('apps/platform-server/pom.xml'):
    errors.append('S3 storage adapter must declare the explicit Apache HTTP client dependency')

for required in [
    'accessKeyMissing != secretKeyMissing',
    'S3 endpoint must be an absolute HTTP(S) URI',
    'S3 bucket must be configured',
    'S3 access-key and secret-key must both be set or both be blank',
]:
    if required not in s3_storage:
        errors.append(f'S3 storage adapter is missing startup validation: {required}')
if not (ROOT / 'apps/platform-server/src/test/java/com/universalplatform/storage/S3StorageAdapterConfigurationTest.java').exists():
    errors.append('missing S3 endpoint/bucket/credential configuration regression tests')

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

if not (ROOT / 'apps/platform-server/src/test/java/com/universalplatform/security/ApiCredentialLastUsedConcurrencyIntegrationTest.java').exists():
    errors.append('missing concurrent API credential last-used update regression test')
credential_repository = text('apps/platform-server/src/main/java/com/universalplatform/security/ApiCredentialRepository.java')
if 'int touchLastUsedAt(' not in credential_repository:
    errors.append('API credential last-used metadata must update atomically without optimistic-lock contention')

# Malware rejection is a terminal state transition; throwing must not roll that transition back.
upload_service_for_rejection = text('apps/platform-server/src/main/java/com/universalplatform/content/UploadService.java')
if '@Transactional(noRollbackFor = UploadRejectedException.class)' not in upload_service_for_rejection:
    errors.append('malware rejection must commit the ABORTED upload state')
if 'throw new UploadRejectedException("Upload rejected by malware scanner")' not in upload_service_for_rejection:
    errors.append('malware rejection must throw the non-rollback terminal exception')
if not (ROOT / 'apps/platform-server/src/main/java/com/universalplatform/content/UploadRejectedException.java').exists():
    errors.append('missing terminal upload rejection exception type')
if 'malwareRejectionCommitsTerminalAbortAndRemovesStagingFile' not in text('apps/platform-server/src/test/java/com/universalplatform/content/UploadFinalizationCompensationTest.java'):
    errors.append('missing malware rejection terminal-state regression test')

# Payment recording uses its dedicated permission separately from general finance management.
finance_service_for_auth = text('apps/platform-server/src/main/java/com/universalplatform/finance/FinanceService.java')
finance_ui_for_auth = text('apps/web/src/finance/FinanceWorkspace.tsx')
payment_method_start = finance_service_for_auth.find('PaymentView pay(UUID invoiceId,BigDecimal amount,String currency,String settlementReference,boolean settlementConfirmed)')
payment_method_end = finance_service_for_auth.find('@Transactional(readOnly=true) PageView<InvoiceView> listInvoices', payment_method_start)
payment_method = finance_service_for_auth[payment_method_start:payment_method_end] if payment_method_start >= 0 and payment_method_end > payment_method_start else ''
if 'auth.require(PermissionKey.PAYMENTS_MANAGE);' not in payment_method:
    errors.append('recording a payment must require PAYMENTS_MANAGE, not just FINANCE_MANAGE')
if 'requireForUpdate(invoiceId)' not in payment_method:
    errors.append('payment balance checks must use a locked tenant-scoped invoice row')
invoice_repository_for_payments = text('apps/platform-server/src/main/java/com/universalplatform/finance/InvoiceRepository.java')
if '@Lock(LockModeType.PESSIMISTIC_WRITE)' not in invoice_repository_for_payments or 'lockByTenantIdAndId' not in invoice_repository_for_payments:
    errors.append('payment creation must lock the invoice at the tenant/id boundary')
if not (ROOT / 'apps/platform-server/src/test/java/com/universalplatform/finance/ConcurrentInvoicePaymentIntegrationTest.java').exists():
    errors.append('missing concurrent invoice overpayment regression test')
if "const canManagePayments = me.permissions.includes('PAYMENTS_MANAGE')" not in finance_ui_for_auth:
    errors.append('finance UI must gate payment recording on PAYMENTS_MANAGE')

if not (ROOT / 'apps/platform-server/src/test/java/com/universalplatform/finance/FinancePaymentAuthorizationIntegrationTest.java').exists():
    errors.append('missing finance-vs-payment permission separation regression test')
finance_openapi = text('docs/api/openapi.yaml')
if 'required: [amount, currency, settlementReference, confirmSettlement]' not in finance_openapi:
    errors.append('OpenAPI must require settlement reference and confirmation for manual payments')
if 'Record an operator-verified manual settlement' not in finance_openapi:
    errors.append('OpenAPI must describe the manual settlement behavior rather than an online gateway capture')

# Manual payments represent operator-attested external settlement, not an implicit successful gateway.
manual_provider = text('apps/platform-server/src/main/java/com/universalplatform/finance/ManualPaymentProvider.java')
finance_service = text('apps/platform-server/src/main/java/com/universalplatform/finance/FinanceService.java')
finance_controller = text('apps/platform-server/src/main/java/com/universalplatform/finance/FinanceController.java')
payment_migration = text('apps/platform-server/src/main/resources/db/migration/V031__prevent_duplicate_manual_settlement_references.sql')
finance_ui = text('apps/web/src/finance/FinanceWorkspace.tsx')
for required in [
    'A valid external settlement reference is required',
    'Operator-verified manual settlement recorded',
    'not automated',
]:
    if required not in manual_provider:
        errors.append(f'MANUAL payment provider is missing an explicit settlement/refund guard: {required}')
if 'settlementReference' not in finance_service or 'Confirm that the funds have settled' not in finance_service:
    errors.append('finance service must require explicit settlement reference and confirmation')
if '@NotBlank @Size(max=120) String settlementReference' not in finance_controller or '@jakarta.validation.constraints.AssertTrue Boolean confirmSettlement' not in finance_controller:
    errors.append('finance payment request must validate settlement evidence and confirmation')
if 'uq_payment_tenant_provider_reference' not in payment_migration:
    errors.append('duplicate external settlement references must be rejected per tenant/provider')
if not (ROOT / 'apps/platform-server/src/test/java/com/universalplatform/finance/ManualPaymentProviderTest.java').exists():
    errors.append('missing manual payment provider regression tests')
if 'No online payment gateway was contacted.' not in finance_ui:
    errors.append('finance UI must clearly identify manual settlement and avoid implying gateway capture')

payment_method_for_amount = finance_service_for_auth[finance_service_for_auth.find('PaymentView pay(UUID invoiceId'):finance_service_for_auth.find('@Transactional(readOnly=true) PageView<InvoiceView> listInvoices', finance_service_for_auth.find('PaymentView pay(UUID invoiceId'))]
if 'amount.scale()>2' not in payment_method_for_amount:
    errors.append('payment amounts with more than two decimal places must be rejected before provider invocation')
if 'subCentPaymentIsRejectedBeforeCreatingPaymentOrCallingProvider' not in text('apps/platform-server/src/test/java/com/universalplatform/finance/FinancePaymentFailureStateTest.java'):
    errors.append('missing sub-cent payment amount regression test')

# A provider-declared decline must leave a committed FAILED payment row, not roll the whole transaction back.
finance_service_failure = text('apps/platform-server/src/main/java/com/universalplatform/finance/FinanceService.java')
for required in [
    '@Transactional(noRollbackFor = PaymentProcessingException.class)',
    'throw new PaymentProcessingException("Payment authorization failed")',
    'throw new PaymentProcessingException("Payment capture failed")',
]:
    if required not in finance_service_failure:
        errors.append(f'finance provider failure handling is missing durable failure-state contract: {required}')
for rel in [
    'apps/platform-server/src/main/java/com/universalplatform/finance/PaymentProcessingException.java',
    'apps/platform-server/src/main/java/com/universalplatform/finance/FinanceApiExceptionHandler.java',
    'apps/platform-server/src/test/java/com/universalplatform/finance/FinancePaymentFailureStateTest.java',
]:
    if not (ROOT / rel).exists():
        errors.append(f'missing durable payment failure-state component/test: {rel}')

# Persisted webhook failures must use safe categories, not raw transport exceptions/URLs.
webhook_worker = text('apps/platform-server/src/main/java/com/universalplatform/integration/WebhookWorker.java')
for required in [
    'safeFailureMessage(e)',
    'Thread.currentThread().interrupt()',
    'Outbound endpoint validation failed',
    'HTTP transport error',
    'HTTP request timed out',
]:
    if required not in webhook_worker:
        errors.append(f'webhook worker is missing safe failure handling: {required}')
for rel in [
    'apps/platform-server/src/test/java/com/universalplatform/integration/WebhookWorkerFailureRedactionTest.java',
    'apps/platform-server/src/test/java/com/universalplatform/integration/WebhookWorkerTransactionIntegrationTest.java',
]:
    if not (ROOT / rel).exists():
        errors.append(f'missing webhook failure-redaction regression test: {rel}')

external_notification_service = text('apps/platform-server/src/main/java/com/universalplatform/integration/ExternalNotificationService.java')
for required in [
    'Thread.currentThread().interrupt()',
    'External notification delivery failed for tenant {} provider {} (failure type {})',
    'throw new IllegalStateException("External notification delivery failed");',
]:
    if required not in external_notification_service:
        errors.append(f'external notification failure handling is missing safe logging contract: {required}')
if 'throw new IllegalStateException("External notification delivery failed", x)' in external_notification_service:
    errors.append('external notification failures must not attach raw transport exceptions as logged causes')
if not (ROOT / 'apps/platform-server/src/test/java/com/universalplatform/integration/ExternalNotificationFailurePrivacyTest.java').exists():
    errors.append('missing external notification failure privacy regression test')

# Upload provider side effects must be compensated after rollback, with durable retry when delete fails.
storage_cleanup_worker = text('apps/platform-server/src/main/java/com/universalplatform/content/StorageCleanupWorker.java')
storage_cleanup_test = text('apps/platform-server/src/test/java/com/universalplatform/content/StorageCleanupWorkerIntegrationTest.java')
if '@ConditionalOnProperty(prefix = "platform.storage.cleanup", name = "enabled"' not in storage_cleanup_worker:
    errors.append('storage cleanup scheduler must support disabling the managed poller for deterministic integration tests')
if '"platform.storage.cleanup.enabled=false"' not in storage_cleanup_test:
    errors.append('storage cleanup worker integration tests must disable the managed background poller')
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
# Local demo authentication must remain separate from the real OIDC identity query.
app_source = text('apps/web/src/app/App.tsx')
if 'queryFn: localMode ? fetchLocalCurrentIdentity : fetchCurrentIdentity' in app_source or 'fetchLocalCurrentIdentity' in app_source:
    errors.append('local demo must not reuse the mock identity through the main authenticated query')
if 'enabled: oidcAuthenticated && !localMode' not in app_source or 'if (localMode) return <LocalExperience />' not in app_source:
    errors.append('local demo must have an isolated entry point and disabled real-identity query')
if "nav.filter(item => item.visible && (!localMode || item.id === 'overview'))" in app_source or '{localMode ? (' in app_source:
    errors.append('unreachable local-mode dashboard branches should not remain in authenticated UI')

# Shared workspace exposes a grading action only for authorized teaching roles.
shared_dashboard = text('apps/web/src/dashboard/SpecialistLocalDashboard.tsx')
submission_panel_start = shared_dashboard.find('function SubmissionPanel(')
submission_panel_end = shared_dashboard.find('function MenteePanel(', submission_panel_start)
submission_panel = shared_dashboard[submission_panel_start:submission_panel_end] if submission_panel_start >= 0 and submission_panel_end > submission_panel_start else ''
if "profile.role === 'teacher' || profile.role === 'teaching-assistant' || profile.role === 'evaluator'" not in submission_panel:
    errors.append('teacher, teaching assistant, and evaluator must have role-authorized grade controls')
if 'onSubmit={submitAssignment}' not in shared_dashboard or 'function submitAssignment(' not in shared_dashboard:
    errors.append('student submission flow must be wired into the shared workspace')
if "case 'student': return 'Student learning workspace'" not in shared_dashboard:
    errors.append('every core persona must receive a meaningful shared-workspace heading')

# Every advertised local demo persona must be reachable through the sign-in registry.
local_accounts = text('apps/web/src/dashboard/localDemoAccounts.ts')
local_experience = text('apps/web/src/dashboard/LocalExperience.tsx')
specialist_dashboard = text('apps/web/src/dashboard/SpecialistLocalDashboard.tsx')
for required in [
    'export const demoAccounts: LocalAccount[] = DEMO_PROFILES.map(profile => ({',
    'familiarLoginAliases[profile.role] ?? profile.email',
]:
    if required not in local_accounts:
        errors.append(f'local demo registry is missing canonical identity wiring: {required}')
if 'SpecialistLocalDashboard profile={account.profile}' not in local_experience:
    errors.append('every local demo account must route to the common role-aware workspace')
if local_experience.count('activeUserForProfile(loadDemoState(), account.profile)') < 2:
    errors.append('fresh sign-in and restored sessions must both reject inactive demo accounts')
if 'This demo account is inactive.' not in local_experience:
    errors.append('inactive demo sign-in must explain the account status to the user')
if 'suspended demo profiles fail the centralized active-user guard' not in text('apps/web/test/LocalDemoStore.test.mjs'):
    errors.append('missing suspended-account identity regression test')
if 'subscribeDemoState(() =>' not in local_experience or "sessionStorage.removeItem('uep-local-account')" not in local_experience:
    errors.append('an active demo session must be revoked when the shared store suspends its account')
if "from './LocalDashboard'" in local_experience or 'from "./LocalDashboard"' in local_experience or 'LocalDemoWorkflowStore' in local_experience:
    errors.append('local demo entry point still splits roles across separate stores')
for role in ['student', 'teacher', 'client', 'developer', 'admin', 'guardian', 'exam-controller', 'finance-admin', 'teaching-assistant', 'evaluator', 'mentor', 'support', 'auditor']:
    if f"'{role}'" not in specialist_dashboard:
        errors.append(f'missing shared dashboard scope for role: {role}')
if 'local demo sign-in exposes all thirteen canonical profiles through one shared workspace' not in text('apps/web/test/LocalDemoStore.test.mjs'):
    errors.append('missing regression test proving all thirteen profiles use the shared demo state')

# Student and guardian academic records must always be constrained by enrollment/course scope.
local_demo_store = text('apps/web/src/dashboard/LocalDemoStore.ts')
local_demo_tests = text('apps/web/test/LocalDemoStore.test.mjs')
if "item.studentId === profile.userId" not in local_demo_store or "enrolledCourseIds.has(item.courseId)" not in local_demo_store:
    errors.append('student submissions must be restricted to their own enrolled courses')
if "item.studentId === linkedLearner.id" not in local_demo_store or "enrolledCourseIds.has(item.courseId)" not in local_demo_store:
    errors.append('guardian submissions must be restricted to the linked learner enrollment')
if 'student, guardian and mentor submission lists exclude non-enrolled course records' not in local_demo_tests:
    errors.append('missing regression test for student/guardian enrollment-scoped submissions')
if "item.status === 'Assigned' && !item.examScheduleId" not in local_demo_store:
    errors.append('generic student assignment submission must not mutate dedicated exam records')
if 'export function submitDemoExam' not in local_demo_store or 'export function gradeDemoExamSubmission' not in local_demo_store:
    errors.append('dedicated exam submit and result workflows must be implemented in the shared store')
local_specialist_for_exam = text('apps/web/src/dashboard/SpecialistLocalDashboard.tsx')
if 'Submit exam attempt' not in local_specialist_for_exam or 'Publish exam result (80%)' not in local_specialist_for_exam:
    errors.append('student and exam-controller dashboards must wire the dedicated exam workflows')
if 'exam attempts and results use the exact assigned exam session' not in local_demo_tests:
    errors.append('missing dedicated exam submit/result authorization regression')
if 'learner.organization !== user.organization' not in local_demo_store:
    errors.append('grading and attendance mutations must enforce the active instructor organization')
if 'item.submissionId !== undefined' not in local_demo_store or 'legacySubmissionMatches' not in local_demo_store:
    errors.append('activity timelines must use record identity instead of duplicate display titles')
if 'activity timelines bind repeated assignment titles to learner and submission identity' not in local_demo_tests:
    errors.append('missing duplicate-title activity isolation regression test')
if 'const legacyAttendanceMatches = (item: DemoActivity): boolean =>' not in local_demo_store:
    errors.append('legacy attendance events must be resolved through unique attendance records')
if 'const legacyProgressMatches = (item: DemoActivity): boolean =>' not in local_demo_store:
    errors.append('legacy progress events must be resolved through unique learner/course records')
if 'const legacyProductActivityMatches = (item: DemoActivity): boolean =>' not in local_demo_store:
    errors.append('legacy product activity must resolve to one exact product title')
if 'legacy product activity hides ambiguous duplicate product titles' not in local_demo_tests:
    errors.append('missing legacy product activity ambiguity regression')
if 'const candidates = state.organizationActions.filter(candidate =>' not in local_demo_store:
    errors.append('legacy organization-action events must be matched to one exact title and organization')
if 'const candidates = state.courses.filter(course => course.status === \'Published\'' not in local_demo_store:
    errors.append('legacy course-publication events must be matched to one exact published course title')
if 'legacy organization and course-published events hide ambiguous duplicate titles' not in local_demo_tests:
    errors.append('missing ambiguous legacy organization-action/course-publication regression')
for test_name in ['legacy attendance activity hides ambiguous duplicate learner and course titles', 'legacy learning-progress activity requires a unique learner and scoped course match']:
    if test_name not in local_demo_tests:
        errors.append(f'missing legacy activity ambiguity regression: {test_name}')

# Exam-controller access must be tied to an explicitly linked exam session, not merely its course.
local_demo_store = text('apps/web/src/dashboard/LocalDemoStore.ts')
if 'examScheduleId?: string' not in local_demo_store:
    errors.append('exam submissions must have an explicit exam session link')
if 'const session = item.examScheduleId ? assignedExamSessions.get(item.examScheduleId) : undefined' not in local_demo_store:
    errors.append('exam-controller submissions must resolve the exact assigned exam session')
if 'session.courseId === item.courseId' not in local_demo_store:
    errors.append('exam submissions must match the exact exam-session course')
if "item.kind !== 'Exam'" not in local_demo_store or 'item.participantIds.includes(user.id)' not in local_demo_store:
    errors.append('teaching assistants and evaluators must not see unassigned exam sessions in course schedule')
if 'course assignment alone must not reveal a dedicated exam schedule to evaluators' not in local_demo_tests:
    errors.append('missing schedule isolation regression for course-assigned non-exam roles')
if '!item.examScheduleId' not in local_demo_store or 'assigned.has(item.courseId)' not in local_demo_store:
    errors.append('ordinary staff grading must exclude exam records and enforce assigned courses')
if "examRows.every((item) => item.courseId === 'course-data' && item.examScheduleId === 'schedule-sql-exam')" not in text('apps/web/test/LocalDemoStore.test.mjs'):
    errors.append('missing regression test for strict exam-session submission scope')

# Specialist local personas must have real seeded finance/support records and safe schema migration.
local_demo_store = text('apps/web/src/dashboard/LocalDemoStore.ts')
local_specialist = text('apps/web/src/dashboard/SpecialistLocalDashboard.tsx')
local_demo_tests = text('apps/web/test/LocalDemoStore.test.mjs')
if 'while (state.products.some(existing => existing.id === id))' not in local_demo_store:
    errors.append('product creation must avoid duplicate IDs within the shared dataset')
if 'rapid product creation allocates unique IDs even when the clock has not advanced' not in local_demo_tests:
    errors.append('missing rapid product ID collision regression')
for required in [
    "const STORAGE_KEY = 'uep.local-demo-data.v4'",
    'invoices: Array.isArray(parsed.invoices) ? parsed.invoices : seed.invoices',
    'supportCases: Array.isArray(parsed.supportCases) ? parsed.supportCases : seed.supportCases',
    'messages: Array.isArray(parsed.messages) ? parsed.messages : seed.messages',
    "actor.role !== 'support' || !activeActor(state, actor)",

]:
    if required not in local_demo_store:
        errors.append(f'local specialist state is missing migration or authorization invariant: {required}')
if 'export function messagesForProfile' not in local_demo_store or 'export function markDemoMessageRead' not in local_demo_store:
    errors.append('shared inbox must use role-scoped message queries and recipient-level read state')
if 'studentIds.has(invoice.studentId)' not in local_demo_store or 'invoice.organization === financeUser.organization' not in local_demo_store:
    errors.append('finance demo invoices must match both organization and student owner scope')
if "courseFor(state, item.courseId)?.status === 'Published'" not in local_demo_store:
    errors.append('learner academic queries must hide unpublished course records')
if 'draft-course records are hidden from learner workflows and cannot be mutated' not in local_demo_tests:
    errors.append('missing draft-course academic record visibility and mutation regression')
if 'invoice-owner-not-student' not in local_demo_tests or 'invoice-owner-other-org' not in local_demo_tests:
    errors.append('missing invoice owner and organization isolation regression')
if 'export function scheduleForProfile' not in local_demo_store or 'export function attendanceForProfile' not in local_demo_store:
    errors.append('schedule and attendance access must use central role-scoped query helpers')
if 'const linkedLearner = linkedLearnerForProfile(state, profile)' not in local_demo_store:
    errors.append('guardian/mentor submissions must reuse the centralized relationship guard')
for helper in ['activeUserForProfile', 'linkedLearnerForProfile', 'learnersForProfile', 'coursesForProfile']:
    if f'export function {helper}' not in local_demo_store:
        errors.append(f'missing centralized profile access query: {helper}')
if "['teacher', 'teaching-assistant', 'evaluator'].includes(profile.role)" not in local_demo_store:
    errors.append('assigned learner scope must include teachers, teaching assistants and evaluators')
if "profile.role === 'teaching-assistant' || profile.role === 'evaluator'" not in local_specialist:
    errors.append('teaching assistant and evaluator analytics must use their scoped learner query')
if 'teaching assistants and evaluators receive scoped learner counts' not in local_demo_tests:
    errors.append('missing scoped learner-count regression for teaching assistants and evaluators')
for helper in ['learnersForProfile(state, profile)', 'coursesForProfile(state, profile)', 'linkedLearnerForProfile(state, profile)']:
    if helper not in local_specialist:
        errors.append(f'shared dashboard bypasses centralized identity/course scope helper: {helper}')
if 'learner and course catalog queries enforce active organization and relationship scope' not in local_demo_tests:
    errors.append('missing learner/catalog organization and relationship isolation regression')
if 'scheduleForProfile(state, profile)' not in local_specialist or 'attendanceForProfile(state, profile)' not in local_specialist:
    errors.append('the shared dashboard must consume central schedule and attendance scope helpers')
if 'schedule and attendance queries enforce active identity, enrollment and organization scope' not in local_demo_tests:
    errors.append('missing schedule/attendance identity, enrollment and organization scope regression')
for required in ['invoices.map', 'state.supportCases.map', 'updateDemoSupportCase(fresh, caseId, status, profile)', 'messagesForProfile(state, profile)', 'markDemoMessageRead(fresh, messageId, profile)']:
    if required not in local_specialist:
        errors.append(f'specialist dashboard is not wired to finance/support state: {required}')
for required in ['legacy browser state migrates to v4 with finance, support and inbox backfills', 'shared inbox is recipient-scoped and read state persists per recipient', 'finance ledger is seeded and support case transitions are role-scoped and persistent']:
    if required not in local_demo_tests:
        errors.append(f'missing finance/support regression test: {required}')

# Retire the disconnected six-role local demo implementation after consolidation.
obsolete_local_demo_sources = [
    'LocalDashboard.tsx',
    'LocalDemoControls.tsx',
    'LocalDemoWorkflowStore.ts',
    'localDemoData.ts',
]
for filename in obsolete_local_demo_sources:
    if (ROOT / 'apps/web/src/dashboard' / filename).exists():
        errors.append(f'obsolete split-store demo source must be removed: {filename}')
if "from './LocalDashboard'" in local_experience or 'from "./LocalDashboard"' in local_experience or 'LocalDemoWorkflowStore' in local_experience:
    errors.append('the local demo entry point must not import disconnected core-role components')

# Learning outcomes and mastery records must preserve tenant and domain ownership coherence.
learning_service = text('apps/platform-server/src/main/java/com/universalplatform/learning/LearningService.java')
if 'mappingTargetTable(targetType)' not in learning_service or 'require(t, targetId, targetTable)' not in learning_service:
    errors.append('learning outcome mappings must validate the target within the active tenant')
if 'pathItemTargetTable(itemType)' not in learning_service or 'require(t, itemId, targetTable)' not in learning_service:
    errors.append('learning path items must validate their targets within the active tenant')
if 'requireCompetencyForOutcome(t, competencyId, outcomeId)' not in learning_service:
    errors.append('competency mastery must reject competencies owned by a different outcome')
if '!Double.isFinite(progress)' not in learning_service:
    errors.append('learning path progress must reject NaN and infinite values')
for action in ['LEARNING_PATH_PROGRESS_RECORDED', 'COMPETENCY_MASTERY_RECORDED']:
    if action not in learning_service:
        errors.append(f'learning outcome mutation must emit an audit event: {action}')
learning_integrity_migration = ROOT / 'apps/platform-server/src/main/resources/db/migration/V032__enforce_learning_mastery_outcome_coherence.sql'
if not learning_integrity_migration.exists():
    errors.append('missing V032 learning mastery outcome-coherence constraints')
else:
    migration_text = learning_integrity_migration.read_text(encoding='utf-8')
    for required in ['fk_mastery_competency_outcome', 'UPDATE competency_mastery AS cm', 'uq_competency_tenant_outcome_id']:
        if required not in migration_text:
            errors.append(f'V032 missing integrity contract: {required}')
learning_integrity_test_path = ROOT / 'apps/platform-server/src/test/java/com/universalplatform/learning/LearningServiceIntegrityTest.java'
if not learning_integrity_test_path.exists():
    errors.append('missing learning mapping/mastery ownership regression tests')
else:
    integrity_test_text = learning_integrity_test_path.read_text(encoding='utf-8')
    for required in ['pathItemRejectsTargetThatDoesNotExistInTheActiveTenant', 'progressRejectsNonFiniteValuesBeforePersisting', 'successfulProgressUpdateIsAudited', 'successfulMasteryUpdateIsAudited']:
        if required not in integrity_test_text:
            errors.append(f'learning integrity regression test missing: {required}')

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
