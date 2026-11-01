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

type PageResult<T> = { items: T[]; totalPages: number }

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
const productE2eEnabled = process.env.UEP_STAGING_PRODUCT_E2E_ENABLED === 'true'
const dryRun = process.env.UEP_STAGING_SEED_DRY_RUN === 'true'
const writeEnabled = process.env.UEP_STAGING_SEED_DRY_RUN === 'false'

test('staging seeder dry-run verifies target identity and reports planned synthetic records', async ({ browser }) => {
  test.skip(!productE2eEnabled || !dryRun, 'The staging seeder dry-run is an explicit, read-only preflight.')
  expect(process.env.UEP_STAGING_SEED_CONFIRMATION).toBe(confirmation)
  const expectedTenantId = process.env.UEP_STAGING_SEED_TENANT_ID?.trim()
  expect(expectedTenantId).toBeTruthy()
  assertStagingTarget()
  const owner = await authenticate(browser, accounts.organization_owner as Account)
  try {
    expect(owner.identity.tenantId).toBe(expectedTenantId)
    const databaseIdentity = await getJson<{ verified: boolean; databaseHost: string; databaseName: string }>(
      owner.api, '/api/v1/platform/staging-database-identity',
    )
    expect(databaseIdentity.verified).toBe(true)
    const suffix = owner.identity.tenantId.replaceAll('-', '').slice(0, 8).toUpperCase()
    const [branches, periods] = await Promise.all([
      getAllItems<CodeRecord>(owner.api, '/api/v1/organization/branches'),
      getAllItems<CodeRecord>(owner.api, '/api/v1/academic-periods'),
    ])
    const plan = [
      'branch ' + 'UEPSTG' + suffix + 'BR: ' + (branches.some(item => item.code === 'UEPSTG' + suffix + 'BR') ? 'already present' : 'would create'),
      'academic period ' + 'UEPSTG' + suffix + 'AP: ' + (periods.some(item => item.code === 'UEPSTG' + suffix + 'AP') ? 'already present' : 'would create'),
      'program, course, subject, batch, student enrollment, and teacher assignment: create-if-absent after resolving parent IDs',
      'assignment publication, student submission, teacher grading, and fresh-login persistence assertion',
    ]
    console.info('[UEP staging seed dry-run] database=' + databaseIdentity.databaseHost + '/' + databaseIdentity.databaseName + ' tenant=' + expectedTenantId + '\\n' + plan.map(item => '- ' + item).join('\\n'))
  } finally {
    await owner.close()
  }
})

test('idempotently seed staging academic data and verify cross-role assignments, attendance, announcements, assessments, finance, and guardian isolation', async ({
  browser,
}) => {
  test.skip(!productE2eEnabled || !writeEnabled, 'Writes require product E2E opt-in and UEP_STAGING_SEED_DRY_RUN=false explicitly.')
  expect(process.env.UEP_STAGING_SEED_CONFIRMATION).toBe(confirmation)
  const expectedTenantId = process.env.UEP_STAGING_SEED_TENANT_ID?.trim()
  expect(expectedTenantId, 'UEP_STAGING_SEED_TENANT_ID must identify the isolated staging tenant').toBeTruthy()
  assertStagingTarget()

  const owner = await authenticate(browser, accounts.organization_owner as Account)
  const teacher = await authenticate(browser, accounts.teacher as Account)
  const student = await authenticate(browser, accounts.student as Account)
  const finance = await authenticate(browser, accounts.finance_administrator as Account)
  const guardian = await authenticate(browser, accounts.guardian as Account)
  let freshStudent: AuthenticatedIdentity | undefined

  try {
    for (const [label, identity] of [
      ['owner', owner.identity],
      ['teacher', teacher.identity],
      ['student', student.identity],
      ['finance administrator', finance.identity],
      ['guardian', guardian.identity],
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

    const branches = await getAllItems<CodeRecord & { status: string }>(owner.api, '/api/v1/organization/branches')
    const branch = await ensureCodeRecord(owner.api, '/api/v1/organization/branches', branches, branchCode, {
      code: branchCode,
      displayName: `UEP Staging Qualification ${tenantSuffix}`,
      timezone: 'Asia/Kolkata',
    })
    expect(branch.status).toBe('ACTIVE')

    const periods = await getAllItems<CodeRecord & { status: string }>(owner.api, '/api/v1/academic-periods')
    const period = await ensureCodeRecord(owner.api, '/api/v1/academic-periods', periods, periodCode, {
      code: periodCode,
      displayName: `UEP Staging Academic Period ${tenantSuffix}`,
      startsOn: '2020-01-01',
      endsOn: '2099-12-31',
    })
    expect(['PLANNED', 'ACTIVE']).toContain(period.status)

    const programs = await getAllItems<CodeRecord & { status: string }>(owner.api, `/api/v1/programs?academicPeriodId=${period.id}`)
    const program = await ensureCodeRecord(owner.api, '/api/v1/programs', programs, programCode, {
      academicPeriodId: period.id,
      code: programCode,
      displayName: `UEP Staging Program ${tenantSuffix}`,
      description: 'Stable synthetic program used by the staging acceptance workflow.',
    })
    expect(program.status).toBe('ACTIVE')

    const courses = await getAllItems<CodeRecord & { status: string }>(owner.api, `/api/v1/courses?programId=${program.id}`)
    const course = await ensureCodeRecord(owner.api, '/api/v1/courses', courses, courseCode, {
      programId: program.id,
      code: courseCode,
      displayName: `UEP Staging Course ${tenantSuffix}`,
      description: 'Synthetic course used for staging workflow qualification.',
    })
    expect(course.status).toBe('ACTIVE')

    const subjects = await getAllItems<CodeRecord & { status: string }>(owner.api, `/api/v1/subjects?courseId=${course.id}`)
    const subject = await ensureCodeRecord(owner.api, '/api/v1/subjects', subjects, subjectCode, {
      courseId: course.id,
      code: subjectCode,
      displayName: `UEP Staging Subject ${tenantSuffix}`,
      description: 'Synthetic subject used for staging workflow qualification.',
    })
    expect(subject.status).toBe('ACTIVE')

    const batches = await getAllItems<CodeRecord & { status: string }>(owner.api, `/api/v1/batches?${new URLSearchParams({ academicPeriodId: period.id, programId: program.id, courseId: course.id, branchId: branch.id }).toString()}`)
    const batch = await ensureCodeRecord(owner.api, '/api/v1/batches', batches, batchCode, {
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

    const memberships = await getAllItems<Membership>(owner.api, '/api/v1/identity/memberships')
    const teacherMembership = memberships.find(item => item.oidcSubject === teacher.identity.oidcSubject)
    const studentMembership = memberships.find(item => item.oidcSubject === student.identity.oidcSubject)
    expect(teacherMembership, 'teacher must have an active tenant membership').toBeTruthy()
    expect(studentMembership, 'student must have an active tenant membership').toBeTruthy()
    expect(teacherMembership?.membershipId).toBe(teacher.identity.membershipId)
    expect(studentMembership?.membershipId).toBe(student.identity.membershipId)

    const enrollments = await getAllItems<{
      id: string
      membershipId: string
      batchId: string
      status: string
    }>(owner.api, '/api/v1/enrollments')
    if (!enrollments.some(item =>
      item.membershipId === student.identity.membershipId
      && item.batchId === batch.id
      && item.status === 'ENROLLED')) {
      await postJson(owner.api, '/api/v1/enrollments', {
        membershipId: student.identity.membershipId,
        batchId: batch.id,
        externalReference: `uep-staging-seed:${tenantSuffix}:student`,
      })
    }

    const teacherAssignments = await getAllItems<{
      id: string
      membershipId: string
      batchId: string | null
      status: string
    }>(owner.api, '/api/v1/teacher-assignments')
    if (!teacherAssignments.some(item =>
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

    const guardianGradebookResponse = await guardian.api.get(
      `/api/v1/gradebook/${batch.id}/learners/${student.identity.membershipId}`,
    )
    expect(
      guardianGradebookResponse.status(),
      'guardian cross-learner gradebook access must fail closed until a verified relationship model exists',
    ).toBe(403)

    const scheduleTitle = `UEP Staging Attendance ${tenantSuffix}`
    const scheduleSeries = await getAllItems<{
      id: string
      title: string
      status: string
    }>(owner.api, '/api/v1/schedule/series')
    let schedule = scheduleSeries.find(item => item.title === scheduleTitle)
    type ScheduleOccurrence = {
      id: string
      seriesId: string
      classSessionId: string
      batchId: string | null
      status: string
    }
    let occurrence: ScheduleOccurrence | undefined
    if (!schedule) {
      const created = await postJson<{
        series: { id: string; title: string; status: string }
        occurrences: ScheduleOccurrence[]
      }>(owner.api, '/api/v1/schedule/series', {
        kind: 'CLASS',
        title: scheduleTitle,
        description: 'Synthetic staging attendance qualification session.',
        timezone: 'Asia/Kolkata',
        deliveryMode: 'OFFLINE',
        branchId: branch.id,
        batchId: batch.id,
        courseId: course.id,
        subjectId: subject.id,
        primaryTeacherMembershipId: teacher.identity.membershipId,
        roomCode: 'UEP-STAGING',
        startLocal: '2099-12-30T10:00:00',
        durationMinutes: 30,
        recurrenceFrequency: 'NONE',
        recurrenceInterval: 1,
        recurrenceDays: [],
        recurrenceDayOfMonth: null,
        recurrenceUntilLocal: null,
        recurrenceCount: null,
        allowConflicts: false,
        conflictOverrideReason: null,
      })
      schedule = created.series
      occurrence = created.occurrences[0]
    } else {
      const range = new URLSearchParams({
        from: '2099-12-29T00:00:00Z',
        to: '2099-12-31T00:00:00Z',
      })
      const occurrences = await getJson<ScheduleOccurrence[]>(
        owner.api,
        `/api/v1/schedule/occurrences?${range.toString()}`,
      )
      occurrence = occurrences.find(item => item.seriesId === schedule?.id)
    }
    if (!occurrence?.classSessionId) {
      throw new Error('Staging attendance schedule must resolve to a batch-backed class session.')
    }
    expect(schedule?.status).toBe('ACTIVE')
    expect(occurrence.status).toBe('SCHEDULED')
    expect(occurrence.batchId).toBe(batch.id)

    const liveClasses = await getAllItems<{
      id: string
      classSessionId: string
      status: string
    }>(owner.api, '/api/v1/live-classes')
    let liveClass = liveClasses.find(item => item.classSessionId === occurrence?.classSessionId)
    if (!liveClass) {
      liveClass = await postJson<{ id: string; classSessionId: string; status: string }>(owner.api, '/api/v1/live-classes', {
        classSessionId: occurrence.classSessionId,
        attendancePolicy: 'MANUAL',
        minimumAttendanceSeconds: 0,
        attendanceThresholdBasisPoints: 0,
        lowBandwidth: true,
        chatEnabled: false,
      })
    }
    expect(['SCHEDULED', 'LIVE', 'ENDED']).toContain(liveClass.status)
    if (liveClass.status === 'SCHEDULED') {
      liveClass = await postJson<{ id: string; classSessionId: string; status: string }>(owner.api, `/api/v1/live-classes/${liveClass.id}/start`, {})
    }

    const attendancePath = `/api/v1/live-classes/${liveClass.id}/attendance`
    let attendance = await getJson<Array<{
      membershipId: string
      status: string
      finalizedAt: string | null
      source: string
      notes: string | null
    }>>(owner.api, attendancePath)
    const studentAttendance = attendance.find(item => item.membershipId === student.identity.membershipId)
    if (!studentAttendance || studentAttendance.status !== 'PRESENT' || studentAttendance.source !== 'MANUAL') {
      await postNoContent(owner.api, `${attendancePath}/${student.identity.membershipId}`, {
        status: 'PRESENT',
        notes: 'Synthetic staging manual attendance qualification.',
      })
    }
    await postNoContent(owner.api, `${attendancePath}/finalize`, {})
    attendance = await getJson<Array<{
      membershipId: string
      status: string
      finalizedAt: string | null
      source: string
      notes: string | null
    }>>(owner.api, attendancePath)
    const verifiedAttendance = attendance.find(item => item.membershipId === student.identity.membershipId)
    expect(verifiedAttendance?.status).toBe('PRESENT')
    expect(verifiedAttendance?.source).toBe('MANUAL')
    expect(verifiedAttendance?.finalizedAt).toBeTruthy()
    if (liveClass.status === 'LIVE') {
      liveClass = await postJson<{ id: string; classSessionId: string; status: string }>(owner.api, `/api/v1/live-classes/${liveClass.id}/end`, {})
    }

    const questionTitle = 'UEP Staging Question ' + tenantSuffix
    type QuestionRecord = {
      id: string
      title: string
      status: string
      latestVersion: { id: string; type: string }
    }
    const questionCatalog = await getAllItems<QuestionRecord>(owner.api, '/api/v1/questions')
    let question = questionCatalog.find(item => item.title === questionTitle)
    if (!question) {
      question = await postJson<QuestionRecord>(owner.api, '/api/v1/questions', {
        title: questionTitle,
        type: 'SINGLE_MCQ',
        difficulty: 'easy',
        language: 'en',
        payloadJson: JSON.stringify({
          question: 'Which option represents the persisted staging answer?',
          options: [{ id: 'A', text: 'Persisted answer' }, { id: 'B', text: 'Transient answer' }],
          correctAnswer: 'A',
        }),
        positiveMarks: 10,
        negativeMarks: 0,
      })
    }
    if (!question) throw new Error('Staging question creation did not return a record.')
    expect(question.status).toBe('ACTIVE')
    expect(question.latestVersion.type).toBe('SINGLE_MCQ')

    const assessmentTitle = 'UEP Staging Assessment ' + tenantSuffix
    type AssessmentRecord = { id: string; title: string; status: string; version: number }
    const assessmentCatalog = await getAllItems<AssessmentRecord>(owner.api, '/api/v1/assessments')
    let assessment = assessmentCatalog.find(item => item.title === assessmentTitle)
    if (!assessment) assessment = await postJson<AssessmentRecord>(owner.api, '/api/v1/assessments', { title: assessmentTitle })
    if (!assessment) throw new Error('Staging assessment creation did not return a record.')
    expect(['DRAFT', 'PUBLISHED']).toContain(assessment.status)

    type AssessmentVersionRecord = { id: string; assessmentId: string; versionNumber: number }
    const versionPath = '/api/v1/assessments/' + assessment.id + '/versions'
    const versions = await getJson<AssessmentVersionRecord[]>(owner.api, versionPath)
    let assessmentVersion = versions[0]
    if (!assessmentVersion) {
      if (assessment.status !== 'DRAFT') throw new Error('Published assessment has no editable version.')
      assessmentVersion = await postJson<AssessmentVersionRecord>(owner.api, versionPath, {
        durationSeconds: 600,
        maxAttempts: 1,
        totalMarks: 10,
        passMarks: 5,
        shuffleQuestions: false,
        allowBacktracking: true,
        availableFrom: new Date(Date.now() - 3600000).toISOString(),
        availableUntil: '2099-12-31T23:59:59Z',
        settingsJson: '{}',
      })
    }
    if (!assessmentVersion) throw new Error('Staging assessment version is missing.')

    type AssessmentPacket = { questions: Array<{ id: string; questionVersionId: string; payloadJson: string }> }
    const packetPath = '/api/v1/assessment-versions/' + assessmentVersion.id + '/packet'
    let packet = await getJson<AssessmentPacket>(owner.api, packetPath)
    if (!packet.questions.some(item => item.questionVersionId === question.latestVersion.id)) {
      if (assessment.status !== 'DRAFT') throw new Error('Refusing to modify a published assessment version missing its seed question.')
      await postJson(owner.api, '/api/v1/assessment-versions/' + assessmentVersion.id + '/questions', {
        questionVersionId: question.latestVersion.id,
        ordinal: 1,
      })
      packet = await getJson<AssessmentPacket>(owner.api, packetPath)
    }
    await postJson(owner.api, '/api/v1/assessment-versions/' + assessmentVersion.id + '/batches/' + batch.id, {})
    if (assessment.status === 'DRAFT') {
      assessment = await postJson<AssessmentRecord>(
        owner.api,
        '/api/v1/assessments/' + assessment.id + '/publish?expectedVersion=' + assessment.version,
        {},
      )
    }
    expect(assessment.status).toBe('PUBLISHED')
    const authoredQuestion = packet.questions.find(item => item.questionVersionId === question.latestVersion.id)
    if (!authoredQuestion) throw new Error('The assessment authoring packet must contain the seeded question.')
    expect(JSON.parse(authoredQuestion.payloadJson)).toHaveProperty('correctAnswer', 'A')

    const learnerAssessments = await getAllItems<{ id: string; title: string }>(student.api, '/api/v1/assessments')
    expect(learnerAssessments.some(item => item.id === assessment?.id)).toBe(true)
    type AttemptRecord = { id: string; assessmentVersionId: string; status: string }
    const learnerAttempts = await getAllItems<AttemptRecord>(student.api, '/api/v1/attempts/me')
    let attempt = learnerAttempts.find(item => item.assessmentVersionId === assessmentVersion?.id)
    if (!attempt) {
      attempt = await postJson<AttemptRecord>(
        student.api,
        '/api/v1/assessment-versions/' + assessmentVersion.id + '/attempts',
        {},
      )
    }
    if (!attempt) throw new Error('Staging assessment attempt was not created.')
    expect(['IN_PROGRESS', 'SUBMITTED']).toContain(attempt.status)
    if (attempt.status === 'IN_PROGRESS') {
      const learnerPacket = await getJson<AssessmentPacket>(student.api, packetPath)
      const firstQuestion = learnerPacket.questions[0]
      if (!firstQuestion) throw new Error('Published assessment packet has no question.')
      const redacted = JSON.parse(firstQuestion.payloadJson) as Record<string, unknown>
      expect(redacted).not.toHaveProperty('correctAnswer')
      const answerPath = '/api/v1/attempts/' + attempt.id + '/answers/' + firstQuestion.id
      const answerData = {
        payloadJson: JSON.stringify({ answer: 'A' }),
        idempotencyKey: 'uep-staging-' + tenantSuffix + '-assessment-answer',
        clientSequence: 1,
      }
      const saved = await putJson<{ id: string; serverSequence: number }>(student.api, answerPath, answerData)
      const replay = await putJson<{ id: string; serverSequence: number }>(student.api, answerPath, answerData)
      expect(replay.id).toBe(saved.id)
      expect(replay.serverSequence).toBe(saved.serverSequence)
      attempt = await postJson<AttemptRecord>(student.api, '/api/v1/attempts/' + attempt.id + '/submit', {})
    }
    expect(attempt.status).toBe('SUBMITTED')
    const persistedAttempt = await getJson<AttemptRecord>(student.api, '/api/v1/attempts/' + attempt.id)
    expect(persistedAttempt.status).toBe('SUBMITTED')

    const assignmentTitle = `UEP Staging Acceptance ${tenantSuffix}`
    const assignmentPage = await getAllItems<Assignment>(teacher.api, '/api/v1/assignments')
    let assignment = assignmentPage.find(item => item.title === assignmentTitle)
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

    const visibleAssignments = await getAllItems<Assignment>(student.api, '/api/v1/assignments')
    expect(visibleAssignments.some(item => item.id === assignment?.id)).toBe(true)

    const mySubmissions = await getAllItems<Submission>(student.api, '/api/v1/assignments/my-submissions')
    let submission = mySubmissions.find(item => item.assignmentId === assignment?.id)
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

    let submissions = await getAllItems<Submission>(teacher.api, `/api/v1/assignments/${assignment.id}/submissions`)
    submission = submissions.find(item => item.membershipId === student.identity.membershipId)
    if (!submission) {
      throw new Error('Teacher did not see the exact student membership submission after it was submitted.')
    }
    if (submission.awardedPoints == null) {
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

    const announcementTitle = `UEP Staging Announcement ${tenantSuffix}`
    const announcementPage = await getAllItems<{
      id: string
      title: string
      body: string
      status: string
      acknowledgementRequired: boolean
      version: number
    }>(owner.api, '/api/v1/announcements')
    let announcement = announcementPage.find(item => item.title === announcementTitle)
    if (!announcement) {
      announcement = await postJson(owner.api, '/api/v1/announcements', {
        title: announcementTitle,
        body: `Synthetic staging announcement for batch ${batch.code}; acknowledge to complete the recipient workflow.`,
        priority: 'NORMAL',
        publishAt: null,
        expiresAt: '2099-12-31T23:59:59Z',
        acknowledgementRequired: true,
        publishNow: true,
        targets: [{ kind: 'BATCH', id: batch.id }],
      })
    }
    if (!announcement) {
      throw new Error('The staging announcement must be created or found before recipient verification.')
    }
    expect(announcement.status).toBe('PUBLISHED')
    expect(announcement.acknowledgementRequired).toBe(true)

    const studentAnnouncements = await getAllItems<{
      id: string
      title: string
      status: string
      acknowledgedAt: string | null
    }>(student.api, '/api/v1/announcements/me')
    const visibleAnnouncement = studentAnnouncements.find(item => item.id === announcement?.id)
    expect(visibleAnnouncement, 'batch-targeted announcement must be visible to the enrolled student').toBeTruthy()
    if (!visibleAnnouncement) {
      throw new Error('Student did not receive the batch-targeted staging announcement.')
    }
    if (!visibleAnnouncement.acknowledgedAt) {
      await postJson(student.api, `/api/v1/announcements/${announcement.id}/acknowledge`, {})
    }
    const acknowledgedAnnouncements = await getAllItems<{
      id: string
      acknowledgedAt: string | null
    }>(student.api, '/api/v1/announcements/me')
    expect(acknowledgedAnnouncements.find(item => item.id === announcement?.id)?.acknowledgedAt).toBeTruthy()

    const invoiceAmount = Number((10 + (Number.parseInt(tenantSuffix.slice(0, 4), 16) % 9000) / 100).toFixed(2))
    const invoiceDueOn = '2099-12-31'
    type InvoiceRecord = {
      id: string
      membershipId: string
      totalAmount: number
      paidAmount: number
      currency: string
      status: string
      dueOn: string
      version: number
      installments: Array<{ amount: number; dueOn: string; status: string }>
    }
    const invoicePage = await getJson<{
      items: InvoiceRecord[]
      size: number
      totalElements: number
    }>(finance.api, '/api/v1/finance/invoices?page=0&size=100')
    const invoicePages = Math.ceil(invoicePage.totalElements / Math.max(1, invoicePage.size))
    const allInvoices = [...invoicePage.items]
    for (let page = 1; page < invoicePages; page += 1) {
      const nextPage = await getJson<{ items: InvoiceRecord[] }>(
        finance.api,
        '/api/v1/finance/invoices?page=' + page + '&size=100',
      )
      allInvoices.push(...nextPage.items)
    }
    const matchingInvoices = allInvoices.filter(item =>
      item.membershipId === student.identity.membershipId
      && Number(item.totalAmount) === invoiceAmount
      && item.currency === 'INR'
      && item.dueOn === invoiceDueOn)
    expect(matchingInvoices.length).toBeLessThanOrEqual(1)
    let invoice = matchingInvoices[0]
    if (!invoice) {
      invoice = await postJson<InvoiceRecord>(finance.api, '/api/v1/finance/invoices', {
        membershipId: student.identity.membershipId,
        totalAmount: invoiceAmount,
        currency: 'INR',
        dueOn: invoiceDueOn,
        installments: [{ amount: invoiceAmount, dueOn: invoiceDueOn }],
      })
    }
    if (!invoice) throw new Error('Staging finance invoice creation did not return a record.')
    if (invoice.status === 'DRAFT') {
      invoice = await postJson<InvoiceRecord>(
        finance.api,
        '/api/v1/finance/invoices/' + invoice.id + '/issue',
        { expectedVersion: invoice.version },
      )
    }
    expect(invoice.status).toBe('ISSUED')
    const issuedInvoice = await getJson<InvoiceRecord>(finance.api, '/api/v1/finance/invoices/' + invoice.id)
    expect(issuedInvoice.status).toBe('ISSUED')
    expect(Number(issuedInvoice.paidAmount)).toBe(0)
    expect(issuedInvoice.installments).toHaveLength(1)
    expect(issuedInvoice.installments[0].dueOn).toBe(invoiceDueOn)

    const secondaryAccountJson = process.env.UEP_E2E_SECOND_TENANT_TEACHER_ACCOUNT_JSON?.trim()
    if (secondaryAccountJson) {
      const secondaryTenantId = process.env.UEP_STAGING_SECOND_TENANT_ID?.trim()
      expect(secondaryTenantId, 'A second-tenant account requires UEP_STAGING_SECOND_TENANT_ID').toBeTruthy()
      const secondaryAccount = JSON.parse(secondaryAccountJson) as Account
      expect(secondaryAccount.roleName).toBe('Teacher')
      const secondaryTeacher = await authenticate(browser, secondaryAccount)
      try {
        expect(secondaryTeacher.identity.tenantId).toBe(secondaryTenantId)
        expect(secondaryTeacher.identity.tenantId).not.toBe(expectedTenantId)
        const foreignAssignment = await secondaryTeacher.api.get(`/api/v1/assignments/${assignment.id}/submissions?page=0&size=10`)
        expect(foreignAssignment.status(), 'a second-tenant teacher must not enumerate submissions for a primary-tenant assignment').toBe(404)
      } finally {
        await secondaryTeacher.close()
      }
    } else {
      console.warn('Tenant-isolation staging assertion not executed: configure UEP_E2E_SECOND_TENANT_TEACHER_ACCOUNT_JSON and UEP_STAGING_SECOND_TENANT_ID.')
    }

    // A new browser context and a new OIDC sign-in prove the grade is not merely
    // held in the original browser's client-side state.
    freshStudent = await authenticate(browser, accounts.student as Account)
    expect(freshStudent.identity.tenantId).toBe(expectedTenantId)
    submissions = await getAllItems<Submission>(freshStudent.api, '/api/v1/assignments/my-submissions')
    const persisted = submissions.find(item => item.assignmentId === assignment?.id)
    expect(persisted).toBeTruthy()
    expect(persisted?.awardedPoints).toBe(expectedScore)
    expect(persisted?.graderFeedback).toBe(expectedFeedback)
    const freshAttempts = await getAllItems<{ id: string; assessmentVersionId: string; status: string }>(
      freshStudent.api,
      '/api/v1/attempts/me',
    )
    expect(freshAttempts.find(item => item.assessmentVersionId === assessmentVersion?.id)?.status).toBe('SUBMITTED')
    const persistedAnnouncement = await getAllItems<{
      id: string
      acknowledgedAt: string | null
    }>(freshStudent.api, '/api/v1/announcements/me')
    expect(persistedAnnouncement.find(item => item.id === announcement?.id)?.acknowledgedAt).toBeTruthy()
  } finally {
    await owner.close()
    await teacher.close()
    await student.close()
    await finance.close()
    await guardian.close()
    await freshStudent?.close()
  }
})

type AuthenticatedIdentity = {
  identity: Identity
  api: APIRequestContext
  close: () => Promise<void>
}

function assertStagingTarget(): void {
  const allowedFrontendHost = process.env.UEP_STAGING_ALLOWED_FRONTEND_HOST?.trim()
  const allowedBackendHost = process.env.UEP_STAGING_ALLOWED_BACKEND_HOST?.trim()
  expect(allowedFrontendHost, 'UEP_STAGING_ALLOWED_FRONTEND_HOST must be explicitly configured').toBeTruthy()
  expect(allowedBackendHost, 'UEP_STAGING_ALLOWED_BACKEND_HOST must be explicitly configured').toBeTruthy()
  expect(new URL(baseUrl).hostname).toBe(allowedFrontendHost)
  expect(new URL(backendUrl).hostname).toBe(allowedBackendHost)
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

async function getAllItems<T>(api: APIRequestContext, path: string): Promise<T[]> {
  const items: T[] = []
  let page = 0
  let totalPages = 1
  while (page < totalPages) {
    const url = new URL(path, 'https://uep-staging.invalid')
    url.searchParams.set('page', String(page))
    url.searchParams.set('size', '100')
    const result = await getJson<PageResult<T>>(api, url.pathname + url.search)
    items.push(...result.items)
    totalPages = result.totalPages
    page += 1
    if (page > 1000) {
      throw new Error('Staging seed pagination exceeded the 1000-page safety limit.')
    }
  }
  return items
}

async function postJson<T>(api: APIRequestContext, path: string, data: unknown): Promise<T> {
  const response = await api.post(path, { data })
  if (!response.ok()) {
    throw new Error(`POST ${path} failed with HTTP ${response.status()}: ${(await response.text()).slice(0, 1200)}`)
  }
  return await response.json() as T
}

async function postNoContent(api: APIRequestContext, path: string, data: unknown): Promise<void> {
  const response = await api.post(path, { data })
  if (!response.ok()) {
    throw new Error(`POST ${path} failed with HTTP ${response.status()}: ${(await response.text()).slice(0, 1200)}`)
  }
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
