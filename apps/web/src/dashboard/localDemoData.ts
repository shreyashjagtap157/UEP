export type LocalRole = 'student' | 'teacher' | 'client' | 'developer' | 'administrator' | 'guardian'

export type DemoCourse = {
  id: string
  title: string
  code: string
  organization: string
  progress: number
  status: 'In progress' | 'Completed' | 'Not started'
  lessonsCompleted: number
  lessonsTotal: number
}

export type DemoAssignment = {
  id: string
  title: string
  course: string
  due: string
  studentNames: string[]
  status: 'Pending' | 'Submitted' | 'Graded'
  score?: number
}

export type DemoSubmission = {
  id: string
  student: string
  assignment: string
  submitted: string
  status: 'Needs grading' | 'Graded'
  score?: number
}

export type DemoSession = {
  id: string
  title: string
  course: string
  startsAt: string
  teacher: string
  attendance: number
}

export type DemoLearner = {
  id: string
  name: string
  cohort: string
  organization: string
  courseIds: string[]
  guardianName: string
  completion: number
  attendance: number
  status: 'Active' | 'At risk' | 'Completed'
}

export type DemoProduct = {
  id: string
  name: string
  version: string
  status: 'Draft' | 'In review' | 'Published'
  modules: number
  updated: string
}

export const localDemoData = {
  students: [
    { id: 'stu-001', name: 'Aarav Mehta', cohort: 'Full Stack · 2026', organization: 'Pune Technology Academy', courseIds: ['course-001', 'course-003'], guardianName: 'Meera Mehta', completion: 72, attendance: 94, status: 'Active' as const },
    { id: 'stu-002', name: 'Ananya Shah', cohort: 'Full Stack · 2026', organization: 'Pune Technology Academy', courseIds: ['course-001'], guardianName: 'Ananya Shah guardian', completion: 88, attendance: 97, status: 'Active' as const },
    { id: 'stu-003', name: 'Rohan Patil', cohort: 'Data Engineering · 2026', organization: 'Maharashtra Digital Skills', courseIds: ['course-002'], guardianName: 'Rohan Patil guardian', completion: 54, attendance: 82, status: 'At risk' as const },
    { id: 'stu-004', name: 'Isha Kulkarni', cohort: 'Full Stack · 2026', organization: 'Pune Technology Academy', courseIds: ['course-001', 'course-003'], guardianName: 'Isha Kulkarni guardian', completion: 100, attendance: 96, status: 'Completed' as const },
    { id: 'stu-005', name: 'Vedant Joshi', cohort: 'Cloud Engineering · 2026', organization: 'Maharashtra Digital Skills', courseIds: ['course-002'], guardianName: 'Vedant Joshi guardian', completion: 67, attendance: 91, status: 'Active' as const },
    { id: 'stu-006', name: 'Meera Deshmukh', cohort: 'Data Engineering · 2026', organization: 'Maharashtra Digital Skills', courseIds: ['course-002'], guardianName: 'Meera Deshmukh guardian', completion: 81, attendance: 95, status: 'Active' as const },
  ] satisfies DemoLearner[],
  courses: [
    { id: 'course-001', title: 'Web Application Architecture', code: 'WEB-301', organization: 'Pune Technology Academy', progress: 72, status: 'In progress', lessonsCompleted: 18, lessonsTotal: 25 },
    { id: 'course-002', title: 'Database Engineering', code: 'DB-204', organization: 'Maharashtra Digital Skills', progress: 61, status: 'In progress', lessonsCompleted: 11, lessonsTotal: 18 },
    { id: 'course-003', title: 'Cloud Deployment', code: 'CLOUD-210', organization: 'Pune Technology Academy', progress: 100, status: 'Completed', lessonsCompleted: 16, lessonsTotal: 16 },
  ] satisfies DemoCourse[],
  assignments: [
    { id: 'assignment-001', title: 'REST API architecture review', course: 'Web Application Architecture', due: '2026-10-24', studentNames: ['Aarav Mehta', 'Ananya Shah'], status: 'Pending' },
    { id: 'assignment-002', title: 'Database normalization exercise', course: 'Database Engineering', due: '2026-10-27', studentNames: ['Rohan Patil', 'Meera Deshmukh'], status: 'Submitted' },
    { id: 'assignment-003', title: 'Cloud deployment report', course: 'Cloud Deployment', due: '2026-10-18', studentNames: ['Isha Kulkarni'], status: 'Graded', score: 95 },
  ] satisfies DemoAssignment[],
  submissions: [
    { id: 'submission-001', student: 'Aarav Mehta', assignment: 'REST API architecture review', submitted: 'Today · 08:42', status: 'Needs grading' },
    { id: 'submission-002', student: 'Ananya Shah', assignment: 'REST API architecture review', submitted: 'Yesterday · 17:18', status: 'Needs grading' },
    { id: 'submission-003', student: 'Rohan Patil', assignment: 'Database normalization exercise', submitted: 'Yesterday · 15:03', status: 'Graded', score: 78 },
    { id: 'submission-004', student: 'Isha Kulkarni', assignment: 'Cloud deployment report', submitted: 'Oct 18 · 11:21', status: 'Graded', score: 95 },
  ] satisfies DemoSubmission[],
  sessions: [
    { id: 'session-001', title: 'Advanced API Design', course: 'Web Application Architecture', startsAt: 'Today · 10:30', teacher: 'Priya Nair', attendance: 24 },
    { id: 'session-002', title: 'SQL Query Workshop', course: 'Database Engineering', startsAt: 'Today · 14:00', teacher: 'Rahul Kulkarni', attendance: 21 },
    { id: 'session-003', title: 'Cloud Architecture Review', course: 'Cloud Deployment', startsAt: 'Tomorrow · 11:00', teacher: 'Priya Nair', attendance: 19 },
  ] satisfies DemoSession[],
  products: [
    { id: 'product-001', name: 'Full Stack Developer Pathway', version: '2.4.0', status: 'Published', modules: 12, updated: 'Today · 09:15' },
    { id: 'product-002', name: 'Data Engineering Pathway', version: '1.8.2', status: 'In review', modules: 10, updated: 'Yesterday · 16:40' },
    { id: 'product-003', name: 'Cloud Engineering Pathway', version: '0.9.0', status: 'Draft', modules: 8, updated: 'Oct 21 · 13:05' },
  ] satisfies DemoProduct[],
  releases: [
    { id: 'release-001', name: 'Platform Web', version: '0.15.0', status: 'Ready for review', checks: 24, passed: 24 },
    { id: 'release-002', name: 'Platform API', version: '0.15.0', status: 'Build blocked', checks: 18, passed: 14 },
  ],
  organizations: [
    { id: 'org-001', name: 'Pune Technology Academy', actions: 7 },
    { id: 'org-002', name: 'Maharashtra Digital Skills', actions: 4 },
  ],
} as const

export const demoRoleLabels: Record<LocalRole, string> = {
  student: 'Student',
  teacher: 'Teacher',
  client: 'Client / Institution',
  developer: 'Product Developer',
  administrator: 'Platform Administrator',
  guardian: 'Guardian',
}

export function organizationsForRole(role: LocalRole, organization?: string) {
  if (role !== 'client') return localDemoData.organizations
  return organization ? localDemoData.organizations.filter(item => item.name === organization) : []
}

export function learnersForOrganization(organization: string) {
  return localDemoData.students.filter(learner => learner.organization === organization)
}

export function coursesForOrganization(organization: string) {
  return localDemoData.courses.filter(course => course.organization === organization)
}

export function organizationMetrics(organization: string) {
  const learners = learnersForOrganization(organization)
  const courses = coursesForOrganization(organization)
  return {
    learners: learners.length,
    activeLearners: learners.filter(learner => learner.status !== 'Completed').length,
    courses: courses.length,
    completion: learners.length ? Math.round(learners.reduce((sum, learner) => sum + learner.completion, 0) / learners.length) : 0,
    attendance: learners.length ? Math.round(learners.reduce((sum, learner) => sum + learner.attendance, 0) / learners.length) : 0,
    atRiskLearners: learners.filter(learner => learner.status === 'At risk').length,
  }
}

export function studentStats(studentName = 'Aarav Mehta') {
  const learner = localDemoData.students.find(item => item.name === studentName)
  const enrolledIds = new Set<string>(learner?.courseIds ?? [])
  const courses = localDemoData.courses.filter(course => enrolledIds.has(course.id))
  const assignments = localDemoData.assignments.filter(item => item.studentNames.includes(studentName))
  const courseTitles = new Set(courses.map(course => course.title))
  const sessions = localDemoData.sessions.filter(session => courseTitles.has(session.course))
  const submissions = localDemoData.submissions.filter(item => item.student === studentName)
  return {
    averageProgress: courses.length ? Math.round(courses.reduce((sum, course) => sum + course.progress, 0) / courses.length) : 0,
    activeCourses: courses.filter(course => course.status === 'In progress').length,
    pendingAssignments: assignments.filter(item => !submissions.some(submission => submission.assignment === item.title)).length,
    submittedAssignments: submissions.filter(item => item.status === 'Needs grading').length,
    completedCourses: courses.filter(course => course.status === 'Completed').length,
    nextSession: sessions[0],
  }
}

export function teacherStats(teacherName = 'Priya Nair') {
  const courseTitles = new Set(localDemoData.sessions.filter(session => session.teacher === teacherName).map(session => session.course))
  const courseIds = new Set<string>(localDemoData.courses.filter(course => courseTitles.has(course.title)).map(course => course.id))
  const learners = localDemoData.students.filter(learner => learner.courseIds.some(courseId => courseIds.has(courseId)))
  const assignments = localDemoData.assignments.filter(item => courseTitles.has(item.course))
  const pendingGrading = localDemoData.submissions.filter(item => item.status === 'Needs grading' && assignments.some(assignment => assignment.title === item.assignment))
  return {
    learners: learners.length,
    activeLearners: learners.filter(item => item.status !== 'Completed').length,
    atRiskLearners: learners.filter(item => item.status === 'At risk').length,
    pendingGrading: pendingGrading.length,
    averageAttendance: learners.length ? Math.round(learners.reduce((sum, item) => sum + item.attendance, 0) / learners.length) : 0,
    averageCompletion: learners.length ? Math.round(learners.reduce((sum, item) => sum + item.completion, 0) / learners.length) : 0,
  }
}

export function clientStats() {
  const organizations = localDemoData.organizations
  const learners = localDemoData.students
  return {
    organizations: organizations.length,
    learners: learners.length,
    courses: localDemoData.courses.length,
    completion: learners.length ? Math.round(learners.reduce((sum, learner) => sum + learner.completion, 0) / learners.length) : 0,
    openActions: organizations.reduce((sum, organization) => sum + organization.actions, 0),
  }
}

export function developerStats() {
  const releases = localDemoData.releases
  const checks = releases.reduce((sum, release) => sum + release.checks, 0)
  const passed = releases.reduce((sum, release) => sum + release.passed, 0)
  return {
    releases: releases.length,
    checks,
    passed,
    failing: checks - passed,
    healthyChecksPercent: Math.round((passed / checks) * 100),
  }
}

export function administratorStats() {
  const organizations = localDemoData.organizations
  return {
    organizations: organizations.length,
    learners: localDemoData.students.length,
    courses: localDemoData.courses.length,
    openActions: organizations.reduce((sum, organization) => sum + organization.actions, 0),
    pendingApprovals: localDemoData.products.filter(product => product.status === 'In review').length,
  }
}
