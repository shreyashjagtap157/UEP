import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const apiCore = readFileSync(fileURLToPath(new URL('../src/platform/api-core.ts', import.meta.url)), 'utf8')
const apiBusiness = readFileSync(fileURLToPath(new URL('../src/platform/api-business.ts', import.meta.url)), 'utf8')
const nativeSmoke = readFileSync(fileURLToPath(new URL('./native/real-analytics-export.native.spec.ts', import.meta.url)), 'utf8')
const appSource = readFileSync(fileURLToPath(new URL('../src/app/App.tsx', import.meta.url)), 'utf8')

test('CSV export uses the shared authenticated API transport', () => {
  assert.match(apiCore, /export async function requestText/)
  assert.ok(apiCore.includes("authorizedResponse(path,init,'text/csv')"))
  assert.match(apiCore, /headers\.set\('Authorization',/)
  assert.match(apiCore, /headers\.set\('Accept',accept\)/)

  assert.match(apiBusiness, /import \{ request, requestText \} from '\.\/api-core'/)
  assert.match(apiBusiness, /export const exportAnalytics=.*requestText\(/)
  assert.doesNotMatch(apiBusiness, /\bfetch\s*\(/)
})

test('membership editing uses authenticated PATCH and optimistic versioning', () => {
  assert.match(apiCore, /export function updateMembership/)
  assert.ok(apiCore.includes("method:'PATCH'"))
  assert.ok(apiCore.includes('/api/v1/identity/memberships/'))
  assert.ok(appSource.includes('updateMembership'))
  assert.ok(appSource.includes('expectedVersion: member.version'))
  assert.ok(appSource.includes('name="status"'))
  assert.ok(appSource.includes('name="externalReference"'))
})

test('membership role assignments use authenticated tenant-scoped API operations', () => {
  assert.match(apiCore, /export const fetchRoleAssignments=/)
  assert.ok(apiCore.includes('/api/v1/identity/roles/memberships/'))
  assert.match(apiCore, /export function assignRole/)
  assert.ok(apiCore.includes("method:'POST'"))
  assert.ok(apiCore.includes('/api/v1/identity/roles/assignments'))
  assert.match(apiCore, /export function unassignRole/)
  assert.ok(apiCore.includes("method:'DELETE'"))
  assert.ok(appSource.includes('MembershipRoleAssignments'))
  assert.ok(appSource.includes("{(has(me, 'ROLES_VIEW') || has(me, 'ROLES_ASSIGN')) && <th>Role assignments</th>}"))
  assert.ok(appSource.includes("{(has(me, 'ROLES_VIEW') || has(me, 'ROLES_ASSIGN')) && <td>"))
  assert.ok(appSource.indexOf('MembershipRoleAssignments') > appSource.indexOf("Manage membership</th>"))
  assert.ok(appSource.includes("has(me, 'ROLES_VIEW')"))
  assert.ok(appSource.includes("has(me, 'ROLES_ASSIGN')"))
  assert.ok(appSource.includes("scopeKind === 'BRANCH' && !scopeId"))
  assert.ok(appSource.includes('Select a branch before assigning a branch-scoped role.'))
})

test('native Real browser smoke proves API persistence and database-role boundaries', () => {
  assert.ok(nativeSmoke.includes("response.request().method() === 'POST'"))
  assert.ok(nativeSmoke.includes("response.request().method() === 'GET'"))
  assert.ok(nativeSmoke.includes('FROM public.branch'))
  assert.ok(nativeSmoke.includes('current_user'))
  assert.ok(nativeSmoke.includes('__uep_native_runtime_forbidden_probe'))
  assert.ok(nativeSmoke.includes('public.flyway_schema_history'))
  assert.ok(nativeSmoke.includes("response.request().method() === 'PATCH'"))
  assert.ok(nativeSmoke.includes("response.request().method() === 'DELETE'"))
  assert.ok(nativeSmoke.includes("createdAssignment.roleName).toBe('Student')"))
  assert.ok(nativeSmoke.includes("createdAssignment.scopeKind).toBe('TENANT')"))
  assert.ok(nativeSmoke.includes("Remove Student assignment"))
  assert.ok(nativeSmoke.includes("response.request().method() === 'POST'"))
  assert.ok(nativeSmoke.includes("FROM public.tenant_membership m"))
  assert.ok(nativeSmoke.includes("JOIN public.user_account u ON u.id = m.user_id"))
  assert.ok(nativeSmoke.includes("membershipStatus: 'SUSPENDED'"))
  assert.ok(nativeSmoke.includes("externalReference: membershipReference"))
  assert.ok(nativeSmoke.includes("expect(updatedMembership.version).toBe(1)"))
  assert.ok(nativeSmoke.includes("['PGPASSWORD']: database.password"))
  assert.ok(!nativeSmoke.includes('console.log('))
  assert.ok(!nativeSmoke.includes('console.info('))
  assert.ok(!nativeSmoke.includes('console.debug('))
})
