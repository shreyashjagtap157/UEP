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
  assert.ok(nativeSmoke.includes("request.method() === 'POST'"))
  assert.ok(nativeSmoke.includes("request.method() === 'GET'"))
  assert.ok(nativeSmoke.includes('FROM public.branch'))
  assert.ok(nativeSmoke.includes('current_user'))
  assert.ok(nativeSmoke.includes('__uep_native_runtime_forbidden_probe'))
  assert.ok(nativeSmoke.includes('public.flyway_schema_history'))
  assert.ok(nativeSmoke.includes('PGPASSWORD: database.password'))
  assert.ok(!nativeSmoke.includes('console.log('))
  assert.ok(!nativeSmoke.includes('console.info('))
  assert.ok(!nativeSmoke.includes('console.debug('))
})
