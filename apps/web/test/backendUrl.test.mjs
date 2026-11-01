import assert from 'node:assert/strict'
import test from 'node:test'
import { joinBackendUrl, resolveBackendBaseUrl } from '../src/platform/api-url.ts'

test('production defaults to same-origin API routing', () => {
  assert.equal(resolveBackendBaseUrl(undefined, false, undefined), '')
  assert.equal(joinBackendUrl('', '/api/v1/me'), '/api/v1/me')
})

test('explicit backend URL is preserved without trailing slashes', () => {
  assert.equal(resolveBackendBaseUrl('https://api.example.test///', false, undefined), 'https://api.example.test')
  assert.equal(joinBackendUrl('https://api.example.test', '/api/v1/me'), 'https://api.example.test/api/v1/me')
})

test('local mode retains localhost fallback', () => {
  assert.equal(resolveBackendBaseUrl(undefined, true, undefined), 'http://localhost:8080')
  assert.equal(resolveBackendBaseUrl(undefined, true, 'http://127.0.0.1:9000/'), 'http://127.0.0.1:9000')
})

test('relative API paths are rejected', () => {
  assert.throws(() => joinBackendUrl('', 'api/v1/me'), /must start with/)
})
