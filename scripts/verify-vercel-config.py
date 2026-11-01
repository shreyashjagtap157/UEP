#!/usr/bin/env python3
"""Fail-fast contract checks for the Vercel multi-service deployment."""
from __future__ import annotations

import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
config = json.loads((root / 'vercel.json').read_text(encoding='utf-8'))
services = config.get('services', {})
assert config.get('git', {}).get('deploymentEnabled') is False, 'Automatic Vercel Git deployments must remain disabled.'
assert set(services) == {'frontend', 'backend'}, services

frontend = services['frontend']
backend = services['backend']
assert frontend['root'] == 'apps/web'
assert frontend['framework'] == 'vite'
assert frontend['routes'] == [{
    'src': '/((?!assets/|.*\\.[^/]+$).*)',
    'transforms': [{'type': 'request.path', 'op': 'set', 'args': '/index.html'}],
}]
assert backend['root'] == 'apps/platform-server'
assert backend['runtime'] == 'container'
assert backend['entrypoint'] == 'Dockerfile'

rewrites = config.get('rewrites', [])
assert len(rewrites) == 3, rewrites
assert rewrites[0] == {'source': '/api/_health', 'destination': {'service': 'backend'}}
assert rewrites[1] == {'source': '/api/(.*)', 'destination': {'service': 'backend'}}
assert rewrites[2] == {'source': '/(.*)', 'destination': {'service': 'frontend'}}

api_core = (root / 'apps/web/src/platform/api-core.ts').read_text(encoding='utf-8')
api_url = (root / 'apps/web/src/platform/api-url.ts').read_text(encoding='utf-8')
vite_config = (root / 'apps/web/vite.config.ts').read_text(encoding='utf-8')
assert 'uep-backend-testing.fly.dev' not in api_core
assert 'resolveBackendBaseUrl' in api_core
assert 'return \'\'' in api_url
assert "'/api'" in vite_config
assert 'rewrite:' not in vite_config, 'Local Vite proxy must preserve /api prefix'


app_config = (root / 'apps/platform-server/src/main/resources/application.yaml').read_text(encoding='utf-8')
vercel_profile = (root / 'apps/platform-server/src/main/resources/application-vercel.yaml').read_text(encoding='utf-8')
app_main = (root / 'apps/platform-server/src/main/java/com/universalplatform/PlatformServerApplication.java').read_text(encoding='utf-8')
scheduler = (root / 'apps/platform-server/src/main/java/com/universalplatform/BackgroundWorkerConfiguration.java').read_text(encoding='utf-8')
secret_guard = (root / 'apps/platform-server/src/main/java/com/universalplatform/security/ProductionSecretGuard.java').read_text(encoding='utf-8')
assert 'PLATFORM_BACKGROUND_WORKERS_ENABLED' in app_config
assert 'PLATFORM_LIVEKIT_ENABLED' in app_config
assert 'PLATFORM_UPLOAD_STAGING_ENABLED' in app_config
assert 'enabled: false' in vercel_profile
assert 'spring:\n  # Schema changes are applied only by the separately gated DatabaseMigrationRunner.\n  flyway:\n    enabled: false' in vercel_profile
assert 'background-workers:' in vercel_profile and 'upload-staging:' in vercel_profile and 'livekit:' in vercel_profile
assert '@ConditionalOnProperty' in scheduler and 'platform.background-workers' in scheduler
assert '@EnableScheduling' not in app_main
assert 'platform.live-learning.livekit.enabled' in secret_guard
assert 'validateProductionOidcConfiguration(environment)' in secret_guard
assert 'OIDC_ISSUER_URI' in secret_guard and 'OIDC_JWK_SET_URI' in secret_guard
live_service = (root / 'apps/platform-server/src/main/java/com/universalplatform/liveclass/LiveClassService.java').read_text(encoding='utf-8')
recording_service = (root / 'apps/platform-server/src/main/java/com/universalplatform/recording/RecordingService.java').read_text(encoding='utf-8')
upload_service = (root / 'apps/platform-server/src/main/java/com/universalplatform/content/UploadService.java').read_text(encoding='utf-8')
assert 'PLATFORM_UPLOAD_STAGING_ENABLED' in upload_service or 'platform.storage.upload-staging.enabled' in upload_service
assert 'platform.live-learning.livekit.enabled' in live_service
assert 'platform.live-learning.livekit.enabled' in recording_service
migration_runner = (root / 'apps/platform-server/src/main/java/com/universalplatform/DatabaseMigrationRunner.java').read_text(encoding='utf-8')
migration_workflow = (root / '.github/workflows/database-migration.yml').read_text(encoding='utf-8')
assert 'UEP_DATABASE_RUNTIME_USERNAME' in migration_runner and 'UEP_DATABASE_RUNTIME_USERNAME' in migration_workflow
assert 'NOINHERIT' not in migration_runner  # role creation belongs to the operator; runner validates the role
assert 'rolbypassrls' in migration_runner and 'rolsuper' in migration_runner
assert 'owns_public_objects' in migration_runner and 'can_create_public_objects' in migration_runner
assert 'can_create_database_objects' in migration_runner
assert 'pg_type t JOIN pg_namespace' in migration_runner
assert 'REVOKE ALL PRIVILEGES ON TABLE public.flyway_schema_history' in migration_runner
assert 'GRANT EXECUTE ON FUNCTION public.uuidv7()' in migration_runner
assert 'REVOKE EXECUTE ON ALL FUNCTIONS IN SCHEMA public FROM PUBLIC' in migration_runner
assert 'REVOKE EXECUTE ON FUNCTIONS FROM PUBLIC' in migration_runner
assert 'GRANT USAGE ON TYPES TO' in migration_runner
assert 'grantUsageOnPublicTypes' in migration_runner
assert 'GRANT EXECUTE ON ALL FUNCTIONS' not in migration_runner
assert 'ALTER DEFAULT PRIVILEGES FOR ROLE' in migration_runner
assert 'confirm_database_migration' in migration_workflow and 'default: "no"' in migration_workflow
assert 'UEP_DATABASE_MIGRATION_JDBC_URL' in migration_workflow
web_package = json.loads((root / 'apps/web/package.json').read_text(encoding='utf-8'))
assert 'guard-vercel-build.mjs' in web_package['scripts']['build']
assert 'verify-deployment-env.mjs' in web_package['scripts']['build:vercel']
assert config['services']['backend']['routes'][0]['src'] == '/api/_health'


print('Vercel routing, production-mode, and runtime-safety contract passed.')
