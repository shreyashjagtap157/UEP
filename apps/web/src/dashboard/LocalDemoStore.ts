export type DemoRole = 'student' | 'teacher' | 'client' | 'developer' | 'admin' | 'guardian' | 'exam-controller' | 'finance-admin' | 'teaching-assistant' | 'evaluator' | 'mentor' | 'support' | 'auditor'

export interface DemoProfile {
  role: DemoRole
  label: string
  name: string
  email: string
  userId: string
  description: string
}

export const DEMO_PROFILES: DemoProfile[] = [
  { role: 'student', label: 'Student', name: 'Asha Sharma', email: 'asha.student@demo.uep', userId: 'student-asha', description: 'Courses, assignments, assessments, results and attendance.' },
  { role: 'teacher', label: 'Teacher', name: 'Rahul Deshmukh', email: 'rahul.teacher@demo.uep', userId: 'teacher-rahul', description: 'Classes, learner submissions, grading, content and analytics.' },
  { role: 'client', label: 'Client / Institution', name: 'Priya Kulkarni', email: 'priya.client@demo.uep', userId: 'client-priya', description: 'Programs, learners, usage, reports and billing.' },
  { role: 'developer', label: 'Product Developer', name: 'Dev Patel', email: 'dev.product@demo.uep', userId: 'developer-dev', description: 'Educational products, drafts, publishing and integrations.' },
  { role: 'admin', label: 'Platform Administrator', name: 'Morgan Admin', email: 'admin.platform@demo.uep', userId: 'admin-platform', description: 'People, academics, operations and configuration.' },
  { role: 'guardian', label: 'Parent / Guardian', name: 'Nina Sharma', email: 'nina.guardian@demo.uep', userId: 'guardian-nina', description: 'Progress and communications for the linked learner.' },
  { role: 'exam-controller', label: 'Exam Controller', name: 'Aarav Shah', email: 'aarav.exams@demo.uep', userId: 'exam-aarav', description: 'Assessment schedules, readiness and submissions.' },
  { role: 'finance-admin', label: 'Finance Administrator', name: 'Anaya Joshi', email: 'anaya.finance@demo.uep', userId: 'finance-anaya', description: 'Invoices, receipts and account status.' },
  { role: 'teaching-assistant', label: 'Teaching Assistant', name: 'Ishaan Patil', email: 'ishaan.ta@demo.uep', userId: 'ta-ishaan', description: 'Assigned classes and submission triage.' },
  { role: 'evaluator', label: 'Evaluator', name: 'Sara Khan', email: 'sara.evaluator@demo.uep', userId: 'evaluator-sara', description: 'Assigned assessment and grading work.' },
  { role: 'mentor', label: 'Mentor', name: 'Rohan Mehta', email: 'rohan.mentor@demo.uep', userId: 'mentor-rohan', description: 'Learner milestones and follow-up.' },
  { role: 'support', label: 'Support Operator', name: 'Alex Support', email: 'alex.support@demo.uep', userId: 'support-alex', description: 'Support queue and issue triage.' },
  { role: 'auditor', label: 'Auditor', name: 'Jordan Audit', email: 'jordan.audit@demo.uep', userId: 'auditor-jordan', description: 'Read-only activity and compliance review.' },
]

export interface DemoUser {
  id: string
  name: string
  email: string
  role: string
  status: 'Active' | 'Invited' | 'Suspended'
  organization: string
  courseIds: string[]
}
export interface DemoCourse {
  id: string
  title: string
  description: string
  category: string
  teacherId: string
  lessonCount: number
  status: 'Published' | 'Draft'
}
export interface DemoSubmission {
  id: string
  title: string
  courseId: string
  studentId: string
  dueAt: string
  status: 'Assigned' | 'Submitted' | 'Graded'
  score?: number
  feedback?: string
  submittedAt?: string
  gradedAt?: string
}
export interface DemoAttendance {
  id: string
  studentId: string
  courseId: string
  date: string
  status: 'Present' | 'Late' | 'Absent'
}
export interface DemoScheduleItem {
  id: string
  title: string
  courseId: string
  startsAt: string
  durationMinutes: number
  participantIds: string[]
  kind: 'Class' | 'Exam' | 'Office hour'
  status: 'Scheduled' | 'Completed' | 'Cancelled'
}
export interface DemoProduct {
  id: string
  title: string
  description: string
  version: number
  status: 'Draft' | 'In review' | 'Published'
  updatedAt: string
}
export interface DemoWebhook {
  id: string
  name: string
  endpoint: string
  status: 'Healthy' | 'Needs attention' | 'Paused'
  successfulDeliveries: number
  failedDeliveries: number
}
export interface DemoActivity {
  id: string
  at: string
  actor: string
  action: string
  detail: string
}
export interface DemoState {
  schemaVersion: 1
  users: DemoUser[]
  courses: DemoCourse[]
  submissions: DemoSubmission[]
  attendance: DemoAttendance[]
  schedule: DemoScheduleItem[]
  products: DemoProduct[]
  webhooks: DemoWebhook[]
  courseProgress: Record<string, number>
  activity: DemoActivity[]
}

const STORAGE_KEY = 'uep.local-demo-data.v1'
const STORE_CHANGED_EVENT = 'uep:local-demo-data-updated'

const ROLE_RECORD_NAMES: Record<DemoRole, string> = {
  student: 'Student',
  teacher: 'Teacher',
  client: 'Client Administrator',
  developer: 'Product Developer',
  admin: 'Platform Administrator',
  guardian: 'Guardian',
  'exam-controller': 'Exam Controller',
  'finance-admin': 'Finance Administrator',
  'teaching-assistant': 'Teaching Assistant',
  evaluator: 'Evaluator',
  mentor: 'Mentor',
  support: 'Support Operator',
  auditor: 'Auditor',
}

function activeActor(state: DemoState, actor: DemoProfile): DemoUser | undefined {
  return state.users.find(user => user.id === actor.userId && user.status === 'Active' && user.role === ROLE_RECORD_NAMES[actor.role])
}
function dateAt(dayOffset: number, hour: number, minute = 0): string {
  const date = new Date()
  date.setDate(date.getDate() + dayOffset)
  date.setHours(hour, minute, 0, 0)
  return date.toISOString()
}
function dateKey(dayOffset: number): string {
  const date = new Date()
  date.setDate(date.getDate() + dayOffset)
  return date.getFullYear() + '-' + String(date.getMonth() + 1).padStart(2, '0') + '-' + String(date.getDate()).padStart(2, '0')
}

function seedDemoState(): DemoState {
  const users: DemoUser[] = [
    { id: 'student-asha', name: 'Asha Sharma', email: 'asha.student@demo.uep', role: 'Student', status: 'Active', organization: 'Northstar Learning Institute', courseIds: ['course-web', 'course-data', 'course-cloud'] },
    { id: 'student-ravi', name: 'Ravi Nair', email: 'ravi.student@demo.uep', role: 'Student', status: 'Active', organization: 'Northstar Learning Institute', courseIds: ['course-web', 'course-data'] },
    { id: 'student-meera', name: 'Meera Iyer', email: 'meera.student@demo.uep', role: 'Student', status: 'Active', organization: 'Northstar Learning Institute', courseIds: ['course-web', 'course-cloud'] },
    { id: 'teacher-rahul', name: 'Rahul Deshmukh', email: 'rahul.teacher@demo.uep', role: 'Teacher', status: 'Active', organization: 'Northstar Learning Institute', courseIds: ['course-web', 'course-data', 'course-cloud'] },
    { id: 'client-priya', name: 'Priya Kulkarni', email: 'priya.client@demo.uep', role: 'Client Administrator', status: 'Active', organization: 'Northstar Learning Institute', courseIds: [] },
    { id: 'developer-dev', name: 'Dev Patel', email: 'dev.product@demo.uep', role: 'Product Developer', status: 'Active', organization: 'UEP Product Team', courseIds: [] },
    { id: 'admin-platform', name: 'Morgan Admin', email: 'admin.platform@demo.uep', role: 'Platform Administrator', status: 'Active', organization: 'UEP Platform', courseIds: [] },
    { id: 'guardian-nina', name: 'Nina Sharma', email: 'nina.guardian@demo.uep', role: 'Guardian', status: 'Active', organization: 'Northstar Learning Institute', courseIds: ['course-web', 'course-data', 'course-cloud'] },
    { id: 'exam-aarav', name: 'Aarav Shah', email: 'aarav.exams@demo.uep', role: 'Exam Controller', status: 'Active', organization: 'Northstar Learning Institute', courseIds: ['course-data'] },
    { id: 'finance-anaya', name: 'Anaya Joshi', email: 'anaya.finance@demo.uep', role: 'Finance Administrator', status: 'Active', organization: 'Northstar Learning Institute', courseIds: [] },
    { id: 'ta-ishaan', name: 'Ishaan Patil', email: 'ishaan.ta@demo.uep', role: 'Teaching Assistant', status: 'Active', organization: 'Northstar Learning Institute', courseIds: ['course-web'] },
    { id: 'evaluator-sara', name: 'Sara Khan', email: 'sara.evaluator@demo.uep', role: 'Evaluator', status: 'Active', organization: 'Northstar Learning Institute', courseIds: ['course-data'] },
    { id: 'mentor-rohan', name: 'Rohan Mehta', email: 'rohan.mentor@demo.uep', role: 'Mentor', status: 'Active', organization: 'Northstar Learning Institute', courseIds: ['course-web', 'course-cloud'] },
    { id: 'support-alex', name: 'Alex Support', email: 'alex.support@demo.uep', role: 'Support Operator', status: 'Active', organization: 'UEP Platform', courseIds: [] },
    { id: 'auditor-jordan', name: 'Jordan Audit', email: 'jordan.audit@demo.uep', role: 'Auditor', status: 'Active', organization: 'UEP Platform', courseIds: [] },
  ]
  const courses: DemoCourse[] = [
    { id: 'course-web', title: 'Full Stack Web Development', description: 'Frontend, APIs, persistence and deployment foundations.', category: 'Software Development', teacherId: 'teacher-rahul', lessonCount: 12, status: 'Published' },
    { id: 'course-data', title: 'Database Systems and SQL', description: 'Relational design, SQL queries, transactions and indexing.', category: 'Data Engineering', teacherId: 'teacher-rahul', lessonCount: 10, status: 'Published' },
    { id: 'course-cloud', title: 'Cloud and DevOps Foundations', description: 'Delivery pipelines, observability and reliable deployments.', category: 'Cloud Computing', teacherId: 'teacher-rahul', lessonCount: 8, status: 'Published' },
    { id: 'course-product-draft', title: 'Applied AI Product Design', description: 'A draft learning product under development.', category: 'Product Development', teacherId: 'teacher-rahul', lessonCount: 6, status: 'Draft' },
  ]
  const submissions: DemoSubmission[] = [
    { id: 'submission-asha-api', title: 'REST API design exercise', courseId: 'course-web', studentId: 'student-asha', dueAt: dateAt(1, 23, 59), status: 'Submitted', submittedAt: dateAt(-1, 16, 20) },
    { id: 'submission-asha-sql', title: 'SQL query challenge', courseId: 'course-data', studentId: 'student-asha', dueAt: dateAt(3, 23, 59), status: 'Graded', score: 88, feedback: 'Strong query structure. Add an index explanation to the final answer.', submittedAt: dateAt(-3, 18), gradedAt: dateAt(-2, 11) },
    { id: 'submission-asha-cloud', title: 'Deployment reflection', courseId: 'course-cloud', studentId: 'student-asha', dueAt: dateAt(5, 23, 59), status: 'Assigned' },
    { id: 'submission-ravi-api', title: 'REST API design exercise', courseId: 'course-web', studentId: 'student-ravi', dueAt: dateAt(1, 23, 59), status: 'Submitted', submittedAt: dateAt(-1, 15, 5) },
    { id: 'submission-meera-api', title: 'REST API design exercise', courseId: 'course-web', studentId: 'student-meera', dueAt: dateAt(1, 23, 59), status: 'Graded', score: 94, feedback: 'Clear API boundaries and excellent error handling.', submittedAt: dateAt(-1, 13, 25), gradedAt: dateAt(0, 9) },
    { id: 'submission-ravi-sql', title: 'SQL query challenge', courseId: 'course-data', studentId: 'student-ravi', dueAt: dateAt(3, 23, 59), status: 'Assigned' },
  ]
  const attendance: DemoAttendance[] = []
  for (const studentId of ['student-asha', 'student-ravi', 'student-meera']) {
    for (const offset of [-9, -8, -7, -6, -5, -4, -3, -2, -1, 0]) {
      attendance.push({
        id: studentId + '-attendance-' + offset,
        studentId,
        courseId: offset % 2 === 0 ? 'course-web' : 'course-data',
        date: dateKey(offset),
        status: offset === -6 && studentId === 'student-ravi' ? 'Absent' : offset === -4 && studentId === 'student-meera' ? 'Late' : 'Present',
      })
    }
  }
  const schedule: DemoScheduleItem[] = [
    { id: 'schedule-web-today', title: 'Full Stack Web Development', courseId: 'course-web', startsAt: dateAt(0, 10, 30), durationMinutes: 60, participantIds: ['student-asha', 'student-ravi', 'student-meera', 'teacher-rahul', 'ta-ishaan'], kind: 'Class', status: 'Scheduled' },
    { id: 'schedule-sql-exam', title: 'Database Systems checkpoint', courseId: 'course-data', startsAt: dateAt(1, 14), durationMinutes: 45, participantIds: ['student-asha', 'student-ravi', 'student-meera', 'teacher-rahul', 'exam-aarav'], kind: 'Exam', status: 'Scheduled' },
    { id: 'schedule-office-hour', title: 'Teacher office hours', courseId: 'course-web', startsAt: dateAt(2, 16), durationMinutes: 30, participantIds: ['student-asha', 'teacher-rahul', 'mentor-rohan'], kind: 'Office hour', status: 'Scheduled' },
    { id: 'schedule-cloud-class', title: 'Cloud and DevOps Foundations', courseId: 'course-cloud', startsAt: dateAt(3, 11), durationMinutes: 60, participantIds: ['student-asha', 'student-meera', 'teacher-rahul'], kind: 'Class', status: 'Scheduled' },
  ]
  const products: DemoProduct[] = [
    { id: 'product-web-course', title: 'Full Stack Web Development', description: 'Published course product for software development learners.', version: 3, status: 'Published', updatedAt: dateAt(-2, 14) },
    { id: 'product-sql-course', title: 'Database Systems and SQL', description: 'SQL and relational database learning path.', version: 2, status: 'Published', updatedAt: dateAt(-5, 12) },
    { id: 'product-ai-draft', title: 'Applied AI Product Design', description: 'Draft curriculum awaiting author review.', version: 1, status: 'Draft', updatedAt: dateAt(-1, 17) },
  ]
  const webhooks: DemoWebhook[] = [
    { id: 'webhook-enrollment', name: 'Enrollment events', endpoint: 'https://client.example.test/hooks/enrollment', status: 'Healthy', successfulDeliveries: 142, failedDeliveries: 1 },
    { id: 'webhook-results', name: 'Published results', endpoint: 'https://client.example.test/hooks/results', status: 'Healthy', successfulDeliveries: 86, failedDeliveries: 0 },
    { id: 'webhook-attendance', name: 'Attendance updates', endpoint: 'https://client.example.test/hooks/attendance', status: 'Needs attention', successfulDeliveries: 39, failedDeliveries: 4 },
  ]
  return {
    schemaVersion: 1,
    users,
    courses,
    submissions,
    attendance,
    schedule,
    products,
    webhooks,
    courseProgress: {
      'student-asha:course-web': 68, 'student-asha:course-data': 82, 'student-asha:course-cloud': 34,
      'student-ravi:course-web': 54, 'student-ravi:course-data': 61,
      'student-meera:course-web': 91, 'student-meera:course-cloud': 77,
    },
    activity: [
      { id: 'activity-seed-1', at: dateAt(-1, 16, 20), actor: 'Asha Sharma', action: 'Assignment submitted', detail: 'REST API design exercise' },
      { id: 'activity-seed-2', at: dateAt(-2, 11), actor: 'Rahul Deshmukh', action: 'Grade published', detail: 'SQL query challenge · 88/100' },
      { id: 'activity-seed-3', at: dateAt(-3, 10), actor: 'Dev Patel', action: 'Product updated', detail: 'Applied AI Product Design draft' },
    ],
  }
}

export function loadDemoState(): DemoState {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY)
    if (raw) {
      const parsed: unknown = JSON.parse(raw)
      if (isDemoState(parsed)) return parsed
    }
  } catch {
    // Corrupt/unavailable browser storage starts a fresh local demonstration.
  }
  const initial = seedDemoState()
  saveDemoState(initial)
  return initial
}

function isDemoState(value: unknown): value is DemoState {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Partial<DemoState>
  return candidate.schemaVersion === 1
    && Array.isArray(candidate.users)
    && Array.isArray(candidate.courses)
    && Array.isArray(candidate.submissions)
    && Array.isArray(candidate.attendance)
    && Array.isArray(candidate.schedule)
    && Array.isArray(candidate.products)
    && Array.isArray(candidate.webhooks)
    && Array.isArray(candidate.activity)
    && typeof candidate.courseProgress === 'object'
    && candidate.courseProgress !== null
}

export function saveDemoState(state: DemoState): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(state))
  } catch {
    // The current tab remains usable even when browser storage is unavailable.
  }
  // The StorageEvent is not emitted in the tab that made the write, so notify
  // current-tab subscribers separately and other tabs through the browser event.
  window.dispatchEvent(new Event(STORE_CHANGED_EVENT))
}

export function subscribeDemoState(listener: () => void): () => void {
  const onStorage = (event: StorageEvent) => {
    if (event.key === STORAGE_KEY || event.key === null) listener()
  }
  window.addEventListener('storage', onStorage)
  window.addEventListener(STORE_CHANGED_EVENT, listener)
  return () => {
    window.removeEventListener('storage', onStorage)
    window.removeEventListener(STORE_CHANGED_EVENT, listener)
  }
}

export function resetDemoState(): DemoState {
  const state = seedDemoState()
  saveDemoState(state)
  return state
}

export function recordDemoActivity(state: DemoState, actor: string, action: string, detail: string): DemoState {
  const item: DemoActivity = { id: 'activity-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8), at: new Date().toISOString(), actor, action, detail }
  return { ...state, activity: [item, ...state.activity].slice(0, 50) }
}

export function submitDemoAssignment(state: DemoState, submissionId: string, actor: DemoProfile): DemoState {
  if (actor.role !== 'student') return state
  const user = activeActor(state, actor)
  const target = state.submissions.find(item => item.id === submissionId && item.studentId === actor.userId && item.status === 'Assigned')
  const course = target && courseFor(state, target.courseId)
  if (!user || !target || !course || course.status !== 'Published' || !user.courseIds.includes(target.courseId)) return state
  const submissions = state.submissions.map(item => item.id === submissionId ? { ...item, status: 'Submitted' as const, submittedAt: new Date().toISOString() } : item)
  return recordDemoActivity({ ...state, submissions }, actor.name, 'Assignment submitted', target.title)
}

export function gradeDemoSubmission(state: DemoState, submissionId: string, score: number, feedback: string, actor: DemoProfile): DemoState {
  if (!['teacher', 'evaluator', 'teaching-assistant'].includes(actor.role)) return state
  const user = activeActor(state, actor)
  const target = state.submissions.find(item => item.id === submissionId && item.status === 'Submitted')
  const learner = target && state.users.find(item => item.id === target.studentId && item.role === 'Student' && item.status === 'Active')
  if (!user || !target || !learner || !user.courseIds.includes(target.courseId) || !learner.courseIds.includes(target.courseId)
      || !Number.isFinite(score) || score < 0 || score > 100) return state
  const submissions = state.submissions.map(item => item.id === submissionId ? { ...item, status: 'Graded' as const, score: Math.round(score), feedback: feedback.trim() || 'Reviewed and graded.', gradedAt: new Date().toISOString() } : item)
  return recordDemoActivity({ ...state, submissions }, actor.name, 'Grade published', target.title + ' · ' + Math.round(score) + '/100 for ' + learner.name)
}

export function advanceDemoProgress(state: DemoState, courseId: string, actor: DemoProfile): DemoState {
  if (actor.role !== 'student') return state
  const user = activeActor(state, actor)
  const course = courseFor(state, courseId)
  if (!user || !course || course.status !== 'Published' || !user.courseIds.includes(courseId)) return state
  const key = actor.userId + ':' + courseId
  const current = state.courseProgress[key] ?? 0
  if (current >= 100) return state
  const nextProgress = { ...state.courseProgress, [key]: Math.min(100, current + 10) }
  return recordDemoActivity({ ...state, courseProgress: nextProgress }, actor.name, 'Learning progress updated', course.title + ' · ' + Math.min(100, current + 10) + '% complete')
}

export function updateDemoAttendance(state: DemoState, attendanceId: string, status: DemoAttendance['status'], actor: DemoProfile): DemoState {
  if (!['teacher', 'admin'].includes(actor.role)) return state
  const user = activeActor(state, actor)
  const target = state.attendance.find(item => item.id === attendanceId)
  const learner = target && state.users.find(item => item.id === target.studentId && item.role === 'Student' && item.status === 'Active')
  if (!user || !target || !learner || !learner.courseIds.includes(target.courseId)
      || (actor.role === 'teacher' && !user.courseIds.includes(target.courseId))) return state
  if (target.status === status) return state
  const attendance = state.attendance.map(item => item.id === attendanceId ? { ...item, status } : item)
  const course = courseFor(state, target.courseId)
  return recordDemoActivity({ ...state, attendance }, actor.name, 'Attendance recorded', learner.name + ' · ' + (course?.title ?? 'Course') + ' · ' + status + ' (' + target.date + ')')
}

export function createDemoProduct(state: DemoState, title: string, description: string, actor: DemoProfile): DemoState {
  if (actor.role !== 'developer' || !activeActor(state, actor)) return state
  const cleanTitle = title.trim()
  if (!cleanTitle) return state
  const product: DemoProduct = { id: 'product-' + Date.now(), title: cleanTitle, description: description.trim() || 'New educational product draft.', version: 1, status: 'Draft', updatedAt: new Date().toISOString() }
  return recordDemoActivity({ ...state, products: [product, ...state.products] }, actor.name, 'Product created', cleanTitle)
}

export function publishDemoProduct(state: DemoState, productId: string, actor: DemoProfile): DemoState {
  if (actor.role !== 'developer' || !activeActor(state, actor)) return state
  const target = state.products.find(item => item.id === productId && item.status !== 'Published')
  if (!target) return state
  const products = state.products.map(item => item.id === productId ? { ...item, status: 'Published' as const, version: item.version + 1, updatedAt: new Date().toISOString() } : item)
  return recordDemoActivity({ ...state, products }, actor.name, 'Product published', target.title)
}

export function setDemoUserStatus(state: DemoState, userId: string, status: DemoUser['status'], actor: DemoProfile): DemoState {
  if (actor.role !== 'admin' || !activeActor(state, actor)) return state
  const target = state.users.find(item => item.id === userId)
  if (!target || target.id === actor.userId || target.role === 'Platform Administrator' || target.status === status) return state
  const users = state.users.map(item => item.id === userId ? { ...item, status } : item)
  return recordDemoActivity({ ...state, users }, actor.name, 'User status updated', target.name + ': ' + status)
}

export function submissionsForProfile(state: DemoState, profile: DemoProfile): DemoSubmission[] {
  const currentUser = state.users.find(user =>
    user.id === profile.userId
    && user.status === 'Active'
    && user.role === ROLE_RECORD_NAMES[profile.role])
  if (!currentUser) return []

  if (profile.role === 'student') {
    return state.submissions.filter(item => item.studentId === profile.userId)
  }
  if (profile.role === 'guardian') {
    return state.submissions.filter(item => item.studentId === 'student-asha')
  }
  if (profile.role === 'mentor') {
    const assigned = new Set(currentUser.courseIds)
    return state.submissions.filter(item => item.studentId === 'student-asha' && assigned.has(item.courseId))
  }
  if (['teacher', 'teaching-assistant', 'evaluator'].includes(profile.role)) {
    const assigned = new Set(currentUser.courseIds)
    return state.submissions.filter(item => assigned.has(item.courseId))
  }
  if (profile.role === 'exam-controller') {
    const examCourseIds = new Set(state.schedule
      .filter(item => item.kind === 'Exam' && item.participantIds.includes(profile.userId))
      .map(item => item.courseId))
    return state.submissions.filter(item => examCourseIds.has(item.courseId))
  }
  if (profile.role === 'client') {
    const learners = new Set(state.users
      .filter(user => user.role === 'Student' && user.status === 'Active' && user.organization === currentUser.organization)
      .map(user => user.id))
    return state.submissions.filter(item => learners.has(item.studentId))
  }
  if (profile.role === 'admin') return state.submissions
  return []
}

export function activityForProfile(state: DemoState, profile: DemoProfile): DemoActivity[] {
  const actor = activeActor(state, profile)
  if (!actor) return []

  // Platform administrators and auditors can review the complete synthetic audit feed.
  if (profile.role === 'admin' || profile.role === 'auditor') return state.activity

  // These personas must not infer individual learning details from a global event feed.
  if (profile.role === 'support' || profile.role === 'finance-admin') return []

  if (profile.role === 'developer') {
    const productTitles = state.products.map(item => item.title.toLocaleLowerCase())
    return state.activity.filter(item => item.action.toLocaleLowerCase().includes('product')
      && productTitles.some(title => item.detail.toLocaleLowerCase().includes(title)))
  }

  const submissions = submissionsForProfile(state, profile)
  const scopedSubmissionTitles = submissions.map(item => item.title.toLocaleLowerCase())
  let scopedCourseIds = new Set(actor.courseIds)
  let scopedLearnerIds = new Set<string>()

  if (profile.role === 'student') {
    scopedLearnerIds = new Set([profile.userId])
  } else if (profile.role === 'guardian' || profile.role === 'mentor') {
    scopedLearnerIds = new Set(['student-asha'])
    const linked = state.users.find(item => item.id === 'student-asha')
    if (profile.role === 'guardian') scopedCourseIds = new Set(linked?.courseIds ?? [])
  } else if (profile.role === 'client') {
    const learners = state.users.filter(item => item.role === 'Student'
      && item.status === 'Active' && item.organization === actor.organization)
    scopedLearnerIds = new Set(learners.map(item => item.id))
    scopedCourseIds = new Set(learners.flatMap(item => item.courseIds))
  } else if (profile.role === 'exam-controller') {
    scopedCourseIds = new Set(state.schedule
      .filter(item => item.kind === 'Exam' && item.participantIds.includes(profile.userId))
      .map(item => item.courseId))
  }

  const scopedCourseTitles = state.courses
    .filter(item => scopedCourseIds.has(item.id))
    .map(item => item.title.toLocaleLowerCase())
  const scopedLearnerNames = state.users
    .filter(item => scopedLearnerIds.has(item.id))
    .map(item => item.name.toLocaleLowerCase())

  return state.activity.filter(item => {
    const action = item.action.toLocaleLowerCase()
    const detail = item.detail.toLocaleLowerCase()
    if (action.includes('product')) return false

    // Assignment and grading events are tied back to the learner/course records.
    if (scopedSubmissionTitles.some(title => detail.includes(title))) return true

    // Attendance/progress events include course context; learner-facing roles additionally
    // require the linked learner name to be present in the event detail.
    if (action.includes('attendance') || action.includes('learning progress')) {
      const courseMatches = scopedCourseTitles.some(title => detail.includes(title))
      const learnerMatches = ['student', 'guardian', 'mentor'].includes(profile.role)
        ? scopedLearnerNames.some(name => detail.includes(name))
        : profile.role === 'client'
          ? scopedLearnerNames.some(name => detail.includes(name))
          : true
      return courseMatches && learnerMatches
    }

    // Do not leak unattributed events when their relationship cannot be established.
    return false
  })
}

export function courseFor(state: DemoState, courseId: string): DemoCourse | undefined { return state.courses.find(course => course.id === courseId) }
export function progressFor(state: DemoState, userId: string, courseId: string): number { return state.courseProgress[userId + ':' + courseId] ?? 0 }
export function percentage(numerator: number, denominator: number): number { return denominator > 0 ? Math.round((numerator / denominator) * 100) : 0 }
