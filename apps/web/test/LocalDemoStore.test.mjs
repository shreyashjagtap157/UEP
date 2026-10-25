import assert from 'node:assert/strict'
import test from 'node:test'
import { demoAccounts } from '../src/dashboard/localDemoAccounts.ts'
import {
  DEMO_PROFILES,
  activityForProfile,
  createDemoProduct,
  activeUserForProfile,
  attendanceForProfile,
  coursesForProfile,
  learnersForProfile,
  scheduleForProfile,
  advanceDemoProgress,
  publishDemoCourse,
  resolveDemoOrganizationAction,
  submissionsForProfile,
  gradeDemoSubmission,
  gradeDemoExamSubmission,
  invoicesForFinanceProfile,
  loadDemoState,
  markDemoMessageRead,
  messagesForProfile,
  resetDemoState,
  saveDemoState,
  setDemoUserStatus,
  submitDemoAssignment,
  submitDemoExam,
  subscribeDemoState,
  updateDemoAttendance,
  updateDemoSupportCase,
} from '../src/dashboard/LocalDemoStore.ts'

const storage = new Map()
const eventListeners = new Map()

const localStorage = {
  getItem(key) {
    return storage.has(key) ? storage.get(key) : null
  },
  setItem(key, value) {
    storage.set(key, String(value))
  },
  removeItem(key) {
    storage.delete(key)
  },
  clear() {
    storage.clear()
  },
}

globalThis.localStorage = localStorage

globalThis.window = {
  localStorage,
  addEventListener(type, listener) {
    const listeners = eventListeners.get(type) ?? new Set()
    listeners.add(listener)
    eventListeners.set(type, listeners)
  },
  removeEventListener(type, listener) {
    eventListeners.get(type)?.delete(listener)
  },
  dispatchEvent(event) {
    for (const listener of eventListeners.get(event.type) ?? []) listener(event)
    return true
  },
}

test('local demo sign-in exposes all thirteen canonical profiles through one shared workspace', () => {
  assert.equal(demoAccounts.length, 13)
  assert.equal(new Set(demoAccounts.map((account) => account.email)).size, 13)
  assert.equal(new Set(demoAccounts.map((account) => account.role)).size, 13)

  assert.deepEqual(demoAccounts.map((account) => account.role).sort(), [
    'admin', 'auditor', 'client', 'developer', 'evaluator', 'exam-controller',
    'finance-admin', 'guardian', 'mentor', 'student', 'support',
    'teacher', 'teaching-assistant',
  ])

  for (const account of demoAccounts) {
    assert.ok(account.email.length > 0)
    assert.equal(account.name, account.profile.name)
    assert.equal(account.role, account.profile.role)
    assert.ok(DEMO_PROFILES.some((profile) => profile === account.profile))
  }
  assert.equal(demoAccounts.find((account) => account.email === 'student@demo.uep')?.profile.userId, 'student-asha')
  assert.equal(demoAccounts.find((account) => account.email === 'teacher@demo.uep')?.profile.userId, 'teacher-rahul')
  assert.equal(demoAccounts.find((account) => account.email === 'guardian@demo.uep')?.profile.userId, 'guardian-nina')
})

function profile(role) {
  const result = DEMO_PROFILES.find((item) => item.role === role)
  assert.ok(result, 'expected seeded demo profile: ' + role)
  return result
}

test('suspended demo profiles fail the centralized active-user guard', () => {
  const state = resetDemoState()
  const student = profile('student')
  assert.equal(activeUserForProfile(state, student)?.id, student.userId)
  const suspended = {
    ...state,
    users: state.users.map(user => user.id === student.userId ? { ...user, status: 'Suspended' } : user),
  }
  assert.equal(activeUserForProfile(suspended, student), undefined)
  assert.deepEqual(submissionsForProfile(suspended, student), [])
})

test('student submission persists and the teacher sees the same record to grade', () => {
  let state = resetDemoState()
  const student = profile('student')
  const teacher = profile('teacher')

  state = submitDemoAssignment(state, 'submission-asha-cloud', student)
  assert.notStrictEqual(state, loadDemoState())
  assert.equal(state.submissions.find((item) => item.id === 'submission-asha-cloud')?.status, 'Submitted')

  saveDemoState(state)
  const teacherQueueState = loadDemoState()
  const queued = teacherQueueState.submissions.find((item) => item.id === 'submission-asha-cloud')
  assert.equal(queued?.status, 'Submitted')
  assert.equal(queued?.studentId, student.userId)

  const graded = gradeDemoSubmission(teacherQueueState, 'submission-asha-cloud', 91, 'Clear deployment notes.', teacher)
  assert.notStrictEqual(graded, teacherQueueState)
  saveDemoState(graded)

  const studentResult = loadDemoState().submissions.find((item) => item.id === 'submission-asha-cloud')
  assert.equal(studentResult?.status, 'Graded')
  assert.equal(studentResult?.score, 91)
  assert.equal(studentResult?.feedback, 'Clear deployment notes.')
})

test('unauthorized role, learner and course mutations are rejected', () => {
  const state = resetDemoState()
  const student = profile('student')
  const evaluator = profile('evaluator')
  const client = profile('client')
  const teacher = profile('teacher')

  assert.strictEqual(submitDemoAssignment(state, 'submission-ravi-sql', student), state)
  assert.strictEqual(submitDemoAssignment(state, 'submission-exam-asha', student), state, 'exam records must not enter the generic assignment submission flow')
  assert.strictEqual(gradeDemoSubmission(state, 'submission-asha-api', 95, 'Out of scope.', evaluator), state)
  assert.strictEqual(gradeDemoSubmission(state, 'submission-asha-api', 101, 'Invalid score.', teacher), state)
  assert.strictEqual(updateDemoAttendance(state, 'student-ravi-attendance--6', 'Present', client), state)
  assert.strictEqual(setDemoUserStatus(state, student.userId, 'Suspended', teacher), state)
})

test('authorized attendance changes propagate through the shared persisted state', () => {
  const original = resetDemoState()
  const teacher = profile('teacher')
  let changeNotifications = 0
  const unsubscribe = subscribeDemoState(() => { changeNotifications += 1 })

  const updated = updateDemoAttendance(original, 'student-ravi-attendance--6', 'Present', teacher)
  assert.notStrictEqual(updated, original)
  saveDemoState(updated)

  const persisted = loadDemoState()
  assert.equal(persisted.attendance.find((item) => item.id === 'student-ravi-attendance--6')?.status, 'Present')
  assert.ok(changeNotifications > 0, 'same-tab subscribers should receive saved-state updates')
  unsubscribe()
})


test('student, guardian and mentor submission lists exclude non-enrolled course records', () => {
  const state = resetDemoState()
  const outOfScope = {
    ...state.submissions[0],
    id: 'submission-asha-unenrolled',
    title: 'Unexpected draft-course submission',
    courseId: 'course-product-draft',
    studentId: 'student-asha',
  }
  const pollutedState = { ...state, submissions: [...state.submissions, outOfScope] }

  assert.ok(!submissionsForProfile(pollutedState, profile('student')).some(item => item.id === outOfScope.id))
  assert.ok(!submissionsForProfile(pollutedState, profile('guardian')).some(item => item.id === outOfScope.id))
  assert.ok(!submissionsForProfile(pollutedState, profile('mentor')).some(item => item.id === outOfScope.id))
  assert.ok(submissionsForProfile(state, profile('student')).every(item =>
    state.users.find(user => user.id === 'student-asha')?.courseIds.includes(item.courseId)))
})

test('submission visibility is restricted to each persona learner/course scope', () => {
  const state = resetDemoState()
  const ids = (role) => submissionsForProfile(state, profile(role)).map((item) => item.id)

  const studentRows = ids('student')
  assert.ok(studentRows.length > 0)
  assert.ok(state.submissions.filter((item) => studentRows.includes(item.id)).every((item) => item.studentId === 'student-asha'))

  const guardianRows = ids('guardian')
  assert.ok(state.submissions.filter((item) => guardianRows.includes(item.id)).every((item) => item.studentId === 'student-asha'))

  const mentorRows = state.submissions.filter((item) => ids('mentor').includes(item.id))
  assert.ok(mentorRows.every((item) => item.studentId === 'student-asha' && ['course-web', 'course-cloud'].includes(item.courseId)))
  assert.ok(!mentorRows.some((item) => item.courseId === 'course-data'))

  const assistantRows = state.submissions.filter((item) => ids('teaching-assistant').includes(item.id))
  assert.ok(assistantRows.length > 0)
  assert.ok(assistantRows.every((item) => item.courseId === 'course-web'))

  const evaluatorRows = state.submissions.filter((item) => ids('evaluator').includes(item.id))
  assert.ok(evaluatorRows.length > 0)
  assert.ok(evaluatorRows.every((item) => item.courseId === 'course-data'))

  for (const submission of state.submissions) {
    const learner = state.users.find((item) => item.id === submission.studentId)
    assert.ok(learner?.role === 'Student' && learner.courseIds.includes(submission.courseId),
      `seeded submission ${submission.id} must belong to a learner enrolled in ${submission.courseId}`)
  }
  assert.ok(state.attendance.every((item) =>
    state.users.find((learner) => learner.id === item.studentId)?.courseIds.includes(item.courseId)),
  'every seeded attendance row must match learner enrollment')

  const examRows = state.submissions.filter((item) => ids('exam-controller').includes(item.id))
  assert.equal(examRows.length, 3)
  assert.ok(examRows.every((item) => item.courseId === 'course-data' && item.examScheduleId === 'schedule-sql-exam'))
  assert.ok(examRows.every((item) => state.schedule.some(session => session.id === item.examScheduleId && session.kind === 'Exam' && session.participantIds.includes('exam-aarav'))))
  assert.ok(!examRows.some((item) => ['submission-asha-sql', 'submission-ravi-sql'].includes(item.id)))
  assert.ok(evaluatorRows.every((item) => !item.examScheduleId))

  assert.equal(ids('admin').length, state.submissions.length)
  assert.ok(ids('client').length > 0)
  assert.deepEqual(ids('support'), [])
  assert.deepEqual(ids('auditor'), [])
  assert.deepEqual(ids('developer'), [])
  assert.deepEqual(ids('finance-admin'), [])
})

test('teaching assistants and evaluators receive scoped learner counts', () => {
  const state = resetDemoState()
  assert.deepEqual(learnersForProfile(state, profile('teaching-assistant')).map(item => item.id), [
    'student-asha', 'student-ravi', 'student-meera',
  ])
  assert.deepEqual(learnersForProfile(state, profile('evaluator')).map(item => item.id), [
    'student-asha', 'student-ravi', 'student-meera',
  ])

  const outOfScope = {
    ...state,
    users: state.users.map(item => {
      if (item.id === 'student-ravi') return { ...item, organization: 'East Valley Academy' }
      if (item.id === 'student-meera') return { ...item, status: 'Suspended' }
      return item
    }),
  }
  assert.deepEqual(learnersForProfile(outOfScope, profile('teaching-assistant')).map(item => item.id), ['student-asha'])
  assert.deepEqual(learnersForProfile(outOfScope, profile('evaluator')).map(item => item.id), ['student-asha'])
})

test('learner and course catalog queries enforce active organization and relationship scope', () => {
  const state = resetDemoState()
  const polluted = {
    ...state,
    users: state.users.map(item => {
      if (item.id === 'student-ravi') return { ...item, organization: 'East Valley Academy' }
      if (item.id === 'student-meera') return { ...item, status: 'Suspended' }
      return item
    }).concat([
      { id: 'student-unassigned', name: 'Uma Singh', email: 'uma@demo.uep', role: 'Student', status: 'Active', organization: 'Northstar Learning Institute', courseIds: ['course-product-draft'] },
    ]),
  }

  assert.deepEqual(learnersForProfile(polluted, profile('teacher')).map(item => item.id), ['student-asha'])
  assert.deepEqual(learnersForProfile(polluted, profile('client')).map(item => item.id), ['student-asha', 'student-unassigned'])
  assert.ok(learnersForProfile(polluted, profile('admin')).some(item => item.id === 'student-ravi'))
  const suspendedTeacher = { ...state, users: state.users.map(item => item.id === 'teacher-rahul' ? { ...item, status: 'Suspended' } : item) }
  assert.deepEqual(learnersForProfile(suspendedTeacher, profile('teacher')), [])

  assert.deepEqual(coursesForProfile(polluted, profile('student')).map(item => item.id), ['course-web', 'course-data', 'course-cloud'])
  assert.deepEqual(coursesForProfile(polluted, profile('client')).map(item => item.id), ['course-web', 'course-data', 'course-cloud'])
  assert.deepEqual(coursesForProfile(polluted, profile('teacher')).map(item => item.id), ['course-web', 'course-data', 'course-cloud'])
  assert.deepEqual(coursesForProfile(polluted, profile('guardian')).map(item => item.id), ['course-web', 'course-data', 'course-cloud'])
  assert.deepEqual(coursesForProfile(polluted, { ...profile('guardian'), userId: 'student-asha' }), [])
  assert.deepEqual(coursesForProfile({ ...state, users: state.users.map(item => item.id === 'guardian-nina' ? { ...item, organization: 'East Valley Academy' } : item) }, profile('guardian')), [])
  assert.ok(!coursesForProfile(polluted, profile('client')).some(item => item.id === 'course-product-draft'))
  assert.deepEqual(coursesForProfile({ ...state, users: state.users.map(item => item.id === 'student-asha' ? { ...item, status: 'Suspended' } : item) }, profile('student')), [])
})

test('draft-course records are hidden from learner workflows and cannot be mutated', () => {
  const state = resetDemoState()
  const draftLearner = {
    id: 'student-draft-only',
    name: 'Uma Singh',
    email: 'uma.draft@demo.uep',
    role: 'Student',
    status: 'Active',
    organization: 'Northstar Learning Institute',
    courseIds: ['course-product-draft'],
  }
  const draftProfile = { ...profile('student'), userId: draftLearner.id, name: draftLearner.name, email: draftLearner.email }
  const polluted = {
    ...state,
    users: [...state.users, draftLearner],
    submissions: [...state.submissions, {
      id: 'submission-draft-course',
      title: 'Draft course assignment',
      courseId: 'course-product-draft',
      studentId: draftLearner.id,
      dueAt: new Date().toISOString(),
      status: 'Assigned',
    }],
    attendance: [...state.attendance, {
      id: 'attendance-draft-course',
      studentId: draftLearner.id,
      courseId: 'course-product-draft',
      date: '2026-10-25',
      status: 'Absent',
    }],
  }

  assert.deepEqual(submissionsForProfile(polluted, draftProfile), [])
  assert.deepEqual(attendanceForProfile(polluted, draftProfile), [])
  assert.deepEqual(scheduleForProfile(polluted, draftProfile), [])
  assert.strictEqual(submitDemoAssignment(polluted, 'submission-draft-course', draftProfile), polluted)
  assert.strictEqual(advanceDemoProgress(polluted, 'course-product-draft', draftProfile), polluted)
  assert.strictEqual(updateDemoAttendance(polluted, 'attendance-draft-course', 'Present', profile('admin')), polluted)
})

test('exam attempts and results use the exact assigned exam session', () => {
  const state = resetDemoState()
  const student = profile('student')
  const controller = profile('exam-controller')

  assert.strictEqual(submitDemoAssignment(state, 'submission-exam-asha', student), state,
    'exam attempts must not pass through the generic assignment submit path')

  const mismatchedSession = {
    ...state,
    schedule: state.schedule.map(item => item.id === 'schedule-sql-exam' ? { ...item, courseId: 'course-web' } : item),
  }
  assert.strictEqual(submitDemoExam(mismatchedSession, 'submission-exam-asha', student), mismatchedSession,
    'attempt submission must reject a session/course mismatch')
  const completedSession = {
    ...state,
    schedule: state.schedule.map(item => item.id === 'schedule-sql-exam' ? { ...item, status: 'Completed' } : item),
  }
  assert.strictEqual(submitDemoExam(completedSession, 'submission-exam-asha', student), completedSession,
    'completed sessions must not accept new attempts')
  const cancelledSession = {
    ...state,
    schedule: state.schedule.map(item => item.id === 'schedule-sql-exam' ? { ...item, status: 'Cancelled' } : item),
  }
  assert.strictEqual(submitDemoExam(cancelledSession, 'submission-exam-asha', student), cancelledSession,
    'cancelled sessions must not accept new attempts')

  const submitted = submitDemoExam(state, 'submission-exam-asha', student)
  assert.notStrictEqual(submitted, state)
  assert.equal(submitted.submissions.find(item => item.id === 'submission-exam-asha')?.status, 'Submitted')
  assert.ok(submissionsForProfile(submitted, student).some(item => item.id === 'submission-exam-asha'))
  assert.ok(submissionsForProfile(submitted, controller).some(item => item.id === 'submission-exam-asha'))
  assert.ok(!submissionsForProfile(submitted, profile('teacher')).some(item => item.id === 'submission-exam-asha'))
  assert.strictEqual(gradeDemoSubmission(submitted, 'submission-exam-asha', 99, 'wrong workflow', profile('teacher')), submitted)

  const crossOrganization = {
    ...submitted,
    users: submitted.users.map(item => item.id === 'student-asha' ? { ...item, organization: 'East Valley Academy' } : item),
  }
  assert.strictEqual(gradeDemoExamSubmission(crossOrganization, 'submission-exam-asha', 88, 'Cross-org', controller), crossOrganization)
  const cancelledSubmitted = {
    ...submitted,
    schedule: submitted.schedule.map(item => item.id === 'schedule-sql-exam' ? { ...item, status: 'Cancelled' } : item),
  }
  assert.strictEqual(gradeDemoExamSubmission(cancelledSubmitted, 'submission-exam-asha', 88, 'Cancelled', controller), cancelledSubmitted,
    'cancelled exam sessions must not publish results')

  const graded = gradeDemoExamSubmission(submitted, 'submission-exam-asha', 88, 'Exam reviewed.', controller)
  assert.notStrictEqual(graded, submitted)
  assert.equal(graded.submissions.find(item => item.id === 'submission-exam-asha')?.status, 'Graded')
  assert.equal(graded.submissions.find(item => item.id === 'submission-exam-asha')?.score, 88)
  assert.ok(activityForProfile(graded, student).some(item => item.submissionId === 'submission-exam-asha'))
  assert.ok(activityForProfile(graded, controller).some(item => item.submissionId === 'submission-exam-asha'))

  const unauthorizedController = { ...controller, userId: 'exam-controller-rogue' }
  assert.strictEqual(gradeDemoExamSubmission(submitted, 'submission-exam-ravi', 80, 'Unauthorised', unauthorizedController), submitted)
})

test('schedule and attendance queries enforce active identity, enrollment and organization scope', () => {
  const state = resetDemoState()
  const polluted = {
    ...state,
    schedule: [
      ...state.schedule,
      { id: 'schedule-draft-course', title: 'Draft course session', courseId: 'course-product-draft', startsAt: new Date().toISOString(), durationMinutes: 45, participantIds: ['student-asha'], kind: 'Class', status: 'Scheduled' },
      { id: 'schedule-ravi-unenrolled-cloud', title: 'Unenrolled cloud session', courseId: 'course-cloud', startsAt: new Date().toISOString(), durationMinutes: 45, participantIds: ['student-ravi'], kind: 'Class', status: 'Scheduled' },
    ],
    attendance: [
      ...state.attendance,
      { id: 'attendance-asha-draft', studentId: 'student-asha', courseId: 'course-product-draft', date: '2026-10-25', status: 'Absent' },
      { id: 'attendance-ravi-unenrolled-cloud', studentId: 'student-ravi', courseId: 'course-cloud', date: '2026-10-25', status: 'Absent' },
    ],
  }
  const ravi = { ...profile('student'), userId: 'student-ravi', name: 'Ravi Nair', email: 'ravi.student@demo.uep' }

  assert.ok(!scheduleForProfile(polluted, profile('student')).some(item => item.id === 'schedule-draft-course'))
  assert.ok(!scheduleForProfile(polluted, ravi).some(item => item.id === 'schedule-ravi-unenrolled-cloud'))
  assert.ok(!scheduleForProfile(polluted, profile('guardian')).some(item => item.id === 'schedule-draft-course'))
  assert.ok(!scheduleForProfile(polluted, profile('client')).some(item => item.id === 'schedule-draft-course'))
  assert.ok(scheduleForProfile(state, profile('exam-controller')).some(item => item.id === 'schedule-sql-exam'))
  assert.ok(!scheduleForProfile(state, profile('evaluator')).some(item => item.id === 'schedule-sql-exam'),
    'course assignment alone must not reveal a dedicated exam schedule to evaluators')
  assert.ok(!scheduleForProfile(state, profile('teaching-assistant')).some(item => item.id === 'schedule-sql-exam'),
    'course assignment alone must not reveal a dedicated exam schedule to teaching assistants')
  assert.ok(scheduleForProfile(state, profile('teaching-assistant')).some(item => item.id === 'schedule-web-today'),
    'teaching assistants retain visibility into classes where they are a participant')

  assert.ok(!attendanceForProfile(polluted, profile('student')).some(item => item.id === 'attendance-asha-draft'))
  assert.ok(!attendanceForProfile(polluted, profile('guardian')).some(item => item.id === 'attendance-asha-draft'))
  assert.ok(!attendanceForProfile(polluted, profile('teacher')).some(item => item.id === 'attendance-ravi-unenrolled-cloud'))
  assert.ok(!attendanceForProfile(polluted, profile('client')).some(item => item.id === 'attendance-asha-draft'))
  assert.ok(!attendanceForProfile(polluted, profile('teaching-assistant')).some(item => item.id === 'attendance-ravi-unenrolled-cloud'))

  const crossOrganization = {
    ...polluted,
    users: polluted.users.map(item => item.id === 'student-ravi' ? { ...item, organization: 'East Valley Academy' } : item),
    schedule: [
      ...polluted.schedule,
      { id: 'schedule-ravi-cross-org', title: 'Cross-organization SQL session', courseId: 'course-data', startsAt: new Date().toISOString(), durationMinutes: 30, participantIds: ['student-ravi'], kind: 'Class', status: 'Scheduled' },
    ],
    attendance: [
      ...polluted.attendance,
      { id: 'attendance-ravi-cross-org', studentId: 'student-ravi', courseId: 'course-data', date: '2026-10-25', status: 'Absent' },
    ],
  }
  assert.ok(!scheduleForProfile(crossOrganization, profile('client')).some(item => item.id === 'schedule-ravi-cross-org'))
  assert.ok(!attendanceForProfile(crossOrganization, profile('teacher')).some(item => item.id === 'attendance-ravi-cross-org'))
  assert.ok(!attendanceForProfile(crossOrganization, profile('client')).some(item => item.id === 'attendance-ravi-cross-org'))

  const disconnectedGuardian = {
    ...state,
    users: state.users.map(item => item.id === 'student-asha' ? { ...item, organization: 'East Valley Academy' } : item),
  }
  assert.deepEqual(scheduleForProfile(disconnectedGuardian, profile('guardian')), [])
  assert.deepEqual(attendanceForProfile(disconnectedGuardian, profile('guardian')), [])

  const suspendedAsha = { ...polluted, users: polluted.users.map(item => item.id === 'student-asha' ? { ...item, status: 'Suspended' } : item) }
  assert.deepEqual(scheduleForProfile(suspendedAsha, profile('student')), [])
  assert.deepEqual(attendanceForProfile(suspendedAsha, profile('student')), [])
})

test('rapid product creation allocates unique IDs even when the clock has not advanced', () => {
  const originalNow = Date.now
  Date.now = () => 1740000000000
  try {
    const developer = profile('developer')
    const initial = resetDemoState()
    const first = createDemoProduct(initial, 'Demo product one', 'First draft', developer)
    const second = createDemoProduct(first, 'Demo product two', 'Second draft', developer)
    const created = second.products.filter(item => item.title.startsWith('Demo product '))

    assert.equal(created.length, 2)
    assert.equal(new Set(created.map(item => item.id)).size, 2)
    assert.ok(created.some(item => item.id === 'product-1740000000000'))
    assert.ok(created.some(item => item.id === 'product-1740000000000-1'))
  } finally {
    Date.now = originalNow
  }
})

test('student progress advances and administrator course publication changes the shared catalog', () => {
  const original = resetDemoState()
  const student = profile('student')
  const admin = profile('admin')

  const progressed = advanceDemoProgress(original, 'course-web', student)
  assert.notStrictEqual(progressed, original)
  assert.equal(progressed.courseProgress['student-asha:course-web'], 78)
  assert.ok(progressed.activity.some((item) => item.action === 'Learning progress updated'))

  assert.strictEqual(advanceDemoProgress(progressed, 'course-web', profile('teacher')), progressed)

  const published = publishDemoCourse(progressed, 'course-product-draft', admin)
  assert.notStrictEqual(published, progressed)
  assert.equal(published.courses.find((item) => item.id === 'course-product-draft')?.status, 'Published')
  assert.ok(published.activity.some((item) => item.action === 'Course published'))
  assert.strictEqual(publishDemoCourse(published, 'course-product-draft', student), published)
})

test('client organization actions resolve only for their matching active organization', () => {
  const state = resetDemoState()
  const client = profile('client')
  const actionId = 'org-action-attendance-review'
  const resolved = resolveDemoOrganizationAction(state, actionId, client)

  assert.notStrictEqual(resolved, state)
  assert.equal(resolved.organizationActions.find((item) => item.id === actionId)?.status, 'Resolved')
  assert.ok(resolved.activity.some((item) => item.action === 'Organization action resolved'
    && item.detail === 'Review attendance exceptions'))
  assert.strictEqual(resolveDemoOrganizationAction(resolved, actionId, client), resolved)
  assert.strictEqual(resolveDemoOrganizationAction(state, actionId, profile('teacher')), state)

  const unrelatedOrgState = {
    ...state,
    organizationActions: state.organizationActions.map((item) => item.id === actionId
      ? { ...item, organization: 'Different Learning Institute' }
      : item),
  }
  assert.strictEqual(resolveDemoOrganizationAction(unrelatedOrgState, actionId, client), unrelatedOrgState)
})

test('legacy browser state migrates to v4 with finance, support and inbox backfills', () => {
  let state = resetDemoState()
  state = advanceDemoProgress(state, 'course-web', profile('student'))
  saveDemoState(state)

  const current = JSON.parse(localStorage.getItem('uep.local-demo-data.v4'))
  current.schemaVersion = 1
  delete current.organizationActions
  delete current.invoices
  delete current.supportCases
  delete current.messages
  localStorage.setItem('uep.local-demo-data.v1', JSON.stringify(current))
  localStorage.removeItem('uep.local-demo-data.v4')

  const migrated = loadDemoState()
  assert.equal(migrated.schemaVersion, 4)
  assert.equal(migrated.courseProgress['student-asha:course-web'], 78)
  assert.equal(migrated.organizationActions.length, 3)
  assert.equal(migrated.invoices.length, 3)
  assert.equal(migrated.supportCases.length, 3)
  assert.ok(migrated.messages.some(message => message.id === 'message-student-weekly'))
  assert.ok(localStorage.getItem('uep.local-demo-data.v4'))

  for (const legacyVersion of [2, 3]) {
    const oldState = { ...migrated, schemaVersion: legacyVersion }
    delete oldState.messages
    if (legacyVersion === 2) {
      delete oldState.invoices
      delete oldState.supportCases
    }
    localStorage.setItem(`uep.local-demo-data.v${legacyVersion}`, JSON.stringify(oldState))
    localStorage.removeItem('uep.local-demo-data.v4')
    const migratedLegacy = loadDemoState()
    assert.equal(migratedLegacy.schemaVersion, 4)
    assert.equal(migratedLegacy.invoices.length, 3)
    assert.equal(migratedLegacy.supportCases.length, 3)
    assert.ok(migratedLegacy.messages.some(message => message.id === 'message-student-weekly'))
    assert.ok(localStorage.getItem('uep.local-demo-data.v4'))
  }
})

test('shared inbox is recipient-scoped and read state persists per recipient', () => {
  const state = resetDemoState()
  const student = profile('student')
  const guardian = profile('guardian')
  const teacher = profile('teacher')

  const studentMessages = messagesForProfile(state, student)
  assert.ok(studentMessages.some(message => message.id === 'message-student-weekly'))
  assert.ok(studentMessages.some(message => message.id === 'message-student-feedback'))
  assert.ok(!studentMessages.some(message => message.id === 'message-client-delivery'))
  assert.ok(!studentMessages.some(message => message.id === 'message-support-update'))

  const guardianMessages = messagesForProfile(state, guardian)
  assert.ok(guardianMessages.some(message => message.id === 'message-student-weekly'))
  assert.ok(!guardianMessages.some(message => message.id === 'message-student-feedback'))

  const teacherMessages = messagesForProfile(state, teacher)
  assert.ok(teacherMessages.some(message => message.id === 'message-client-delivery'))
  assert.ok(!teacherMessages.some(message => message.id === 'message-student-feedback'))
  assert.deepEqual(messagesForProfile(state, profile('support')), [])

  assert.strictEqual(markDemoMessageRead(state, 'message-student-weekly', teacher), state)
  const studentRead = markDemoMessageRead(state, 'message-student-weekly', student)
  assert.notStrictEqual(studentRead, state)
  assert.ok(studentRead.messages.find(message => message.id === 'message-student-weekly')?.readBy.includes(student.userId))
  assert.ok(!studentRead.messages.find(message => message.id === 'message-student-weekly')?.readBy.includes(guardian.userId))

  saveDemoState(studentRead)
  const persisted = loadDemoState()
  const guardianRead = markDemoMessageRead(persisted, 'message-student-weekly', guardian)
  saveDemoState(guardianRead)
  const finalState = loadDemoState()
  assert.deepEqual(finalState.messages.find(message => message.id === 'message-student-weekly')?.readBy.sort(), ['guardian-nina', 'student-asha'])
})

test('finance invoice access is restricted to the active finance persona organization', () => {
  const state = resetDemoState()
  const finance = profile('finance-admin')
  assert.equal(invoicesForFinanceProfile(state, finance).length, 3)
  assert.deepEqual(invoicesForFinanceProfile(state, profile('support')), [])

  const extraTenantInvoice = {
    ...state.invoices[0],
    id: 'invoice-other-tenant',
    invoiceNumber: 'OTHER-2026-001',
    organization: 'Another Learning Institute',
  }
  const multipleTenants = { ...state, invoices: [...state.invoices, extraTenantInvoice] }
  assert.equal(invoicesForFinanceProfile(multipleTenants, finance).length, 3)
  assert.ok(invoicesForFinanceProfile(multipleTenants, finance).every(invoice => invoice.organization === 'Northstar Learning Institute'))

  const ownerMismatches = {
    ...state,
    users: state.users.map(user => user.id === 'student-ravi' ? { ...user, organization: 'East Valley Academy' } : user),
    invoices: [
      ...state.invoices,
      { ...state.invoices[0], id: 'invoice-owner-not-student', invoiceNumber: 'NLI-2026-099', studentId: 'admin-platform' },
      { ...state.invoices[1], id: 'invoice-owner-other-org', invoiceNumber: 'NLI-2026-100', studentId: 'student-ravi' },
    ],
  }
  const scopedInvoices = invoicesForFinanceProfile(ownerMismatches, finance)
  assert.equal(scopedInvoices.length, 2)
  assert.ok(!scopedInvoices.some(invoice => invoice.id === 'invoice-owner-not-student'))
  assert.ok(!scopedInvoices.some(invoice => invoice.id === 'invoice-owner-other-org'))

  const suspended = {
    ...state,
    users: state.users.map(user => user.id === finance.userId ? { ...user, status: 'Suspended' } : user),
  }
  assert.deepEqual(invoicesForFinanceProfile(suspended, finance), [])
})

test('finance ledger is seeded and support case transitions are role-scoped and persistent', () => {
  const state = resetDemoState()
  assert.equal(state.invoices.length, 3)
  assert.equal(new Set(state.invoices.map((invoice) => invoice.invoiceNumber)).size, 3)
  assert.equal(state.invoices.filter((invoice) => invoice.status === 'Paid').length, 1)
  assert.equal(state.invoices.filter((invoice) => invoice.status === 'Overdue').length, 1)

  const openCase = state.supportCases.find((item) => item.status === 'Open')
  assert.ok(openCase)
  assert.strictEqual(updateDemoSupportCase(state, openCase.id, 'In progress', profile('teacher')), state)

  const triaged = updateDemoSupportCase(state, openCase.id, 'In progress', profile('support'))
  assert.notStrictEqual(triaged, state)
  assert.equal(triaged.supportCases.find((item) => item.id === openCase.id)?.status, 'In progress')

  const resolved = updateDemoSupportCase(triaged, openCase.id, 'Resolved', profile('support'))
  assert.notStrictEqual(resolved, triaged)
  assert.equal(resolved.supportCases.find((item) => item.id === openCase.id)?.status, 'Resolved')
  assert.strictEqual(updateDemoSupportCase(resolved, openCase.id, 'In progress', profile('support')), resolved)

  saveDemoState(resolved)
  const persisted = loadDemoState()
  assert.equal(persisted.supportCases.find((item) => item.id === openCase.id)?.status, 'Resolved')
  assert.ok(activityForProfile(persisted, profile('admin')).some((item) => item.action === 'Support case updated'))
  assert.deepEqual(activityForProfile(persisted, profile('support')), [])
})

test('activity feeds do not expose global details to unrelated personas', () => {
  const state = resetDemoState()
  const events = (role) => activityForProfile(state, profile(role))

  const studentEvents = events('student')
  assert.ok(studentEvents.length > 0)
  assert.ok(studentEvents.every((item) => item.actor === 'Asha Sharma'
    || state.submissions.some((submission) => submission.studentId === 'student-asha'
      && item.detail.toLowerCase().includes(submission.title.toLowerCase()))))
  assert.ok(!studentEvents.some((item) => item.action === 'Product updated'))

  const guardianEvents = events('guardian')
  assert.ok(guardianEvents.some((item) => item.action === 'Grade published'))
  assert.ok(!guardianEvents.some((item) => item.action === 'Product updated'))

  const mentorEvents = events('mentor')
  assert.ok(mentorEvents.some((item) => item.action === 'Assignment submitted'))
  assert.ok(!mentorEvents.some((item) => item.action === 'Grade published'))

  const evaluatorEvents = events('evaluator')
  assert.ok(evaluatorEvents.some((item) => item.action === 'Grade published'))
  assert.ok(!evaluatorEvents.some((item) => item.action === 'Assignment submitted'))

  const developerEvents = events('developer')
  assert.ok(developerEvents.length > 0)
  assert.ok(developerEvents.every((item) => item.action.toLowerCase().includes('product')))
  assert.deepEqual(events('support'), [])
  assert.deepEqual(events('finance-admin'), [])
  assert.equal(events('admin').length, state.activity.length)
  assert.equal(events('auditor').length, state.activity.length)
})

test('legacy product activity hides ambiguous duplicate product titles', () => {
  const state = resetDemoState()
  const product = state.products.find(item => item.id === 'product-ai-draft')
  assert.ok(product)
  const event = {
    id: 'legacy-ambiguous-product-event',
    at: new Date().toISOString(),
    actor: 'Dev Patel',
    action: 'Product created',
    detail: product.title,
  }
  const ambiguous = {
    ...state,
    products: [...state.products, { ...product, id: 'product-ai-draft-duplicate' }],
    activity: [event, ...state.activity],
  }
  assert.ok(!activityForProfile(ambiguous, profile('developer')).some(item => item.id === event.id))

  const unambiguous = { ...state, activity: [event, ...state.activity] }
  assert.ok(activityForProfile(unambiguous, profile('developer')).some(item => item.id === event.id))
})

test('legacy organization and course-published events hide ambiguous duplicate titles', () => {
  const state = resetDemoState()
  const organizationAction = state.organizationActions[0]
  const webCourse = state.courses.find(item => item.id === 'course-web')
  assert.ok(organizationAction)
  assert.ok(webCourse)
  const legacyActionEvent = {
    id: 'legacy-ambiguous-org-action',
    at: new Date().toISOString(),
    actor: 'Priya Kulkarni',
    action: 'Organization action resolved',
    detail: organizationAction.title,
  }
  const legacyCourseEvent = {
    id: 'legacy-ambiguous-course-published',
    at: new Date().toISOString(),
    actor: 'Morgan Admin',
    action: 'Course published',
    detail: webCourse.title,
  }
  const ambiguous = {
    ...state,
    organizationActions: [
      ...state.organizationActions,
      { ...organizationAction, id: 'org-action-other-tenant', organization: 'East Valley Academy' },
    ],
    courses: [
      ...state.courses,
      { ...webCourse, id: 'course-web-other-tenant', teacherId: 'teacher-other-tenant' },
    ],
    activity: [legacyActionEvent, legacyCourseEvent, ...state.activity],
  }
  assert.ok(!activityForProfile(ambiguous, profile('client')).some(item => item.id === legacyActionEvent.id))
  assert.ok(!activityForProfile(ambiguous, profile('client')).some(item => item.id === legacyCourseEvent.id))

  const unambiguous = { ...state, activity: [legacyActionEvent, legacyCourseEvent, ...state.activity] }
  const visible = activityForProfile(unambiguous, profile('client'))
  assert.ok(visible.some(item => item.id === legacyActionEvent.id))
  assert.ok(visible.some(item => item.id === legacyCourseEvent.id))
})

test('legacy attendance activity hides ambiguous duplicate learner and course titles', () => {
  const state = resetDemoState()
  const existingRaviAttendance = state.attendance.find(item =>
    item.studentId === 'student-ravi' && item.courseId === 'course-web' && item.status === 'Absent')
  assert.ok(existingRaviAttendance, 'seed must contain an absent Ravi web-course attendance row')
  const legacyEvent = {
    id: 'legacy-cross-org-attendance',
    at: new Date().toISOString(),
    actor: 'Rahul Deshmukh',
    action: 'Attendance recorded',
    detail: `Ravi Nair · Full Stack Web Development · Absent (${existingRaviAttendance.date})`,
  }
  const polluted = {
    ...state,
    users: [
      ...state.users,
      { id: 'student-ravi-other-org', name: 'Ravi Nair', email: 'ravi.other@demo.uep', role: 'Student', status: 'Active', organization: 'East Valley Academy', courseIds: ['course-web-other-org'] },
    ],
    courses: [
      ...state.courses,
      { ...state.courses.find(item => item.id === 'course-web'), id: 'course-web-other-org', teacherId: 'teacher-other-org' },
    ],
    attendance: [
      ...state.attendance,
      { id: 'attendance-other-org', studentId: 'student-ravi-other-org', courseId: 'course-web-other-org', date: existingRaviAttendance.date, status: 'Absent' },
    ],
    activity: [legacyEvent, ...state.activity],
  }

  assert.ok(!activityForProfile(polluted, profile('teacher')).some(item => item.id === legacyEvent.id),
    'ambiguous legacy attendance must be hidden from staff when names/course titles collide')
  assert.ok(!activityForProfile(polluted, profile('client')).some(item => item.id === legacyEvent.id))
})

test('legacy learning-progress activity requires a unique learner and scoped course match', () => {
  const state = resetDemoState()
  const event = {
    id: 'legacy-ambiguous-progress',
    at: new Date().toISOString(),
    actor: 'Asha Sharma',
    action: 'Learning progress updated',
    detail: 'Full Stack Web Development · 30% complete',
  }
  const polluted = {
    ...state,
    courses: [
      ...state.courses,
      { ...state.courses.find(item => item.id === 'course-web'), id: 'course-web-duplicate-title', teacherId: 'teacher-other-org' },
    ],
    users: [
      ...state.users,
      { id: 'student-asha-duplicate-name', name: 'Asha Sharma', email: 'asha.other@demo.uep', role: 'Student', status: 'Active', organization: 'East Valley Academy', courseIds: ['course-web-duplicate-title'] },
    ],
    activity: [event, ...state.activity],
  }
  assert.ok(!activityForProfile(polluted, profile('student')).some(item => item.id === event.id))
  assert.ok(!activityForProfile(polluted, profile('guardian')).some(item => item.id === event.id))
})

test('activity timelines bind repeated assignment titles to learner and submission identity', () => {
  const state = resetDemoState()
  const polluted = {
    ...state,
    activity: [
      { id: 'activity-ravi-api-duplicate-title', at: new Date().toISOString(), actor: 'Ravi Nair', action: 'Assignment submitted', detail: 'REST API design exercise' },
      { id: 'activity-meera-grade-duplicate-title', at: new Date().toISOString(), actor: 'Rahul Deshmukh', action: 'Grade published', detail: 'REST API design exercise · 94/100 for Meera Iyer' },
      ...state.activity,
    ],
  }

  const studentEvents = activityForProfile(polluted, profile('student'))
  const guardianEvents = activityForProfile(polluted, profile('guardian'))
  const teacherEvents = activityForProfile(polluted, profile('teacher'))

  assert.ok(!studentEvents.some((item) => item.id === 'activity-ravi-api-duplicate-title'))
  assert.ok(!studentEvents.some((item) => item.id === 'activity-meera-grade-duplicate-title'))
  assert.ok(!guardianEvents.some((item) => item.id === 'activity-ravi-api-duplicate-title'))
  assert.ok(!guardianEvents.some((item) => item.id === 'activity-meera-grade-duplicate-title'))
  assert.ok(teacherEvents.some((item) => item.id === 'activity-ravi-api-duplicate-title'))
  assert.ok(teacherEvents.some((item) => item.id === 'activity-meera-grade-duplicate-title'))

  const submitted = submitDemoAssignment(state, 'submission-asha-cloud', profile('student'))
  const event = submitted.activity[0]
  assert.equal(event.submissionId, 'submission-asha-cloud')
  assert.equal(event.subjectUserId, 'student-asha')
  assert.equal(event.courseId, 'course-cloud')
  assert.ok(activityForProfile(submitted, profile('student')).some((item) => item.id === event.id))
})

test('reset restores seeded records after mutations', () => {
  let state = resetDemoState()
  const developer = profile('developer')
  state = submitDemoAssignment(state, 'submission-asha-cloud', profile('student'))
  saveDemoState(state)
  assert.equal(loadDemoState().submissions.find((item) => item.id === 'submission-asha-cloud')?.status, 'Submitted')

  const reset = resetDemoState()
  assert.equal(reset.products.length, 3)
  assert.equal(reset.submissions.find((item) => item.id === 'submission-asha-cloud')?.status, 'Assigned')
  assert.equal(loadDemoState().submissions.find((item) => item.id === 'submission-asha-cloud')?.status, 'Assigned')
  assert.equal(developer.role, 'developer')
})
