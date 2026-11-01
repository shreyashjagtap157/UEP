import { expect, request as playwrightRequest, test, type APIRequestContext, type Browser } from '@playwright/test'
import { accounts, backendUrl, baseUrl } from '../../playwright.staging.config'

type Account = {
  username: string
  password: string
  roleName: string
  displayName: string
  tenantId: string
}

type Identity = {
  tenantId: string
  membershipId: string
  oidcSubject: string
  displayName: string
}

type PageResult<T> = { items: T[] }

type Versioned = { id: string; version: number; status?: string }
type CodeRecord = Versioned & { code: string }
type Membership = { membershipId: string; oidcSubject: string; displayName: string }
type Assignment = Versioned & { title: string; status: string }
type Submission = Versioned & {
  assignmentId: string
  membershipId: string
  status: string
  awardedPoints: number | null
  graderFeedback: string | null
}

const confirmation = 'UEP-STAGING-SEED-CONFIRMED'
const expectedScore = 92
const expectedFeedback = 'Staging acceptance: persisted cross-role grading verified.'

test.skip(
  process.env.UEP_STAGING_PRODUCT_E2E_ENABLED !== 'true',
  'Product seeding and cross-role acceptance require the explicit staging product-E2E opt-in.',
)

test('idempotently seed staging academic data and verify assignment submission/grading across real identities', async ({
  browser,
}) => {
  expect(process.env.UEP_STAGING_SEED_CONFIRMATION).toBe(confirmation)
  const expectedTenantId = process.env.UEP_STAGING_SEED_TENANT_ID?.trim()
  expect(expectedTenantId, 'UEP_STAGING_SEED_TENANT_ID must identify the isolated staging tenant').toBeTruthy()
  expect(new URL(baseUrl).hostname).toBe('uep-frontend-testing.pages.dev')
  expect(new URL(backendUrl).hostname).toBe('uep-backend-testing.fly.dev')

  const owner = await authenticate(browser, accounts.organization_owner as Account)
  const teacher = await authenticate(browser, accounts.teacher as Account)
  const student = await authenticate(browser, accounts.student as Account)
  let freshStudent: AuthenticatedIdentity | undefined

  try {
    for (const [label, identity] of [
      ['owner', owner.identity],
      ['teacher', teacher.identity],
      ['student', student.identity],
    ] as const) {
      expect(identity.tenantId, `${label} must resolve to the explicitly configured staging tenant`)
        .toBe(expectedTenantId)
    }

    // This must succeed before the first write. The backend compares live JDBC metadata
    // against the separately configured staging allowlist and requires OPERATIONS_MANAGE.
    const databaseIdentity = await getJson<{
      verified: boolean
      databaseHost: string
      databaseName: string
    }>(owner.api, '/api/v1/platform/staging-database-identity')
    expect(databaseIdentity.verified).toBe(true)
    expect(databaseIdentity.databaseHost).toBeTruthy()
    expect(databaseIdentity.databaseName).toBeTruthy()

    const tenantSuffix = owner.identity.tenantId.replaceAll('-', '').slice(0, 8).toUpperCase()
    const branchCode = `UEPSTG${tenantSuffix}BR`
    const periodCode = `UEPSTG${tenantSuffix}AP`
    const programCode = `UEPSTG${tenantSuffix}PG`
    const courseCode = `UEPSTG${tenantSuffix}CR`
    const subjectCode = `UEPSTG${tenantSuffix}SB`
    const batchCode = `UEPSTG${tenantSuffix}BT`

    const branches = await getJson<PageResult<CodeRecord & { status: string }>>(
      owner.api,
      '/api/v1/organization/branches?page=0&size=100',
    )
    const branch = await ensureCodeRecord(owner.api, '/api/v1/organization/branches', branches.items, branchCode, {
      code: branchCode,
      displayName: `UEP Staging Qualification ${tenantSuffix}`,
      timezone: 'Asia/Kolkata',
    })
    expect(branch.status).toBe('ACTIVE')

    const periods = await getJson<PageResult<CodeRecord & { status: string }>>(
      owner.api,
      '/api/v1/academic-periods?page=0&size=100',
    )
    const period = await ensureCodeRecord(owner.api, '/api/v1/academic-periods', periods.items, periodCode, {
      code: periodCode,
      displayName: `UEP Staging Academic Period ${tenantSuffix}`,
      startsOn: '2020-01-01',
      endsOn: '2099-12-31',
    })
    expect(['PLANNED', 'ACTIVE']).toContain(period.status)

    const programs = await getJson<PageResult<CodeRecord & { status: string }>>(
      owner.api,
      `/api/v1/programs?academicPeriodId=${period.id}&page=0&size=100`,
    )
    const program = await ensureCodeRecord(owner.api, '/api/v1/programs', programs.items, programCode, {
      academicPeriodId: period.id,
      code: programCode,
      displayName: `UEP Staging Program ${tenantSuffix}`,
      description: 'Stable synthetic program used by the staging acceptance workflow.',
    })
    expect(program.status).toBe('ACTIVE')

    const courses = await getJson<PageResult<CodeRecord & { status: string }>>(
      owner.api,
      `/api/v1/courses?programId=${program.id}&page=0&size=100`,
    )
    const course = await ensureCodeRecord(owner.api, '/api/v1/courses', courses.items, courseCode, {
      programId: program.id,
      code: courseCode,
      displayName: `UEP Staging Course ${tenantSuffix}`,
      description: 'Synthetic course used for staging workflow qualification.',
    })
    expect(course.status).toBe('ACTIVE')

    const subjects = await getJson<PageResult<CodeRecord & { status: string }>>(
      owner.api,
      `/api/v1/subjects?courseId=${course.id}&page=0&size=100`,
    )
    const subject = await ensureCodeRecord(owner.api, '/api/v1/subjects', subjects.items, subjectCode, {
      courseId: course.id,
      code: subjectCode,
      displayName: `UEP Staging Subject ${tenantSuffix}`,
      description: 'Synthetic subject used for staging workflow qualification.',
    })
    expect(subject.status).toBe('ACTIVE')

    const batchQuery = new URLSearchParams({
      academicPeriodId: period.id,
      programId: program.id,
      courseId: course.id,
      branchId: branch.id,
      page: '0',
      size: '100',
    })
    const batches = await getJson<PageResult<CodeRecord & { status: string }>>(
      owner.api,
      `/api/v1/batches?${batchQuery.toString()}`,
    )
    const batch = await ensureCodeRecord(owner.api, '/api/v1/batches', batches.items, batchCode, {
      academicPeriodId: period.id,
      programId: program.id,
      courseId: course.id,
      branchId: branch.id,
      code: batchCode,
      displayName: `UEP Staging Batch ${tenantSuffix}`,
      sectionCode: 'STG',
      startsOn: '2020-01-01',
      endsOn: '2099-12-31',
      capacity: 100,
    })
    expect(['PLANNED', 'ACTIVE']).toContain(batch.status)

    const memberships = await getJson<PageResult<Membership>>(
      owner.api,
      '/api/v1/identity/memberships?page=0&size=100',
    )
    const teacherMembership = memberships.items.find(item => item.oidcSubject === teacher.identity.oidcSubject)
    const studentMembership = memberships.items.find(item => item.oidcSubject === student.identity.oidcSubject)
    expect(teacherMembership, 'teacher must have an active tenant membership').toBeTruthy()
    expect(studentMembership, 'student must have an active tenant membership').toBeTruthy()
    expect(teacherMembership?.membershipId).toBe(teacher.identity.membershipId)
    expect(studentMembership?.membershipId).toBe(student.identity.membershipId)

    const enrollments = await getJson<PageResult<{
      id: string
      membershipId: string
      batchId: string
      status: string
    }>>(owner.api, '/api/v1/enrollments?page=0&size=100')
    if (!enrollments.items.some(item =>
      item.membershipId === student.identity.membershipId
      && item.batchId === batch.id
      && item.status === 'ENROLLED')) {
      await postJson(owner.api, '/api/v1/enrollments', {
        membershipId: student.identity.membershipId,
        batchId: batch.id,
        externalReference: `uep-staging-seed:${tenantSuffix}:student`,
      })
    }

    const teacherAssignments = await getJson<PageResult<{
      id: string
      membershipId: string
      batchId: string | null
      status: string
    }>>(owner.api, '/api/v1/teacher-assignments?page=0&size=100')
    if (!teacherAssignments.items.some(item =>
      item.membershipId === teacher.identity.membershipId
      && item.batchId === batch.id
      && item.status === 'ACTIVE')) {
      await postJson(owner.api, '/api/v1/teacher-assignments', {
        membershipId: teacher.identity.membershipId,
        batchId: batch.id,
        assignmentRole: 'TEACHER',
        startsOn: new Date().toISOString().slice(0, 10),
      })
    }

    const assignmentTitle = `UEP Staging Acceptance ${tenantSuffix}`
    const assignmentPage = await getJson<PageResult<Assignment>>(
      teacher.api,
      '/api/v1/assignments?page=0&size=100',
    )
    let assignment = assignmentPage.items.find(item => item.title === assignmentTitle)
    if (!assignment) {
      assignment = await postJson<Assignment>(teacher.api, '/api/v1/assignments', {
        title: assignmentTitle,
        instructions: 'Submit the staging acceptance response. This record is synthetic and safe to reuse.',
        maxPoints: 100,
        weightBasisPoints: 0,
        dueAt: '2099-12-31T23:59:59Z',
      })
    }
    expect(['DRAFT', 'PUBLISHED']).toContain(assignment.status)

    await postJson(
      teacher.api,
      `/api/v1/assignments/${assignment.id}/batches/${batch.id}`,
      {},
    )
    if (assignment.status === 'DRAFT') {
      assignment = await postJson<Assignment>(
        teacher.api,
        `/api/v1/assignments/${assignment.id}/status`,
        { status: 'PUBLISHED', expectedVersion: assignment.version },
      )
    }
    expect(assignment.status).toBe('PUBLISHED')

    const visibleAssignments = await getJson<PageResult<Assignment>>(
      student.api,
      '/api/v1/assignments?page=0&size=100',
    )
    expect(visibleAssignments.items.some(item => item.id === assignment?.id)).toBe(true)

    const mySubmissions = await getJson<PageResult<Submission>>(
      student.api,
      '/api/v1/assignments/my-submissions?page=0&size=100',
    )
    let submission = mySubmissions.items.find(item => item.assignmentId === assignment?.id)
    if (!submission) {
      submission = await postJson<Submission>(
        student.api,
        `/api/v1/assignments/${assignment.id}/my-submission`,
        { textBody: `Staging acceptance response for tenant ${tenantSuffix}.`, resourceId: null },
      )
    } else if (submission.status === 'DRAFT') {
      submission = await postJson<Submission>(
        student.api,
        `/api/v1/assignments/${assignment.id}/my-submission`,
        { textBody: `Staging acceptance response for tenant ${tenantSuffix}.`, resourceId: null },
      )
    }

    let submissions = await getJson<PageResult<Submission>>(
      teacher.api,
      `/api/v1/assignments/${assignment.id}/submissions?page=0&size=100`,
    )
    submission = submissions.items.find(item => item.membershipId === student.identity.membershipId)
    expect(submission, 'teacher must see the exact student's persisted submission').toBeTruthy()
    if (submission?.awardedPoints == null) {
      submission = await putJson<Submission>(
        teacher.api,
        `/api/v1/assignment-submissions/${submission.id}/grade`,
        {
          awardedPoints: expectedScore,
          feedback: expectedFeedback,
          rubricScoresJson: '{}',
          expectedVersion: submission.version,
        },
      )
    } else {
      expect(submission.awardedPoints).toBe(expectedScore)
      expect(submission.graderFeedback).toBe(expectedFeedback)
    }

    // A new browser context and a new OIDC sign-in prove the grade is not merely
    // held in the original browser's client-side state.
    freshStudent = await authenticate(browser, accounts.student as Account)
    expect(freshStudent.identity.tenantId).toBe(expectedTenantId)
    submissions = await getJson<PageResult<Submission>>(
      freshStudent.api,
      '/api/v1/assignments/my-submissions?page=0&size=100',
    )
    const persisted = submissions.items.find(item => item.assignmentId === assignment?.id)
    expect(persisted).toBeTruthy()
    expect(persisted?.awardedPoints).toBe(expectedScore)
    expect(persisted?.graderFeedback).toBe(expectedFeedback)
  } finally {
    await owner.close()
    await teacher.close()
    await student.close()
    await freshStudent?.close()
  }
})

type AuthenticatedIdentity = {
  identity: Identity
  api: APIRequestContext
  close: () => Promise<void>
}

async function authenticate(browser: Browser, account: Account): Promise<AuthenticatedIdentity> {
  const context = await browser.newContext()
  const page = await context.newPage()
  const identityResponsePromise = page.waitForResponse(response => {
    try {
      const url = new URL(response.url())
      return url.origin === new URL(backendUrl).origin
        && url.pathname === '/api/v1/me'
        && response.request().method() === 'GET'
    } catch {
      return false
    }
  })
  await page.goto(baseUrl)
  await expect(page.locator('input[name="username"]')).toBeVisible()
  await page.locator('input[name="username"]').fill(account.username)
  await page.locator('input[name="password"]').fill(account.password)
  await page.locator('#kc-login').click()
  const identityResponse = await identityResponsePromise
  expect(identityResponse.status(), `authenticated identity lookup for ${account.roleName}`).toBe(200)
  const identity = await identityResponse.json() as Identity
  const authorization = await identityResponse.request().headerValue('authorization')
  expect(authorization, 'browser API request must carry the Keycloak bearer token').toMatch(/^Bearer\s+\S+$/)
  const api = await playwrightRequest.newContext({
    baseURL: backendUrl,
    extraHTTPHeaders: { Authorization: authorization as string, Accept: 'application/json' },
  })
  return {
    identity,
    api,
    close: async () => {
      await api.dispose()
      await context.close()
    },
  }
}

async function getJson<T>(api: APIRequestContext, path: string): Promise<T> {
  const response = await api.get(path)
  if (!response.ok()) {
    throw new Error(`GET ${path} failed with HTTP ${response.status()}: ${(await response.text()).slice(0, 1200)}`)
  }
  return await response.json() as T
}

async function postJson<T>(api: APIRequestContext, path: string, data: unknown): Promise<T> {
  const response = await api.post(path, { data })
  if (!response.ok()) {
    throw new Error(`POST ${path} failed with HTTP ${response.status()}: ${(await response.text()).slice(0, 1200)}`)
  }
  return await response.json() as T
}

async function putJson<T>(api: APIRequestContext, path: string, data: unknown): Promise<T> {
  const response = await api.put(path, { data })
  if (!response.ok()) {
    throw new Error(`PUT ${path} failed with HTTP ${response.status()}: ${(await response.text()).slice(0, 1200)}`)
  }
  return await response.json() as T
}

async function ensureCodeRecord<T extends CodeRecord>(
  api: APIRequestContext,
  path: string,
  items: T[],
  code: string,
  data: unknown,
): Promise<T> {
  const existing = items.find(item => item.code === code)
  return existing ?? await postJson<T>(api, path, data)
}
