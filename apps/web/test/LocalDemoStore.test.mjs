import assert from 'node:assert/strict'
import test from 'node:test'
import {
  administratorStats,
  clientStats,
  coursesForOrganization,
  developerStats,
  learnersForOrganization,
  localDemoData,
  organizationMetrics,
  studentStats,
  teacherStats,
} from '../src/dashboard/localDemoData.ts'
import {
  loadDemoStore as loadPresentationStore,
  markApiReleaseReady as markPresentationReleaseReady,
  publishDataEngineeringProduct as publishPresentationProduct,
  resetDemoStore as resetPresentationStore,
  resolveOrganizationAction as resolvePresentationOrganizationAction,
} from '../src/dashboard/LocalDemoWorkflowStore.ts'
import {
  DEMO_PROFILES,
  activityForProfile,
  advanceDemoProgress,
  publishDemoCourse,
  resolveDemoOrganizationAction,
  submissionsForProfile,
  gradeDemoSubmission,
  loadDemoState,
  resetDemoState,
  saveDemoState,
  setDemoUserStatus,
  submitDemoAssignment,
  subscribeDemoState,
  updateDemoAttendance,
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

function profile(role) {
  const result = DEMO_PROFILES.find((item) => item.role === role)
  assert.ok(result, 'expected seeded demo profile: ' + role)
  return result
}

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

  const examRows = state.submissions.filter((item) => ids('exam-controller').includes(item.id))
  assert.ok(examRows.length > 0)
  assert.ok(examRows.every((item) => item.courseId === 'course-data'))

  assert.equal(ids('admin').length, state.submissions.length)
  assert.ok(ids('client').length > 0)
  assert.deepEqual(ids('support'), [])
  assert.deepEqual(ids('auditor'), [])
  assert.deepEqual(ids('developer'), [])
  assert.deepEqual(ids('finance-admin'), [])
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

test('legacy browser state migrates to v2 without losing saved workflows', () => {
  let state = resetDemoState()
  state = advanceDemoProgress(state, 'course-web', profile('student'))
  saveDemoState(state)

  const current = JSON.parse(localStorage.getItem('uep.local-demo-data.v2'))
  current.schemaVersion = 1
  delete current.organizationActions
  localStorage.setItem('uep.local-demo-data.v1', JSON.stringify(current))
  localStorage.removeItem('uep.local-demo-data.v2')

  const migrated = loadDemoState()
  assert.equal(migrated.schemaVersion, 2)
  assert.equal(migrated.courseProgress['student-asha:course-web'], 78)
  assert.equal(migrated.organizationActions.length, 3)
  assert.ok(localStorage.getItem('uep.local-demo-data.v2'))
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

test('presentation controls update dashboard metrics and reset the pristine seed', () => {
  resetPresentationStore()
  assert.equal(administratorStats().pendingApprovals, 1)
  assert.equal(developerStats().failing, 4)
  assert.equal(localDemoData.organizations.find((item) => item.id === 'org-001')?.actions, 7)

  publishPresentationProduct()
  assert.equal(administratorStats().pendingApprovals, 0)
  assert.equal(loadPresentationStore().products.find((item) => item.id === 'product-002')?.status, 'Published')

  markPresentationReleaseReady()
  assert.equal(developerStats().failing, 0)
  assert.equal(loadPresentationStore().releases.find((item) => item.id === 'release-002')?.passed, 18)

  resolvePresentationOrganizationAction()
  assert.equal(localDemoData.organizations.find((item) => item.id === 'org-001')?.actions, 6)

  resetPresentationStore()
  assert.equal(administratorStats().pendingApprovals, 1)
  assert.equal(developerStats().failing, 4)
  assert.equal(localDemoData.organizations.find((item) => item.id === 'org-001')?.actions, 7)
  assert.equal(loadPresentationStore().products.find((item) => item.id === 'product-002')?.status, 'In review')
  assert.equal(loadPresentationStore().releases.find((item) => item.id === 'release-002')?.passed, 14)
})

test('learner, course, and organization metrics are derived from scoped seed records', () => {
  const puneLearners = learnersForOrganization('Pune Technology Academy')
  assert.deepEqual(puneLearners.map((learner) => learner.name).sort(), ['Aarav Mehta', 'Ananya Shah', 'Isha Kulkarni'].sort())
  assert.ok(puneLearners.every((learner) => learner.organization === 'Pune Technology Academy'))
  assert.equal(coursesForOrganization('Pune Technology Academy').length, 2)
  assert.equal(coursesForOrganization('Maharashtra Digital Skills').length, 1)

  assert.deepEqual(learnersForOrganization('Unknown organization'), [])
  assert.deepEqual(coursesForOrganization('Unknown organization'), [])
  assert.deepEqual(organizationMetrics('Unknown organization'), {
    learners: 0, activeLearners: 0, courses: 0, completion: 0, attendance: 0, atRiskLearners: 0,
  })
  const linkedLearner = localDemoData.students.find((learner) => learner.guardianName === 'Meera Mehta')
  assert.equal(linkedLearner?.name, 'Aarav Mehta')

  const pune = organizationMetrics('Pune Technology Academy')
  assert.equal(pune.learners, 3)
  assert.equal(pune.activeLearners, 2)
  assert.equal(pune.courses, 2)
  assert.equal(pune.completion, Math.round((72 + 88 + 100) / 3))

  const all = clientStats()
  assert.equal(all.learners, localDemoData.students.length)
  assert.equal(all.courses, localDemoData.courses.length)
  assert.equal(all.completion, Math.round(localDemoData.students.reduce((sum, learner) => sum + learner.completion, 0) / localDemoData.students.length))
  assert.equal(studentStats('Aarav Mehta').activeCourses, 1)
  assert.equal(studentStats('Aarav Mehta').pendingAssignments, 0)
  assert.equal(teacherStats('Priya Nair').learners, 3)
  assert.equal(teacherStats('Priya Nair').pendingGrading, 2)
  assert.equal(administratorStats().learners, localDemoData.students.length)
  assert.equal(administratorStats().courses, localDemoData.courses.length)
})

test('legacy presentation state migrates to ownership-aware records without losing workflow changes', () => {
  resetPresentationStore()
  const oldState = loadPresentationStore()
  const assignment = oldState.assignments.find((item) => item.id === 'assignment-001')
  assert.ok(assignment)
  assignment.status = 'Submitted'
  for (const learner of oldState.students) {
    delete learner.organization
    delete learner.courseIds
    delete learner.guardianName
  }
  for (const course of oldState.courses) delete course.organization
  for (const item of oldState.assignments) delete item.studentNames
  localStorage.setItem('uep-local-demo-store-v1', JSON.stringify(oldState))
  localStorage.removeItem('uep-local-demo-store-v2')

  const migrated = loadPresentationStore()
  assert.equal(migrated.assignments.find((item) => item.id === 'assignment-001')?.status, 'Submitted')
  assert.equal(migrated.students.find((item) => item.id === 'stu-001')?.organization, 'Pune Technology Academy')
  assert.deepEqual(migrated.students.find((item) => item.id === 'stu-001')?.courseIds, ['course-001', 'course-003'])
  assert.equal(migrated.courses.find((item) => item.id === 'course-002')?.organization, 'Maharashtra Digital Skills')
  assert.deepEqual(migrated.assignments.find((item) => item.id === 'assignment-001')?.studentNames, ['Aarav Mehta', 'Ananya Shah'])
  assert.ok(localStorage.getItem('uep-local-demo-store-v2'))
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
