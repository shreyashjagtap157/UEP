#!/usr/bin/env python3
"""Fail-fast contract checks for the Vercel multi-service deployment."""
from __future__ import annotations

import json
from pathlib import Path

root = Path(__file__).resolve().parents[1]
config = json.loads((root / 'vercel.json').read_text(encoding='utf-8'))
services = config.get('services', {})
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
web_package = json.loads((root / 'apps/web/package.json').read_text(encoding='utf-8'))
assert 'guard-vercel-build.mjs' in web_package['scripts']['build']
assert 'verify-deployment-env.mjs' in web_package['scripts']['build:vercel']
assert config['services']['backend']['routes'][0]['src'] == '/api/_health'


print('Vercel routing, production-mode, and runtime-safety contract passed.')
