export type LocalRole = 'student' | 'teacher' | 'client' | 'developer' | 'administrator' | 'guardian'

export type DemoCourse = {
  id: string
  title: string
  code: string
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
    { id: 'stu-001', name: 'Aarav Mehta', cohort: 'Full Stack · 2026', completion: 72, attendance: 94, status: 'Active' as const },
    { id: 'stu-002', name: 'Ananya Shah', cohort: 'Full Stack · 2026', completion: 88, attendance: 97, status: 'Active' as const },
    { id: 'stu-003', name: 'Rohan Patil', cohort: 'Data Engineering · 2026', completion: 54, attendance: 82, status: 'At risk' as const },
    { id: 'stu-004', name: 'Isha Kulkarni', cohort: 'Full Stack · 2026', completion: 100, attendance: 96, status: 'Completed' as const },
    { id: 'stu-005', name: 'Vedant Joshi', cohort: 'Cloud Engineering · 2026', completion: 67, attendance: 91, status: 'Active' as const },
    { id: 'stu-006', name: 'Meera Deshmukh', cohort: 'Data Engineering · 2026', completion: 81, attendance: 95, status: 'Active' as const },
  ] satisfies DemoLearner[],
  courses: [
    { id: 'course-001', title: 'Web Application Architecture', code: 'WEB-301', progress: 72, status: 'In progress', lessonsCompleted: 18, lessonsTotal: 25 },
    { id: 'course-002', title: 'Database Engineering', code: 'DB-204', progress: 61, status: 'In progress', lessonsCompleted: 11, lessonsTotal: 18 },
    { id: 'course-003', title: 'Cloud Deployment', code: 'CLOUD-210', progress: 100, status: 'Completed', lessonsCompleted: 16, lessonsTotal: 16 },
  ] satisfies DemoCourse[],
  assignments: [
    { id: 'assignment-001', title: 'REST API architecture review', course: 'Web Application Architecture', due: '2026-10-24', status: 'Pending' },
    { id: 'assignment-002', title: 'Database normalization exercise', course: 'Database Engineering', due: '2026-10-27', status: 'Submitted' },
    { id: 'assignment-003', title: 'Cloud deployment report', course: 'Cloud Deployment', due: '2026-10-18', status: 'Graded', score: 91 },
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
    { id: 'org-001', name: 'Pune Technology Academy', learners: 248, courses: 12, completion: 82, actions: 7 },
    { id: 'org-002', name: 'Maharashtra Digital Skills', learners: 164, courses: 8, completion: 76, actions: 4 },
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

export function studentStats() {
  const courses = localDemoData.courses
  const assignments = localDemoData.assignments
  const sessions = localDemoData.sessions
  return {
    averageProgress: Math.round(courses.reduce((sum, course) => sum + course.progress, 0) / courses.length),
    activeCourses: courses.filter(course => course.status === 'In progress').length,
    pendingAssignments: assignments.filter(item => item.status === 'Pending').length,
    submittedAssignments: assignments.filter(item => item.status === 'Submitted').length,
    completedCourses: courses.filter(course => course.status === 'Completed').length,
    nextSession: sessions[0],
  }
}

export function teacherStats() {
  const learners = localDemoData.students
  const submissions = localDemoData.submissions
  return {
    learners: learners.length,
    activeLearners: learners.filter(item => item.status === 'Active').length,
    atRiskLearners: learners.filter(item => item.status === 'At risk').length,
    pendingGrading: submissions.filter(item => item.status === 'Needs grading').length,
    averageAttendance: Math.round(learners.reduce((sum, item) => sum + item.attendance, 0) / learners.length),
    averageCompletion: Math.round(learners.reduce((sum, item) => sum + item.completion, 0) / learners.length),
  }
}

export function clientStats() {
  const organizations = localDemoData.organizations
  const learners = organizations.reduce((sum, org) => sum + org.learners, 0)
  const actions = organizations.reduce((sum, org) => sum + org.actions, 0)
  const weightedCompletion = Math.round(organizations.reduce((sum, org) => sum + org.learners * org.completion, 0) / learners)
  return {
    organizations: organizations.length,
    learners,
    courses: organizations.reduce((sum, org) => sum + org.courses, 0),
    completion: weightedCompletion,
    openActions: actions,
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
  const learners = organizations.reduce((sum, org) => sum + org.learners, 0)
  return {
    organizations: organizations.length,
    learners,
    courses: organizations.reduce((sum, org) => sum + org.courses, 0),
    openActions: organizations.reduce((sum, org) => sum + org.actions, 0),
    pendingApprovals: localDemoData.products.filter(product => product.status === 'In review').length,
  }
}
