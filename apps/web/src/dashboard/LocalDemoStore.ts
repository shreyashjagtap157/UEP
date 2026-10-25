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
  examScheduleId?: string
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
  subjectUserId?: string
  courseId?: string
  submissionId?: string
  organization?: string
  productId?: string
}
export interface DemoOrganizationAction {
  id: string
  organization: string
  title: string
  description: string
  status: 'Open' | 'Resolved'
  updatedAt: string
}

export interface DemoInvoice {
  id: string
  invoiceNumber: string
  studentId: string
  organization: string
  description: string
  amountInr: number
  issuedAt: string
  dueAt: string
  status: 'Open' | 'Overdue' | 'Paid'
  paidAt?: string
}

export interface DemoSupportCase {
  id: string
  reference: string
  organization: string
  category: string
  summary: string
  priority: 'Low' | 'Medium' | 'High'
  status: 'Open' | 'In progress' | 'Resolved'
  createdAt: string
  updatedAt: string
}

export interface DemoMessage {
  id: string
  senderId: string
  recipientIds: string[]
  subject: string
  body: string
  sentAt: string
  readBy: string[]
}

export interface DemoState {
  schemaVersion: 4
  users: DemoUser[]
  courses: DemoCourse[]
  submissions: DemoSubmission[]
  attendance: DemoAttendance[]
  schedule: DemoScheduleItem[]
  products: DemoProduct[]
  webhooks: DemoWebhook[]
  organizationActions: DemoOrganizationAction[]
  invoices: DemoInvoice[]
  supportCases: DemoSupportCase[]
  messages: DemoMessage[]
  courseProgress: Record<string, number>
  activity: DemoActivity[]
}

const STORAGE_KEY = 'uep.local-demo-data.v4'
const LEGACY_STORAGE_KEYS = ['uep.local-demo-data.v3', 'uep.local-demo-data.v2', 'uep.local-demo-data.v1'] as const
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

export function activeUserForProfile(state: DemoState, profile: DemoProfile): DemoUser | undefined {
  return activeActor(state, profile)
}

export function linkedLearnerForProfile(state: DemoState, profile: DemoProfile): DemoUser | undefined {
  const actor = activeActor(state, profile)
  if (!actor || (profile.role !== 'guardian' && profile.role !== 'mentor')) return undefined
  return state.users.find(user => user.id === 'student-asha'
    && user.role === 'Student'
    && user.status === 'Active'
    && user.organization === actor.organization)
}

export function learnersForProfile(state: DemoState, profile: DemoProfile): DemoUser[] {
  const actor = activeActor(state, profile)
  if (!actor) return []
  if (profile.role === 'admin') return state.users
  if (profile.role === 'client') {
    return state.users.filter(user => user.role === 'Student'
      && user.status === 'Active'
      && user.organization === actor.organization)
  }
  if (['teacher', 'teaching-assistant', 'evaluator'].includes(profile.role)) {
    const assignedPublishedCourses = new Set(actor.courseIds.filter(courseId => courseFor(state, courseId)?.status === 'Published'))
    return state.users.filter(user => user.role === 'Student'
      && user.status === 'Active'
      && user.organization === actor.organization
      && user.courseIds.some(courseId => assignedPublishedCourses.has(courseId)))
  }
  return []
}

export function coursesForProfile(state: DemoState, profile: DemoProfile): DemoCourse[] {
  const actor = activeActor(state, profile)
  if (!actor) return []
  if (profile.role === 'admin') return state.courses
  if (profile.role === 'student') {
    return state.courses.filter(course => course.status === 'Published' && actor.courseIds.includes(course.id))
  }
  if (profile.role === 'teacher') {
    return state.courses.filter(course => actor.courseIds.includes(course.id))
  }
  if (profile.role === 'guardian' || profile.role === 'mentor') {
    const learner = linkedLearnerForProfile(state, profile)
    if (!learner) return []
    const allowedCourseIds = profile.role === 'guardian'
      ? new Set(learner.courseIds)
      : new Set(actor.courseIds.filter(courseId => learner.courseIds.includes(courseId)))
    return state.courses.filter(course => course.status === 'Published' && allowedCourseIds.has(course.id))
  }
  if (profile.role === 'client') {
    const learners = learnersForProfile(state, profile)
    const enrolledCourseIds = new Set(learners.flatMap(learner => learner.courseIds))
    return state.courses.filter(course => course.status === 'Published' && enrolledCourseIds.has(course.id))
  }
  return []
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
    { id: 'student-meera', name: 'Meera Iyer', email: 'meera.student@demo.uep', role: 'Student', status: 'Active', organization: 'Northstar Learning Institute', courseIds: ['course-web', 'course-data', 'course-cloud'] },
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
    { id: 'submission-exam-asha', title: 'Database Systems checkpoint', courseId: 'course-data', studentId: 'student-asha', dueAt: dateAt(1, 14, 45), status: 'Assigned', examScheduleId: 'schedule-sql-exam' },
    { id: 'submission-exam-ravi', title: 'Database Systems checkpoint', courseId: 'course-data', studentId: 'student-ravi', dueAt: dateAt(1, 14, 45), status: 'Assigned', examScheduleId: 'schedule-sql-exam' },
    { id: 'submission-exam-meera', title: 'Database Systems checkpoint', courseId: 'course-data', studentId: 'student-meera', dueAt: dateAt(1, 14, 45), status: 'Assigned', examScheduleId: 'schedule-sql-exam' },
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
  const organizationActions: DemoOrganizationAction[] = [
    { id: 'org-action-attendance-review', organization: 'Northstar Learning Institute', title: 'Review attendance exceptions', description: 'Review the recent attendance register and confirm follow-up for missed sessions.', status: 'Open', updatedAt: dateAt(-1, 15) },
    { id: 'org-action-onboarding', organization: 'Northstar Learning Institute', title: 'Confirm learner onboarding', description: 'Confirm that active learners can access their published course catalog.', status: 'Open', updatedAt: dateAt(-2, 11) },
    { id: 'org-action-outcomes-review', organization: 'Northstar Learning Institute', title: 'Review monthly learning outcomes', description: 'Review the course progress snapshot and retain next-step actions for the academic team.', status: 'Resolved', updatedAt: dateAt(-4, 14) },
  ]
  const invoices: DemoInvoice[] = [
    { id: 'invoice-asha-2026-041', invoiceNumber: 'NLI-2026-041', studentId: 'student-asha', organization: 'Northstar Learning Institute', description: 'Full Stack Web Development · Term 2', amountInr: 12500, issuedAt: dateAt(-18, 10), dueAt: dateAt(-2, 23, 59), status: 'Overdue' },
    { id: 'invoice-ravi-2026-042', invoiceNumber: 'NLI-2026-042', studentId: 'student-ravi', organization: 'Northstar Learning Institute', description: 'Database Systems and SQL · Term 2', amountInr: 8400, issuedAt: dateAt(-5, 10), dueAt: dateAt(7, 23, 59), status: 'Open' },
    { id: 'invoice-meera-2026-037', invoiceNumber: 'NLI-2026-037', studentId: 'student-meera', organization: 'Northstar Learning Institute', description: 'Cloud and DevOps Foundations · Term 1', amountInr: 9600, issuedAt: dateAt(-24, 10), dueAt: dateAt(-10, 23, 59), status: 'Paid', paidAt: dateAt(-12, 12) },
  ]
  const supportCases: DemoSupportCase[] = [
    { id: 'support-418', reference: 'UEP-418', organization: 'Northstar Learning Institute', category: 'Course access', summary: 'A learner cannot reopen a course after a session expired.', priority: 'High', status: 'Open', createdAt: dateAt(-1, 9, 20), updatedAt: dateAt(-1, 9, 20) },
    { id: 'support-425', reference: 'UEP-425', organization: 'Northstar Learning Institute', category: 'Live learning', summary: 'Video playback buffers during a low-bandwidth session.', priority: 'Medium', status: 'In progress', createdAt: dateAt(-2, 13, 5), updatedAt: dateAt(-1, 15, 10) },
    { id: 'support-431', reference: 'UEP-431', organization: 'Northstar Learning Institute', category: 'Accessibility', summary: 'An attendance table needs a clearer screen-reader label.', priority: 'Low', status: 'Open', createdAt: dateAt(-3, 11, 30), updatedAt: dateAt(-3, 11, 30) },
  ]
  const messages: DemoMessage[] = [
    { id: 'message-student-weekly', senderId: 'teacher-rahul', recipientIds: ['student-asha', 'guardian-nina'], subject: 'Weekly learning update', body: 'Asha has made progress across her enrolled courses. Review course progress and latest feedback in the learning workspace.', sentAt: dateAt(-1, 9), readBy: [] },
    { id: 'message-student-feedback', senderId: 'teacher-rahul', recipientIds: ['student-asha'], subject: 'Feedback on SQL query challenge', body: 'Your SQL query has been graded. Review the result and feedback in your submissions panel.', sentAt: dateAt(-2, 12), readBy: [] },
    { id: 'message-client-delivery', senderId: 'client-priya', recipientIds: ['teacher-rahul', 'admin-platform'], subject: 'Delivery review reminder', body: 'Please review the current attendance exceptions and note follow-up actions for the academic team.', sentAt: dateAt(-1, 11), readBy: [] },
    { id: 'message-support-update', senderId: 'support-alex', recipientIds: ['client-priya'], subject: 'Support case update', body: 'The low-bandwidth video playback case is being investigated by support.', sentAt: dateAt(-1, 16), readBy: [] },
  ]
  const webhooks: DemoWebhook[] = [
    { id: 'webhook-enrollment', name: 'Enrollment events', endpoint: 'https://client.example.test/hooks/enrollment', status: 'Healthy', successfulDeliveries: 142, failedDeliveries: 1 },
    { id: 'webhook-results', name: 'Published results', endpoint: 'https://client.example.test/hooks/results', status: 'Healthy', successfulDeliveries: 86, failedDeliveries: 0 },
    { id: 'webhook-attendance', name: 'Attendance updates', endpoint: 'https://client.example.test/hooks/attendance', status: 'Needs attention', successfulDeliveries: 39, failedDeliveries: 4 },
  ]
  return {
    schemaVersion: 4,
    users,
    courses,
    submissions,
    attendance,
    schedule,
    products,
    webhooks,
    organizationActions,
    invoices,
    supportCases,
    messages,
    courseProgress: {
      'student-asha:course-web': 68, 'student-asha:course-data': 82, 'student-asha:course-cloud': 34,
      'student-ravi:course-web': 54, 'student-ravi:course-data': 61,
      'student-meera:course-web': 91, 'student-meera:course-data': 46, 'student-meera:course-cloud': 77,
    },
    activity: [
      { id: 'activity-seed-1', at: dateAt(-1, 16, 20), actor: 'Asha Sharma', action: 'Assignment submitted', detail: 'REST API design exercise' },
      { id: 'activity-seed-2', at: dateAt(-2, 11), actor: 'Rahul Deshmukh', action: 'Grade published', detail: 'SQL query challenge · 88/100' },
      { id: 'activity-seed-3', at: dateAt(-3, 10), actor: 'Dev Patel', action: 'Product updated', detail: 'Applied AI Product Design draft' },
    ],
  }
}

type PersistedDemoState = Omit<DemoState, 'schemaVersion' | 'organizationActions' | 'invoices' | 'supportCases' | 'messages'> & {
  schemaVersion: 1 | 2 | 3 | 4
  organizationActions?: DemoOrganizationAction[]
  invoices?: DemoInvoice[]
  supportCases?: DemoSupportCase[]
  messages?: DemoMessage[]
}

export function loadDemoState(): DemoState {
  try {
    const candidates = [STORAGE_KEY, ...LEGACY_STORAGE_KEYS]
      .map(key => ({ key, raw: window.localStorage.getItem(key) }))
    for (const stored of candidates) {
      if (!stored.raw) continue
      const parsed: unknown = JSON.parse(stored.raw)
      if (!isPersistedDemoState(parsed)) continue
      const seed = seedDemoState()
      const state: DemoState = {
        ...parsed,
        schemaVersion: 4,
        organizationActions: Array.isArray(parsed.organizationActions) ? parsed.organizationActions : seed.organizationActions,
        invoices: Array.isArray(parsed.invoices) ? parsed.invoices : seed.invoices,
        supportCases: Array.isArray(parsed.supportCases) ? parsed.supportCases : seed.supportCases,
        messages: Array.isArray(parsed.messages) ? parsed.messages : seed.messages,
      }
      if (stored.key !== STORAGE_KEY || parsed.schemaVersion !== 4
          || !Array.isArray(parsed.organizationActions)
          || !Array.isArray(parsed.invoices)
          || !Array.isArray(parsed.supportCases)
          || !Array.isArray(parsed.messages)) {
        saveDemoState(state)
      }
      return state
    }
  } catch {
    // Corrupt/unavailable browser storage starts a fresh local demonstration.
  }
  const initial = seedDemoState()
  saveDemoState(initial)
  return initial
}

function isPersistedDemoState(value: unknown): value is PersistedDemoState {
  if (typeof value !== 'object' || value === null) return false
  const candidate = value as Partial<PersistedDemoState>
  return (candidate.schemaVersion === 1 || candidate.schemaVersion === 2 || candidate.schemaVersion === 3 || candidate.schemaVersion === 4)
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
    if (event.key === STORAGE_KEY || event.key === null || LEGACY_STORAGE_KEYS.some(key => key === event.key)) listener()
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

export function recordDemoActivity(
  state: DemoState,
  actor: string,
  action: string,
  detail: string,
  scope: Partial<Pick<DemoActivity, 'subjectUserId' | 'courseId' | 'submissionId' | 'organization' | 'productId'>> = {},
): DemoState {
  const item: DemoActivity = {
    id: 'activity-' + Date.now() + '-' + Math.random().toString(36).slice(2, 8),
    at: new Date().toISOString(),
    actor,
    action,
    detail,
    ...scope,
  }
  return { ...state, activity: [item, ...state.activity].slice(0, 50) }
}

export function submitDemoAssignment(state: DemoState, submissionId: string, actor: DemoProfile): DemoState {
  if (actor.role !== 'student') return state
  const user = activeActor(state, actor)
  const target = state.submissions.find(item => item.id === submissionId && item.studentId === actor.userId && item.status === 'Assigned' && !item.examScheduleId)
  const course = target && courseFor(state, target.courseId)
  if (!user || !target || !course || course.status !== 'Published' || !user.courseIds.includes(target.courseId)) return state
  const submissions = state.submissions.map(item => item.id === submissionId ? { ...item, status: 'Submitted' as const, submittedAt: new Date().toISOString() } : item)
  return recordDemoActivity({ ...state, submissions }, actor.name, 'Assignment submitted', target.title, {
    subjectUserId: target.studentId,
    courseId: target.courseId,
    submissionId: target.id,
    organization: user.organization,
  })
}

export function submitDemoExam(state: DemoState, submissionId: string, actor: DemoProfile): DemoState {
  if (actor.role !== 'student') return state
  const user = activeActor(state, actor)
  const target = state.submissions.find(item => item.id === submissionId
    && item.studentId === actor.userId
    && item.status === 'Assigned'
    && item.examScheduleId !== undefined)
  const course = target && courseFor(state, target.courseId)
  const session = target?.examScheduleId
    ? state.schedule.find(item => item.id === target.examScheduleId)
    : undefined
  if (!user || !target || !course || course.status !== 'Published'
      || !session || session.kind !== 'Exam' || session.courseId !== target.courseId
      || !session.participantIds.includes(user.id)
      || !user.courseIds.includes(target.courseId)) return state

  const submissions = state.submissions.map(item => item.id === target.id
    ? { ...item, status: 'Submitted' as const, submittedAt: new Date().toISOString() }
    : item)
  return recordDemoActivity({ ...state, submissions }, actor.name, 'Exam attempt submitted', target.title, {
    subjectUserId: user.id,
    courseId: target.courseId,
    submissionId: target.id,
    organization: user.organization,
  })
}

export function gradeDemoSubmission(state: DemoState, submissionId: string, score: number, feedback: string, actor: DemoProfile): DemoState {
  if (!['teacher', 'evaluator', 'teaching-assistant'].includes(actor.role)) return state
  const user = activeActor(state, actor)
  const target = state.submissions.find(item => item.id === submissionId && item.status === 'Submitted' && !item.examScheduleId)
  const learner = target && state.users.find(item => item.id === target.studentId && item.role === 'Student' && item.status === 'Active')
  const course = target && courseFor(state, target.courseId)
  if (!user || !target || !learner || !course || course.status !== 'Published'
      || learner.organization !== user.organization
      || !user.courseIds.includes(target.courseId) || !learner.courseIds.includes(target.courseId)
      || !Number.isFinite(score) || score < 0 || score > 100) return state
  const submissions = state.submissions.map(item => item.id === submissionId ? { ...item, status: 'Graded' as const, score: Math.round(score), feedback: feedback.trim() || 'Reviewed and graded.', gradedAt: new Date().toISOString() } : item)
  return recordDemoActivity({ ...state, submissions }, actor.name, 'Grade published', target.title + ' · ' + Math.round(score) + '/100 for ' + learner.name, {
    subjectUserId: learner.id,
    courseId: target.courseId,
    submissionId: target.id,
    organization: learner.organization,
  })
}

export function gradeDemoExamSubmission(state: DemoState, submissionId: string, score: number, feedback: string, actor: DemoProfile): DemoState {
  if (actor.role !== 'exam-controller') return state
  const user = activeActor(state, actor)
  const target = state.submissions.find(item => item.id === submissionId
    && item.status === 'Submitted'
    && item.examScheduleId !== undefined)
  const session = target?.examScheduleId
    ? state.schedule.find(item => item.id === target.examScheduleId)
    : undefined
  const learner = target && state.users.find(item => item.id === target.studentId
    && item.role === 'Student' && item.status === 'Active')
  const course = target && courseFor(state, target.courseId)
  if (!user || !target || !session || session.kind !== 'Exam'
      || !session.participantIds.includes(user.id)
      || session.courseId !== target.courseId
      || !learner || learner.organization !== user.organization
      || !learner.courseIds.includes(target.courseId)
      || !user.courseIds.includes(target.courseId)
      || !course || course.status !== 'Published'
      || !Number.isFinite(score) || score < 0 || score > 100) return state

  const submissions = state.submissions.map(item => item.id === target.id
    ? { ...item, status: 'Graded' as const, score: Math.round(score), feedback: feedback.trim() || 'Exam reviewed.', gradedAt: new Date().toISOString() }
    : item)
  return recordDemoActivity({ ...state, submissions }, actor.name, 'Exam result published',
    target.title + ' · ' + Math.round(score) + '/100 for ' + learner.name, {
      subjectUserId: learner.id,
      courseId: target.courseId,
      submissionId: target.id,
      organization: learner.organization,
    })
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
  return recordDemoActivity({ ...state, courseProgress: nextProgress }, actor.name, 'Learning progress updated', course.title + ' · ' + Math.min(100, current + 10) + '% complete', {
    subjectUserId: user.id,
    courseId,
    organization: user.organization,
  })
}

export function updateDemoAttendance(state: DemoState, attendanceId: string, status: DemoAttendance['status'], actor: DemoProfile): DemoState {
  if (!['teacher', 'admin'].includes(actor.role)) return state
  const user = activeActor(state, actor)
  const target = state.attendance.find(item => item.id === attendanceId)
  const learner = target && state.users.find(item => item.id === target.studentId && item.role === 'Student' && item.status === 'Active')
  const course = target && courseFor(state, target.courseId)
  if (!user || !target || !learner || !course || course.status !== 'Published' || !learner.courseIds.includes(target.courseId)
      || (actor.role === 'teacher' && (learner.organization !== user.organization || !user.courseIds.includes(target.courseId)))) return state
  if (target.status === status) return state
  const attendance = state.attendance.map(item => item.id === attendanceId ? { ...item, status } : item)
  return recordDemoActivity({ ...state, attendance }, actor.name, 'Attendance recorded', learner.name + ' · ' + course.title + ' · ' + status + ' (' + target.date + ')', {
    subjectUserId: learner.id,
    courseId: target.courseId,
    organization: learner.organization,
  })
}

export function createDemoProduct(state: DemoState, title: string, description: string, actor: DemoProfile): DemoState {
  const user = activeActor(state, actor)
  if (actor.role !== 'developer' || !user) return state
  const cleanTitle = title.trim()
  if (!cleanTitle) return state
  const baseId = 'product-' + Date.now()
  let id = baseId
  let suffix = 1
  while (state.products.some(existing => existing.id === id)) {
    id = baseId + '-' + suffix
    suffix += 1
  }
  const product: DemoProduct = { id, title: cleanTitle, description: description.trim() || 'New educational product draft.', version: 1, status: 'Draft', updatedAt: new Date().toISOString() }
  return recordDemoActivity({ ...state, products: [product, ...state.products] }, actor.name, 'Product created', cleanTitle, {
    productId: product.id,
    organization: user.organization,
  })
}

export function publishDemoProduct(state: DemoState, productId: string, actor: DemoProfile): DemoState {
  const user = activeActor(state, actor)
  if (actor.role !== 'developer' || !user) return state
  const target = state.products.find(item => item.id === productId && item.status !== 'Published')
  if (!target) return state
  const products = state.products.map(item => item.id === productId ? { ...item, status: 'Published' as const, version: item.version + 1, updatedAt: new Date().toISOString() } : item)
  return recordDemoActivity({ ...state, products }, actor.name, 'Product published', target.title, {
    productId: target.id,
    organization: user.organization,
  })
}

export function publishDemoCourse(state: DemoState, courseId: string, actor: DemoProfile): DemoState {
  if (actor.role !== 'admin' || !activeActor(state, actor)) return state
  const target = state.courses.find(item => item.id === courseId && item.status === 'Draft')
  if (!target) return state
  const courses = state.courses.map(item => item.id === courseId ? { ...item, status: 'Published' as const } : item)
  return recordDemoActivity({ ...state, courses }, actor.name, 'Course published', target.title, {
    courseId: target.id,
  })
}

export function resolveDemoOrganizationAction(state: DemoState, actionId: string, actor: DemoProfile): DemoState {
  if (actor.role !== 'client') return state
  const user = activeActor(state, actor)
  const target = state.organizationActions.find(item =>
    item.id === actionId && item.organization === user?.organization && item.status === 'Open')
  if (!user || !target) return state
  const updatedAt = new Date().toISOString()
  const organizationActions = state.organizationActions.map(item =>
    item.id === actionId ? { ...item, status: 'Resolved' as const, updatedAt } : item)
  return recordDemoActivity(
    { ...state, organizationActions },
    actor.name,
    'Organization action resolved',
    target.title,
    { organization: target.organization },
  )
}

export function messagesForProfile(state: DemoState, profile: DemoProfile): DemoMessage[] {
  if (!activeActor(state, profile)) return []
  return state.messages.filter(message => message.recipientIds.includes(profile.userId))
}

export function markDemoMessageRead(state: DemoState, messageId: string, actor: DemoProfile): DemoState {
  if (!activeActor(state, actor)) return state
  const target = state.messages.find(message => message.id === messageId
    && message.recipientIds.includes(actor.userId)
    && !message.readBy.includes(actor.userId))
  if (!target) return state
  const messages = state.messages.map(message => message.id === messageId
    ? { ...message, readBy: [...message.readBy, actor.userId] }
    : message)
  return { ...state, messages }
}

export function updateDemoSupportCase(state: DemoState, caseId: string, status: DemoSupportCase['status'], actor: DemoProfile): DemoState {
  if (actor.role !== 'support' || !activeActor(state, actor)) return state
  const target = state.supportCases.find(item => item.id === caseId)
  if (!target || target.status === 'Resolved' || status === 'Open' || status === target.status) return state

  const updatedAt = new Date().toISOString()
  const supportCases = state.supportCases.map(item => item.id === caseId ? { ...item, status, updatedAt } : item)
  return recordDemoActivity(
    { ...state, supportCases },
    actor.name,
    'Support case updated',
    target.reference + ' · ' + status,
    { organization: target.organization },
  )
}

export function invoicesForFinanceProfile(state: DemoState, profile: DemoProfile): DemoInvoice[] {
  const financeUser = profile.role === 'finance-admin' ? activeActor(state, profile) : undefined
  if (!financeUser) return []
  const studentIds = new Set(state.users
    .filter(user => user.role === 'Student' && user.status === 'Active' && user.organization === financeUser.organization)
    .map(user => user.id))
  return state.invoices.filter(invoice => invoice.organization === financeUser.organization
    && studentIds.has(invoice.studentId))
}

export function setDemoUserStatus(state: DemoState, userId: string, status: DemoUser['status'], actor: DemoProfile): DemoState {
  if (actor.role !== 'admin' || !activeActor(state, actor)) return state
  const target = state.users.find(item => item.id === userId)
  if (!target || target.id === actor.userId || target.role === 'Platform Administrator' || target.status === status) return state
  const users = state.users.map(item => item.id === userId ? { ...item, status } : item)
  return recordDemoActivity({ ...state, users }, actor.name, 'User status updated', target.name + ': ' + status, {
    subjectUserId: target.id,
    organization: target.organization,
  })
}

export function submissionsForProfile(state: DemoState, profile: DemoProfile): DemoSubmission[] {
  const currentUser = state.users.find(user =>
    user.id === profile.userId
    && user.status === 'Active'
    && user.role === ROLE_RECORD_NAMES[profile.role])
  if (!currentUser) return []

  if (profile.role === 'student') {
    const enrolledCourseIds = new Set(currentUser.courseIds)
    return state.submissions.filter(item => item.studentId === profile.userId
      && enrolledCourseIds.has(item.courseId)
      && courseFor(state, item.courseId)?.status === 'Published')
  }
  if (profile.role === 'guardian' || profile.role === 'mentor') {
    const linkedLearner = linkedLearnerForProfile(state, profile)
    if (!linkedLearner) return []
    const enrolledCourseIds = new Set(linkedLearner.courseIds)
    if (profile.role === 'guardian') {
      return state.submissions.filter(item => item.studentId === linkedLearner.id
        && enrolledCourseIds.has(item.courseId)
        && courseFor(state, item.courseId)?.status === 'Published')
    }
    const assignedCourseIds = new Set(currentUser.courseIds.filter(courseId => enrolledCourseIds.has(courseId)))
    return state.submissions.filter(item => item.studentId === linkedLearner.id
      && assignedCourseIds.has(item.courseId)
      && courseFor(state, item.courseId)?.status === 'Published')
  }
  if (['teacher', 'teaching-assistant', 'evaluator'].includes(profile.role)) {
    const assigned = new Set(currentUser.courseIds)
    const activeLearners = new Map<string, DemoUser>(state.users
      .filter(user => user.role === 'Student' && user.status === 'Active' && user.organization === currentUser.organization)
      .map(user => [user.id, user] as const))
    return state.submissions.filter(item => {
      const learner = activeLearners.get(item.studentId)
      return !item.examScheduleId
        && assigned.has(item.courseId)
        && courseFor(state, item.courseId)?.status === 'Published'
        && learner !== undefined
        && learner.courseIds.includes(item.courseId)
    })
  }
  if (profile.role === 'exam-controller') {
    const assignedExamSessions = new Map<string, DemoScheduleItem>(state.schedule
      .filter(item => item.kind === 'Exam' && item.participantIds.includes(profile.userId))
      .map(item => [item.id, item] as const))
    return state.submissions.filter(item => {
      const session = item.examScheduleId ? assignedExamSessions.get(item.examScheduleId) : undefined
      const learner = state.users.find(user => user.id === item.studentId && user.role === 'Student' && user.status === 'Active')
      return session !== undefined
        && session.courseId === item.courseId
        && learner !== undefined
        && learner.organization === currentUser.organization
        && learner.courseIds.includes(item.courseId)
        && courseFor(state, item.courseId)?.status === 'Published'
    })
  }
  if (profile.role === 'client') {
    const learners = new Map<string, DemoUser>(state.users
      .filter(user => user.role === 'Student' && user.status === 'Active' && user.organization === currentUser.organization)
      .map(user => [user.id, user] as const))
    return state.submissions.filter(item => learners.get(item.studentId)?.courseIds.includes(item.courseId) === true
      && courseFor(state, item.courseId)?.status === 'Published')
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
    const productIds = new Set(state.products.map(item => item.id))
    const legacyProductActivityMatches = (item: DemoActivity): boolean => {
      const title = item.detail.trim().toLocaleLowerCase()
      const candidates = state.products.filter(product => {
        const productTitle = product.title.trim().toLocaleLowerCase()
        return productTitle === title || title === productTitle + ' draft'
      })
      return candidates.length === 1 && candidates[0] !== undefined
    }
    return state.activity.filter(item => item.action.toLocaleLowerCase().includes('product')
      && (item.productId !== undefined
        ? productIds.has(item.productId)
        : legacyProductActivityMatches(item)))
  }

  const submissions = submissionsForProfile(state, profile)
  const visibleAttendanceIds = new Set(attendanceForProfile(state, profile).map(item => item.id))
  let scopedCourseIds = new Set(actor.courseIds)

  if (profile.role === 'guardian' || profile.role === 'mentor') {
    const linked = linkedLearnerForProfile(state, profile)
    scopedCourseIds = new Set((linked?.courseIds ?? []).filter(courseId =>
      profile.role === 'guardian' || actor.courseIds.includes(courseId)))
  } else if (profile.role === 'client') {
    scopedCourseIds = new Set(learnersForProfile(state, profile).flatMap(learner => learner.courseIds))
  } else if (profile.role === 'exam-controller') {
    scopedCourseIds = new Set(state.schedule
      .filter(item => item.kind === 'Exam' && item.participantIds.includes(profile.userId))
      .map(item => item.courseId))
  }

  const clientCourseIds = new Set(profile.role === 'client'
    ? learnersForProfile(state, profile).flatMap(learner => learner.courseIds)
    : [])
  const assignedExamSessions = state.schedule.filter(item => item.kind === 'Exam'
    && item.participantIds.includes(profile.userId))

  const legacySubmissionMatches = (item: DemoActivity): boolean => {
    const action = item.action.toLocaleLowerCase()
    if (action.includes('assignment submitted')) {
      const matchingLearners = state.users.filter(user => user.role === 'Student'
        && user.status === 'Active'
        && user.name.toLocaleLowerCase() === item.actor.toLocaleLowerCase())
      if (matchingLearners.length !== 1) return false
      const learner = matchingLearners[0]
      if (!learner) return false
      const title = item.detail.trim().toLocaleLowerCase()
      const candidates = state.submissions.filter(submission => submission.studentId === learner.id
        && submission.title.toLocaleLowerCase() === title
        && submission.status !== 'Assigned')
      return candidates.length === 1
        && submissions.some(visible => visible.id === candidates[0]?.id)
    }
    if (action.includes('grade published')) {
      const match = item.detail.match(/^(.+?)\s+·\s+(\d+(?:\.\d+)?)\/100(?:\s+for\s+(.+))?$/i)
      const rawTitle = match?.[1]
      const rawScore = match?.[2]
      const rawLearnerName = match?.[3]
      if (!rawTitle || !rawScore) return false
      const title = rawTitle.trim().toLocaleLowerCase()
      const score = Number(rawScore)
      let candidates = state.submissions.filter(submission => submission.status === 'Graded'
        && submission.title.toLocaleLowerCase() === title
        && submission.score === score)
      if (rawLearnerName) {
        const learnerName = rawLearnerName.trim().toLocaleLowerCase()
        candidates = candidates.filter(submission => state.users.some(user =>
          user.id === submission.studentId && user.name.toLocaleLowerCase() === learnerName))
      }
      const [candidate] = candidates
      return candidates.length === 1 && candidate !== undefined
        && submissions.some(visible => visible.id === candidate.id)
    }
    return false
  }

  const legacyAttendanceMatches = (item: DemoActivity): boolean => {
    const match = item.detail.match(/^(.+?)\s+·\s+(.+?)\s+·\s+(Present|Late|Absent)\s+\((\d{4}-\d{2}-\d{2})\)$/i)
    const rawLearnerName = match?.[1]
    const rawCourseTitle = match?.[2]
    const rawStatus = match?.[3]
    const rawDate = match?.[4]
    if (!rawLearnerName || !rawCourseTitle || !rawStatus || !rawDate) return false
    const learnerName = rawLearnerName.trim().toLocaleLowerCase()
    const courseTitle = rawCourseTitle.trim().toLocaleLowerCase()
    const status = rawStatus.toLocaleLowerCase()
    const date = rawDate
    const candidates = state.attendance.filter(record => record.date === date
      && record.status.toLocaleLowerCase() === status
      && state.users.some(user => user.id === record.studentId
        && user.role === 'Student'
        && user.name.toLocaleLowerCase() === learnerName)
      && courseFor(state, record.courseId)?.title.toLocaleLowerCase() === courseTitle)
    return candidates.length === 1 && candidates[0] !== undefined
      && visibleAttendanceIds.has(candidates[0].id)
  }

  const legacyProgressMatches = (item: DemoActivity): boolean => {
    const action = item.action.toLocaleLowerCase()
    if (!action.includes('learning progress')) return false
    const match = item.detail.match(/^(.+?)\s+·\s+(\d+(?:\.\d+)?)%\s+complete$/i)
    const rawCourseTitle = match?.[1]
    const rawProgress = match?.[2]
    if (!rawCourseTitle || !rawProgress) return false
    const courseTitle = rawCourseTitle.trim().toLocaleLowerCase()
    const matchingLearners = state.users.filter(user => user.role === 'Student'
      && user.status === 'Active'
      && user.name.toLocaleLowerCase() === item.actor.toLocaleLowerCase())
    if (matchingLearners.length !== 1) return false
    const learner = matchingLearners[0]
    if (!learner) return false
    const matchingCourses = state.courses.filter(course => course.title.toLocaleLowerCase() === courseTitle
      && scopedCourseIds.has(course.id)
      && learner.courseIds.includes(course.id))
    if (matchingCourses.length !== 1) return false
    const course = matchingCourses[0]
    if (!course) return false
    if (profile.role === 'student') return learner.id === profile.userId
    if (profile.role === 'guardian' || profile.role === 'mentor') {
      const linked = linkedLearnerForProfile(state, profile)
      return learner.id === linked?.id && learner.organization === actor.organization
    }
    if (profile.role === 'client') return learner.organization === actor.organization
    return false
  }

  return state.activity.filter(item => {
    const action = item.action.toLocaleLowerCase()
    if (action.includes('product')) return false

    // New events carry stable record IDs. Never infer ownership from a shared display title.
    if (item.submissionId !== undefined) return submissions.some(submission => submission.id === item.submissionId)
    if (item.subjectUserId !== undefined) {
      if (!action.includes('attendance') && !action.includes('learning progress')) return false
      const subject = state.users.find(user => user.id === item.subjectUserId
        && user.role === 'Student' && user.status === 'Active')
      const courseId = item.courseId
      if (!subject || !courseId || !subject.courseIds.includes(courseId) || !scopedCourseIds.has(courseId)) return false
      if (profile.role === 'student') return subject.id === profile.userId
      if (profile.role === 'guardian') return subject.id === 'student-asha' && subject.organization === actor.organization
      if (profile.role === 'mentor') return subject.id === 'student-asha'
        && subject.organization === actor.organization && actor.courseIds.includes(courseId)
      if (profile.role === 'client') return subject.organization === actor.organization
      if (['teacher', 'teaching-assistant', 'evaluator'].includes(profile.role)) {
        return subject.organization === actor.organization && actor.courseIds.includes(courseId)
      }
      if (profile.role === 'exam-controller') {
        return subject.organization === actor.organization && assignedExamSessions.some(session => session.courseId === courseId)
      }
      return false
    }

    if (profile.role === 'client' && action.includes('organization action resolved')) {
      if (item.organization !== undefined) return item.organization === actor.organization
      const eventTitle = item.detail.trim().toLocaleLowerCase()
      const candidates = state.organizationActions.filter(candidate =>
        candidate.title.trim().toLocaleLowerCase() === eventTitle)
      const [candidate] = candidates
      return candidates.length === 1 && candidate !== undefined && candidate.organization === actor.organization
    }

    if (profile.role === 'client' && action.includes('course published')) {
      if (item.courseId !== undefined) {
        return clientCourseIds.has(item.courseId)
          && state.courses.some(course => course.id === item.courseId && course.status === 'Published')
      }
      const eventTitle = item.detail.trim().toLocaleLowerCase()
      const candidates = state.courses.filter(course => course.status === 'Published'
        && course.title.trim().toLocaleLowerCase() === eventTitle)
      const [candidate] = candidates
      return candidates.length === 1 && candidate !== undefined && clientCourseIds.has(candidate.id)
    }

    if (legacySubmissionMatches(item)) return true
    if (action.includes('attendance')) return legacyAttendanceMatches(item)
    if (legacyProgressMatches(item)) return true

    // Do not leak unattributed events when their relationship cannot be established.
    return false
  })
}

export function scheduleForProfile(state: DemoState, profile: DemoProfile): DemoScheduleItem[] {
  const user = activeActor(state, profile)
  if (!user) return []
  const publishedCourse = (courseId: string) => courseFor(state, courseId)?.status === 'Published'

  if (profile.role === 'admin') return state.schedule
  if (profile.role === 'exam-controller') {
    return state.schedule.filter(item => item.kind === 'Exam'
      && item.participantIds.includes(user.id)
      && user.courseIds.includes(item.courseId)
      && publishedCourse(item.courseId))
  }
  if (profile.role === 'student') {
    return state.schedule.filter(item => item.participantIds.includes(user.id)
      && user.courseIds.includes(item.courseId)
      && publishedCourse(item.courseId))
  }
  if (profile.role === 'guardian' || profile.role === 'mentor') {
    const learner = linkedLearnerForProfile(state, profile)
    if (!learner) return []
    const linkedCourses = new Set(learner.courseIds)
    if (profile.role === 'guardian') {
      return state.schedule.filter(item => item.participantIds.includes(learner.id)
        && linkedCourses.has(item.courseId)
        && publishedCourse(item.courseId))
    }
    return state.schedule.filter(item => user.courseIds.includes(item.courseId)
      && linkedCourses.has(item.courseId)
      && publishedCourse(item.courseId))
  }
  if (profile.role === 'teacher') {
    return state.schedule.filter(item => item.participantIds.includes(user.id)
      && user.courseIds.includes(item.courseId)
      && publishedCourse(item.courseId))
  }
  if (profile.role === 'client') {
    const learners = state.users.filter(item => item.role === 'Student'
      && item.status === 'Active' && item.organization === user.organization)
    const learnersById = new Map(learners.map(item => [item.id, item] as const))
    return state.schedule.filter(item => publishedCourse(item.courseId)
      && item.participantIds.some(id => learnersById.get(id)?.courseIds.includes(item.courseId) === true))
  }
  if (profile.role === 'teaching-assistant' || profile.role === 'evaluator') {
    return state.schedule.filter(item => item.kind !== 'Exam'
      && item.participantIds.includes(user.id)
      && user.courseIds.includes(item.courseId)
      && publishedCourse(item.courseId))
  }
  return []
}

export function attendanceForProfile(state: DemoState, profile: DemoProfile): DemoAttendance[] {
  const user = activeActor(state, profile)
  if (!user) return []
  if (profile.role === 'admin') return state.attendance

  const enrolledAttendance = (learner: DemoUser, courseIds: Set<string>) =>
    state.attendance.filter(item => item.studentId === learner.id
      && courseIds.has(item.courseId)
      && learner.courseIds.includes(item.courseId)
      && courseFor(state, item.courseId)?.status === 'Published')

  if (profile.role === 'student') return enrolledAttendance(user, new Set(user.courseIds))
  if (profile.role === 'guardian' || profile.role === 'mentor') {
    const learner = linkedLearnerForProfile(state, profile)
    if (!learner) return []
    const allowedCourses = profile.role === 'guardian'
      ? new Set(learner.courseIds)
      : new Set(user.courseIds.filter(courseId => learner.courseIds.includes(courseId)))
    return enrolledAttendance(learner, allowedCourses)
  }
  if (profile.role === 'teacher' || profile.role === 'teaching-assistant') {
    const activeLearners = state.users.filter(item => item.role === 'Student'
      && item.status === 'Active' && item.organization === user.organization)
    const assignedCourses = new Set(user.courseIds)
    return state.attendance.filter(item => assignedCourses.has(item.courseId)
      && courseFor(state, item.courseId)?.status === 'Published'
      && activeLearners.some(learner => learner.id === item.studentId
        && learner.courseIds.includes(item.courseId)))
  }
  if (profile.role === 'client') {
    const activeLearners = state.users.filter(item => item.role === 'Student'
      && item.status === 'Active' && item.organization === user.organization)
    return state.attendance.filter(item => courseFor(state, item.courseId)?.status === 'Published'
      && activeLearners.some(learner => learner.id === item.studentId
        && learner.courseIds.includes(item.courseId)))
  }
  return []
}

export function courseFor(state: DemoState, courseId: string): DemoCourse | undefined { return state.courses.find(course => course.id === courseId) }
export function progressFor(state: DemoState, userId: string, courseId: string): number { return state.courseProgress[userId + ':' + courseId] ?? 0 }
export function percentage(numerator: number, denominator: number): number { return denominator > 0 ? Math.round((numerator / denominator) * 100) : 0 }
