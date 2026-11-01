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
assert frontend['installCommand'] == 'npm ci --ignore-scripts'
assert frontend['buildCommand'] == 'npm run build'
assert frontend['outputDirectory'] == 'dist'
assert frontend['rewrites'] == [{'source': '/(.*)', 'destination': '/index.html'}]
assert backend['root'] == 'apps/platform-server'
assert backend['runtime'] == 'container'
assert backend['entrypoint'] == 'Dockerfile'

rewrites = config.get('rewrites', [])
assert len(rewrites) == 2, rewrites
assert rewrites[0] == {'source': '/api/(.*)', 'destination': {'service': 'backend'}}
assert rewrites[1] == {'source': '/(.*)', 'destination': {'service': 'frontend'}}

api_core = (root / 'apps/web/src/platform/api-core.ts').read_text(encoding='utf-8')
api_url = (root / 'apps/web/src/platform/api-url.ts').read_text(encoding='utf-8')
vite_config = (root / 'apps/web/vite.config.ts').read_text(encoding='utf-8')
assert 'uep-backend-testing.fly.dev' not in api_core
assert 'resolveBackendBaseUrl' in api_core
assert 'return \'\'' in api_url
assert "'/api'" in vite_config
assert 'rewrite:' not in vite_config, 'Local Vite proxy must preserve /api prefix'

print('Vercel service routing and same-origin API contract passed.')
