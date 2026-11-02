import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const apiCore = readFileSync(fileURLToPath(new URL('../src/platform/api-core.ts', import.meta.url)), 'utf8')
const apiBusiness = readFileSync(fileURLToPath(new URL('../src/platform/api-business.ts', import.meta.url)), 'utf8')
const nativeSmoke = readFileSync(fileURLToPath(new URL('./native/real-analytics-export.native.spec.ts', import.meta.url)), 'utf8')

test('CSV export uses the shared authenticated API transport', () => {
  assert.match(apiCore, /export async function requestText/)
  assert.ok(apiCore.includes("authorizedResponse(path,init,'text/csv')"))
  assert.match(apiCore, /headers\.set\('Authorization',/)
  assert.match(apiCore, /headers\.set\('Accept',accept\)/)

  assert.match(apiBusiness, /import \{ request, requestText \} from '\.\/api-core'/)
  assert.match(apiBusiness, /export const exportAnalytics=.*requestText\(/)
  assert.doesNotMatch(apiBusiness, /\bfetch\s*\(/)
})

test('native Real browser smoke proves API persistence and database-role boundaries', () => {
  assert.match(nativeSmoke, /request\\.method\\(\\) === 'POST'/)
  assert.match(nativeSmoke, /request\\.method\\(\\) === 'GET'/)
  assert.match(nativeSmoke, /public\\.branch/)
  assert.match(nativeSmoke, /current_user/)
  assert.match(nativeSmoke, /__uep_native_runtime_forbidden_probe/)
  assert.match(nativeSmoke, /public\\.flyway_schema_history/)
  assert.match(nativeSmoke, /PGPASSWORD: database\\.password/)
  assert.doesNotMatch(nativeSmoke, /console\\.(?:log|info|debug)\\([^)]*(?:password|token|credential)/i)
})
