import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import test from 'node:test'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const here = dirname(fileURLToPath(import.meta.url))
const script = resolve(here, '../scripts/verify-deployment-env.mjs')
const valid = {
  VITE_LOCAL_MODE: 'false',
  VITE_KEYCLOAK_URL: 'https://identity.example.test',
  VITE_KEYCLOAK_REALM: 'education',
  VITE_KEYCLOAK_CLIENT_ID: 'platform-web',
}

function run(overrides = {}) {
  return spawnSync(process.execPath, [script], {
    encoding: 'utf8',
    env: { ...process.env, ...valid, ...overrides },
  })
}

test('accepts real OIDC and same-origin Vercel configuration', () => {
  const result = run()
  assert.equal(result.status, 0, result.stderr + result.stdout)
})

test('rejects missing or enabled demo mode', () => {
  for (const value of [undefined, 'true', '1']) {
    const result = run({ VITE_LOCAL_MODE: value })
    assert.notEqual(result.status, 0)
    assert.match(result.stderr, /VITE_LOCAL_MODE must be exactly false/)
  }
})

test('rejects missing OIDC build configuration', () => {
  const result = run({ VITE_KEYCLOAK_URL: '', VITE_KEYCLOAK_REALM: '', VITE_KEYCLOAK_CLIENT_ID: '' })
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /VITE_KEYCLOAK_URL must be set/)
})

test('rejects non-HTTPS and localhost identity endpoints', () => {
  for (const url of ['http://identity.example.test', 'https://localhost:8080']) {
    const result = run({ VITE_KEYCLOAK_URL: url })
    assert.notEqual(result.status, 0)
  }
})

test('rejects cross-origin API URLs in single-origin Vercel mode', () => {
  const result = run({ VITE_BACKEND_URL: 'https://api.example.test' })
  assert.notEqual(result.status, 0)
  assert.match(result.stderr, /VITE_BACKEND_URL must be unset/)
})

test('rejects a realm or browser client different from the declared product identity contract', () => {
  const wrongRealm = run({ VITE_KEYCLOAK_REALM: 'master' })
  assert.notEqual(wrongRealm.status, 0)
  assert.match(wrongRealm.stderr, /VITE_KEYCLOAK_REALM must be exactly education/)

  const wrongClient = run({ VITE_KEYCLOAK_CLIENT_ID: 'another-client' })
  assert.notEqual(wrongClient.status, 0)
  assert.match(wrongClient.stderr, /VITE_KEYCLOAK_CLIENT_ID must be exactly platform-web/)
})
