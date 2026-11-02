import { expect, test } from '@playwright/test'
import { execFileSync } from 'node:child_process'
import { existsSync, readFileSync } from 'node:fs'
import { join } from 'node:path'

const frontend = 'http://localhost:5173'
const keycloakIssuer = 'http://localhost:8081/realms/education'

type LocalBootstrapState = {
  LocalAdminUsername?: unknown
  LocalAdminPassword?: unknown
  DatabaseName?: unknown
  RuntimeUser?: unknown
  RuntimePassword?: unknown
}

type CurrentIdentity = {
  tenantId: string
  membershipStatus: string
  roles: string[]
  permissions: string[]
}

function loadLocalOwnerCredentials(): { username: string; password: string } {
  const localAppData = process.env.LOCALAPPDATA
  if (!localAppData) {
    throw new Error('Native Windows test requires the same Windows user profile that initialized UEP.')
  }

  let state: LocalBootstrapState
  try {
    const statePath = join(localAppData, 'UEP', 'local-native', 'state.json')
    state = JSON.parse(readFileSync(statePath, 'utf8').replace(/^\uFEFF/, '')) as LocalBootstrapState
  } catch {
    throw new Error('Protected local UEP bootstrap state is unavailable. Start Real mode under this Windows user first.')
  }

  if (
    typeof state.LocalAdminUsername !== 'string' ||
    !state.LocalAdminUsername ||
    typeof state.LocalAdminPassword !== 'string' ||
    !state.LocalAdminPassword
  ) {
    throw new Error('Protected local UEP owner credentials are missing. Re-run the native launcher without displaying the state file.')
  }

  // Credentials remain in process memory only. Never log, attach or persist them.
  return { username: state.LocalAdminUsername, password: state.LocalAdminPassword }
}

function requireLocalRuntimeDatabase(state: LocalBootstrapState): { database: string; username: string; password: string } {
  if (
    typeof state.DatabaseName !== 'string' || !state.DatabaseName ||
    typeof state.RuntimeUser !== 'string' || !state.RuntimeUser ||
    typeof state.RuntimePassword !== 'string' || !state.RuntimePassword
  ) {
    throw new Error('Protected local PostgreSQL runtime settings are missing. The native launcher must initialize the runtime role first.')
  }
  return { database: state.DatabaseName, username: state.RuntimeUser, password: state.RuntimePassword }
}

function resolvePsql(): string {
  const candidates: string[] = []
  const programFiles = process.env.ProgramFiles || 'C:\\Program Files'
  for (const major of ['18', '17']) {
    candidates.push(join(programFiles, 'PostgreSQL', major, 'bin', 'psql.exe'))
  }
  try {
    const pathResults = execFileSync('where.exe', ['psql.exe'], {
      encoding: 'utf8',
      windowsHide: true,
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    candidates.push(...pathResults.split(/\r?\n/).map(value => value.trim()).filter(Boolean))
  } catch {
    // A PATH lookup is optional; use the supported Program Files installations above.
  }

  for (const candidate of [...new Set(candidates)]) {
    if (!existsSync(candidate)) continue
    try {
      const version = execFileSync(candidate, ['--version'], {
        encoding: 'utf8',
        windowsHide: true,
        stdio: ['ignore', 'pipe', 'pipe'],
      })
      if (/PostgreSQL\)\s+(?:17|18)\./.test(version)) return candidate
    } catch {
      // Continue to the next supported client candidate.
    }
  }
  throw new Error('PostgreSQL 17/18 psql.exe was not found. The native launcher prerequisite check must pass before this test.')
}

function runAsRuntimeRole(
  psql: string,
  database: { database: string; username: string; password: string },
  sql: string,
): string {
  return execFileSync(psql, [
    '-X', '-v', 'ON_ERROR_STOP=1', '-A', '-t', '-F', '|',
    '-h', '127.0.0.1', '-p', '5432', '-U', database.username, '-d', database.database,
    '-c', sql,
  ], {
    encoding: 'utf8',
    windowsHide: true,
    maxBuffer: 1024 * 1024,
    stdio: ['ignore', 'pipe', 'pipe'],
    env: {
      ...process.env,
      ['PGPASSWORD']: database.password,
      PGCONNECT_TIMEOUT: '5',
    },
  }).trim()
}

function expectRuntimeRoleDenial(
  psql: string,
  database: { database: string; username: string; password: string },
  sql: string,
): void {
  let denied = false
  try {
    runAsRuntimeRole(psql, database, sql)
  } catch {
    denied = true
  }
  expect(denied, 'the restricted Spring Boot database role must not perform this operation').toBe(true)
}

test('real OIDC owner proves authenticated analytics, branch and membership persistence through restricted PostgreSQL', async ({ page, request }) => {
  const credentials = loadLocalOwnerCredentials()
  const browserApiOrigins = new Set<string>()

  page.on('request', request => {
    const url = new URL(request.url())
    if (url.pathname.startsWith('/api/')) browserApiOrigins.add(url.origin)
  })

  const identityResponsePromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/me'
      && response.request().method() === 'GET'
  })
  const authorizationRequestPromise = page.waitForRequest(request => {
    const url = new URL(request.url())
    return url.origin === 'http://localhost:8081'
      && url.pathname === '/realms/education/protocol/openid-connect/auth'
  })

  await page.goto(frontend)
  const authorizationRequest = await authorizationRequestPromise
  const authorizationUrl = new URL(authorizationRequest.url())
  expect(authorizationUrl.searchParams.get('client_id')).toBe('platform-web')
  expect(authorizationUrl.searchParams.get('response_type')).toBe('code')
  expect(authorizationUrl.searchParams.get('code_challenge_method')).toBe('S256')
  expect(authorizationUrl.searchParams.get('code_challenge')).toBeTruthy()
  const username = page.locator('input[name="username"]')
  const password = page.locator('input[name="password"]')
  await expect(username, 'Real mode must redirect to the local Keycloak login form').toBeVisible()
  await username.fill(credentials.username)
  await password.fill(credentials.password)
  await page.locator('#kc-login').click()

  const identityResponse = await identityResponsePromise
  expect(identityResponse.status()).toBe(200)
  const identity = await identityResponse.json() as CurrentIdentity
  expect(identity.membershipStatus).toBe('ACTIVE')
  expect(identity.roles).toContain('Organization Owner')
  expect(identity.permissions).toContain('ANALYTICS_VIEW')
  expect(identity.permissions).toContain('REPORTS_EXPORT')
  expect(identity.tenantId).toBeTruthy()
  await expect(page.locator('.app-frame')).toBeVisible()

  const overviewPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/analytics/overview'
      && response.request().method() === 'GET'
  })
  await page.getByRole('button', { name: 'Analytics & Operations' }).click()
  const overviewResponse = await overviewPromise
  expect(overviewResponse.status()).toBe(200)

  const csvResponsePromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/analytics/exports/attendance'
      && response.request().method() === 'GET'
  })
  const downloadPromise = page.waitForEvent('download')
  await page.getByRole('button', { name: 'Attendance CSV' }).click()
  const [csvResponse, download] = await Promise.all([csvResponsePromise, downloadPromise])

  expect(csvResponse.status()).toBe(200)
  expect(csvResponse.headers()['content-type']).toContain('text/csv')
  expect(await csvResponse.text()).toContain('present,partial,absent,excused,present_seconds')
  expect(download.suggestedFilename()).toBe('uep-attendance-report.csv')

  const requestHeaders = await csvResponse.request().allHeaders()
  // Assert only the presence of a bearer header; never include the token in test output.
  expect(requestHeaders.authorization?.startsWith('Bearer ') === true).toBe(true)
  expect([...browserApiOrigins]).toEqual([frontend])

  const discovery = await request.get(keycloakIssuer + '/.well-known/openid-configuration')
  expect(discovery.status()).toBe(200)
  const metadata = await discovery.json() as { issuer?: string }
  expect(metadata.issuer).toBe(keycloakIssuer)

  // Create a real tenant branch from the authenticated UI, then prove the same
  // committed row survives a browser reload and is readable with the API runtime role.
  await page.getByRole('button', { name: 'Organization', exact: true }).click()
  const branchCode = 'NATIVE-E2E-' + Date.now().toString()
  const branchName = 'Native persistence qualification ' + branchCode

  await page.getByLabel('Code').fill(branchCode)
  await page.getByLabel('Display name').fill(branchName)
  await page.getByLabel('Timezone').fill('Asia/Kolkata')

  const createBranchPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/organization/branches'
      && response.request().method() === 'POST'
  })
  await page.getByRole('button', { name: 'Create branch', exact: true }).click()
  const createBranchResponse = await createBranchPromise
  expect(createBranchResponse.status()).toBe(200)
  const createdBranch = await createBranchResponse.json() as {
    id: string
    code: string
    displayName: string
    timezone: string
    status: string
  }
  expect(createdBranch.code).toBe(branchCode)
  expect(createdBranch.displayName).toBe(branchName)
  expect(createdBranch.status).toBe('ACTIVE')
  expect(createdBranch.id).toMatch(/^[0-9a-f-]{36}$/i)
  test.info().annotations.push({
    type: 'created-branch',
    description: branchCode + ' / ' + createdBranch.id + ' (retained: no branch delete API exists)',
  })

  const createHeaders = await createBranchResponse.request().allHeaders()
  expect(createHeaders.authorization?.startsWith('Bearer ') === true).toBe(true)

  // Register the response waiter before reload so a fast local response cannot be missed.
  const reloadedIdentityPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/me'
      && response.request().method() === 'GET'
  })
  await page.reload()
  const reloadedIdentity = await reloadedIdentityPromise
  expect(reloadedIdentity.status()).toBe(200)
  await expect(page.locator('.app-frame')).toBeVisible()

  const branchReadbackPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/organization/branches'
      && response.request().method() === 'GET'
  })
  await page.getByRole('button', { name: 'Organization', exact: true }).click()
  const branchReadback = await branchReadbackPromise
  expect(branchReadback.status()).toBe(200)
  const branchPage = await branchReadback.json() as {
    items: Array<{ id: string; code: string; displayName: string; status: string }>
  }
  expect(branchPage.items).toContainEqual(expect.objectContaining({
    id: createdBranch.id,
    code: branchCode,
    displayName: branchName,
    status: 'ACTIVE',
  }))

  // The direct PostgreSQL check uses only the restricted runtime login from the
  // protected LocalAppData state. Neither password nor SQL credentials are logged.
  const localStatePath = join(
    process.env.LOCALAPPDATA || '',
    'UEP',
    'local-native',
    'state.json',
  )
  const localState = JSON.parse(readFileSync(localStatePath, 'utf8').replace(/^\uFEFF/, '')) as LocalBootstrapState
  const runtimeDatabase = requireLocalRuntimeDatabase(localState)
  const psql = resolvePsql()
  const roleBoundarySql = [
    'SELECT current_user,',
    '(r.rolcanlogin AND NOT r.rolsuper AND NOT r.rolcreatedb AND NOT r.rolcreaterole',
    "AND NOT r.rolreplication AND NOT r.rolbypassrls)::text,",
    "(NOT has_schema_privilege(current_user, 'public', 'CREATE'))::text,",
    "(NOT has_database_privilege(current_user, current_database(), 'CREATE'))::text",
    'FROM pg_roles r WHERE r.rolname = current_user',
  ].join(' ')
  const roleBoundary = runAsRuntimeRole(psql, runtimeDatabase, roleBoundarySql)
  expect(roleBoundary).toBe(runtimeDatabase.username + '|true|true|true')

  const persistedBranchSql = [
    "SELECT code || '|' || display_name || '|' || status",
    'FROM public.branch',
    "WHERE id = '" + createdBranch.id + "'::uuid",
    "AND tenant_id = '" + identity.tenantId + "'::uuid",
  ].join(' ')
  const persistedRow = runAsRuntimeRole(psql, runtimeDatabase, persistedBranchSql)
  expect(persistedRow).toBe(branchCode + '|' + branchName + '|ACTIVE')

  // Provision a synthetic, role-free tenant membership through the People UI,
  // update it through the authenticated PATCH path, and verify the committed row.
  const membershipSubject = 'native-e2e-' + Date.now().toString()
  const membershipName = 'Native E2E Membership ' + membershipSubject
  const membershipReference = 'NATIVE-PATCH-' + Date.now().toString()

  await page.getByRole('button', { name: 'People', exact: true }).click()
  await page.getByLabel('OIDC subject').fill(membershipSubject)
  await page.getByLabel('Display name').fill(membershipName)
  await page.getByLabel('Email').fill(membershipSubject + '@example.invalid')

  const provisionMembershipPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/identity/memberships'
      && response.request().method() === 'POST'
  })
  await page.getByRole('button', { name: 'Create membership', exact: true }).click()
  const provisionMembershipResponse = await provisionMembershipPromise
  expect(provisionMembershipResponse.status()).toBe(200)
  const createdMembership = await provisionMembershipResponse.json() as {
    membershipId: string
    oidcSubject: string
    membershipStatus: string
    version: number
  }
  expect(createdMembership.membershipId).toMatch(/^[0-9a-f-]{36}$/i)
  expect(createdMembership.oidcSubject).toBe(membershipSubject)
  expect(createdMembership.membershipStatus).toBe('ACTIVE')
  expect(createdMembership.version).toBe(0)
  test.info().annotations.push({
    type: 'created-membership',
    description: membershipSubject + ' / ' + createdMembership.membershipId + ' (retained: no membership-delete API exists)',
  })

  const provisionHeaders = await provisionMembershipResponse.request().allHeaders()
  expect(provisionHeaders.authorization?.startsWith('Bearer ') === true).toBe(true)

  const membershipRow = page.getByRole('row').filter({ hasText: membershipSubject })
  await expect(membershipRow).toBeVisible()

  // Exercise assignment listing, tenant-scoped role assignment and removal.
  const assignRolePromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/identity/roles/assignments'
      && response.request().method() === 'POST'
  })
  await membershipRow.locator('select[name="roleId"]').selectOption({ label: 'Student' })
  await membershipRow.getByRole('button', { name: 'Assign role', exact: true }).click()
  const assignRoleResponse = await assignRolePromise
  expect(assignRoleResponse.status()).toBe(200)
  const createdAssignment = await assignRoleResponse.json() as {
    id: string
    membershipId: string
    roleId: string
    roleName: string
    scopeKind: string
  }
  expect(createdAssignment.id).toMatch(/^[0-9a-f-]{36}$/i)
  expect(createdAssignment.membershipId).toBe(createdMembership.membershipId)
  expect(createdAssignment.roleName).toBe('Student')
  expect(createdAssignment.scopeKind).toBe('TENANT')

  const persistedAssignmentSql = [
    "SELECT count(*)::text || '|' || COALESCE(max(scope_kind), '')",
    'FROM public.role_assignment',
    "WHERE id = '" + createdAssignment.id + "'::uuid",
    "AND tenant_id = '" + identity.tenantId + "'::uuid",
  ].join(' ')
  expect(runAsRuntimeRole(psql, runtimeDatabase, persistedAssignmentSql)).toBe('1|TENANT')
  const assignHeaders = await assignRoleResponse.request().allHeaders()
  expect(assignHeaders.authorization?.startsWith('Bearer ') === true).toBe(true)
  await expect(membershipRow).toContainText('Student')

  const unassignRolePromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/identity/roles/assignments/' + createdAssignment.id
      && response.request().method() === 'DELETE'
  })
  await membershipRow.getByRole('button', { name: 'Remove Student assignment', exact: true }).click()
  const unassignRoleResponse = await unassignRolePromise
  expect(unassignRoleResponse.status()).toBe(204)
  expect(runAsRuntimeRole(psql, runtimeDatabase, persistedAssignmentSql)).toBe('0|')
  const unassignHeaders = await unassignRoleResponse.request().allHeaders()
  expect(unassignHeaders.authorization?.startsWith('Bearer ') === true).toBe(true)
  await expect(membershipRow).not.toContainText('Student')

  // Repeat at branch scope to verify tenant-bound branch assignment integrity.
  const branchAssignmentPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/identity/roles/assignments'
      && response.request().method() === 'POST'
  })
  await membershipRow.locator('select[name="roleId"]').selectOption({ label: 'Student' })
  await membershipRow.locator('select[name="scopeKind"]').selectOption('BRANCH')
  await membershipRow.locator('select[name="scopeId"]').selectOption(createdBranch.id)
  await membershipRow.getByRole('button', { name: 'Assign role', exact: true }).click()
  const branchAssignmentResponse = await branchAssignmentPromise
  expect(branchAssignmentResponse.status()).toBe(200)
  const branchAssignment = await branchAssignmentResponse.json() as {
    id: string
    membershipId: string
    roleName: string
    scopeKind: string
    scopeId: string
  }
  expect(branchAssignment.membershipId).toBe(createdMembership.membershipId)
  expect(branchAssignment.roleName).toBe('Student')
  expect(branchAssignment.scopeKind).toBe('BRANCH')
  expect(branchAssignment.scopeId).toBe(createdBranch.id)
  const persistedBranchAssignmentSql = [
    "SELECT count(*)::text || '|' || COALESCE(max(scope_kind), '') || '|' || COALESCE(max(scope_id::text), '')",
    'FROM public.role_assignment',
    "WHERE id = '" + branchAssignment.id + "'::uuid",
    "AND tenant_id = '" + identity.tenantId + "'::uuid",
  ].join(' ')
  expect(runAsRuntimeRole(psql, runtimeDatabase, persistedBranchAssignmentSql)).toBe('1|BRANCH|' + createdBranch.id)

  const unassignBranchPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/identity/roles/assignments/' + branchAssignment.id
      && response.request().method() === 'DELETE'
  })
  await membershipRow.getByRole('button', { name: 'Remove Student assignment', exact: true }).click()
  const unassignBranchResponse = await unassignBranchPromise
  expect(unassignBranchResponse.status()).toBe(204)
  expect(runAsRuntimeRole(psql, runtimeDatabase, persistedBranchAssignmentSql)).toBe('0||')

  await membershipRow.locator('select[name="scopeKind"]').selectOption('TENANT')
  await membershipRow.locator('select[name="status"]').selectOption('SUSPENDED')
  await membershipRow.locator('input[name="externalReference"]').fill(membershipReference)

  const updateMembershipPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/identity/memberships/' + createdMembership.membershipId
      && response.request().method() === 'PATCH'
  })
  await membershipRow.getByRole('button', { name: 'Save membership', exact: true }).click()
  const updateMembershipResponse = await updateMembershipPromise
  expect(updateMembershipResponse.status()).toBe(200)
  const updatedMembership = await updateMembershipResponse.json() as {
    membershipId: string
    membershipStatus: string
    externalReference: string
    version: number
  }
  expect(updatedMembership.membershipId).toBe(createdMembership.membershipId)
  expect(updatedMembership.membershipStatus).toBe('SUSPENDED')
  expect(updatedMembership.externalReference).toBe(membershipReference)
  expect(updatedMembership.version).toBe(1)

  const updateHeaders = await updateMembershipResponse.request().allHeaders()
  expect(updateHeaders.authorization?.startsWith('Bearer ') === true).toBe(true)

  const membershipReadbackPromise = page.waitForResponse(response => {
    const url = new URL(response.url())
    return url.origin === frontend
      && url.pathname === '/api/v1/identity/memberships'
      && response.request().method() === 'GET'
  })
  await page.reload()
  await expect(page.locator('.app-frame')).toBeVisible()
  await page.getByRole('button', { name: 'People', exact: true }).click()
  const membershipReadback = await membershipReadbackPromise
  expect(membershipReadback.status()).toBe(200)
  const membershipPage = await membershipReadback.json() as {
    items: Array<{ membershipId: string; membershipStatus: string; externalReference: string; version: number }>
  }
  expect(membershipPage.items).toContainEqual(expect.objectContaining({
    membershipId: createdMembership.membershipId,
    membershipStatus: 'SUSPENDED',
    externalReference: membershipReference,
    version: 1,
  }))
  await expect(page.getByRole('row').filter({ hasText: membershipSubject })).toContainText('SUSPENDED')

  const persistedMembershipSql = [
    "SELECT m.status || '|' || COALESCE(m.external_reference, '') || '|' || m.version::text",
    'FROM public.tenant_membership m',
    'JOIN public.user_account u ON u.id = m.user_id',
    "WHERE u.oidc_subject = '" + membershipSubject + "'",
    "AND m.tenant_id = '" + identity.tenantId + "'::uuid",
  ].join(' ')
  const persistedMembership = runAsRuntimeRole(psql, runtimeDatabase, persistedMembershipSql)
  expect(persistedMembership).toBe('SUSPENDED|' + membershipReference + '|1')

  expectRuntimeRoleDenial(psql, runtimeDatabase,
    'CREATE TABLE public.__uep_native_runtime_forbidden_probe (id integer)')
  expectRuntimeRoleDenial(psql, runtimeDatabase,
    'SELECT count(*) FROM public.flyway_schema_history')
})
