import assert from 'node:assert/strict'
import test from 'node:test'
import {
  DEMO_PROFILES,
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
