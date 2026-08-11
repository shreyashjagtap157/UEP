import { accessToken } from '../auth/keycloak'

export interface PlatformVersion {
  version: string
  releaseStatus: string
}

export type PermissionKey =
  | 'ORGANIZATION_VIEW'
  | 'ORGANIZATION_MANAGE'
  | 'BRANCHES_VIEW'
  | 'BRANCHES_MANAGE'
  | 'USERS_VIEW'
  | 'USERS_MANAGE'
  | 'ROLES_VIEW'
  | 'ROLES_MANAGE'
  | 'ROLES_ASSIGN'
  | 'SESSIONS_VIEW'
  | 'SESSIONS_MANAGE'
  | 'ACADEMICS_VIEW'
  | 'ACADEMICS_MANAGE'
  | 'CURRICULUM_VIEW'
  | 'CURRICULUM_MANAGE'
  | 'ENROLLMENTS_VIEW'
  | 'ENROLLMENTS_MANAGE'
  | 'TEACHING_ASSIGNMENTS_VIEW'
  | 'TEACHING_ASSIGNMENTS_MANAGE'
  | 'AUDIT_VIEW'

export interface AuthenticationAssurance {
  acr: string
  methods: string[]
  otpEvidence: boolean
  webAuthnEvidence: boolean
  multiFactorEvidence: boolean
}

export interface CurrentIdentity {
  userId: string
  membershipId: string
  oidcSubject: string
  email?: string
  displayName: string
  tenantId: string
  membershipStatus: 'ACTIVE' | 'SUSPENDED' | 'ENDED'
  primaryBranchId?: string
  roles: string[]
  permissions: PermissionKey[]
  authenticationAssurance: AuthenticationAssurance
}

export interface PageResult<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface RoleReference {
  id: string
  systemKey?: string
  name: string
  systemManaged: boolean
}

export interface MemberView {
  membershipId: string
  userId: string
  oidcSubject: string
  email?: string
  displayName: string
  userStatus: 'ACTIVE' | 'DISABLED'
  membershipStatus: 'ACTIVE' | 'SUSPENDED' | 'ENDED'
  primaryBranchId?: string
  externalReference?: string
  joinedAt: string
  roles: RoleReference[]
  version: number
}

export interface RoleView {
  id: string
  systemKey?: string
  name: string
  description?: string
  systemManaged: boolean
  permissions: PermissionKey[]
  version: number
}

export interface BranchView {
  id: string
  code: string
  displayName: string
  timezone: string
  status: 'ACTIVE' | 'INACTIVE' | 'CLOSED'
  createdAt: string
  version: number
}

export interface OrganizationSettings {
  tenantId: string
  defaultTimezone: string
  defaultLocale: string
  weekStartsOn: number
  supportEmail?: string
  supportUrl?: string
  version: number
}

export interface SessionView {
  id: string
  startedAt: string
  lastSeenAt: string
  expiresAt?: string
  revokedAt?: string
  revocationReason?: string
  current: boolean
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = await accessToken()
  const headers = new Headers(init.headers)
  headers.set('Accept', 'application/json')
  headers.set('Authorization', `Bearer ${token}`)
  if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
  const response = await fetch(path, { ...init, headers })
  if (response.status === 204) return undefined as T
  const payload = await response.json().catch(() => undefined) as { message?: string } | T | undefined
  if (!response.ok) {
    const message = payload && typeof payload === 'object' && 'message' in payload ? payload.message : undefined
    throw new Error(message ?? `Platform request failed: ${response.status}`)
  }
  return payload as T
}

export async function fetchPlatformVersion(signal?: AbortSignal): Promise<PlatformVersion> {
  const init: RequestInit = { headers: { Accept: 'application/json' } }
  if (signal) init.signal = signal
  const response = await fetch('/api/v1/platform/version', init)
  if (!response.ok) throw new Error(`Platform version request failed: ${response.status}`)
  return response.json() as Promise<PlatformVersion>
}

export const fetchCurrentIdentity = () => request<CurrentIdentity>('/api/v1/me')
export const fetchMembers = () => request<PageResult<MemberView>>('/api/v1/identity/memberships?size=100')
export const fetchRoles = () => request<PageResult<RoleView>>('/api/v1/identity/roles?size=100')
export const fetchBranches = () => request<PageResult<BranchView>>('/api/v1/organization/branches?size=100')
export const fetchOrganizationSettings = () => request<OrganizationSettings>('/api/v1/organization/settings')
export const fetchMySessions = () => request<PageResult<SessionView>>('/api/v1/identity/sessions/me?size=100')

export function provisionMembership(input: {
  subject: string
  email?: string
  displayName: string
  roleIds: string[]
}): Promise<MemberView> {
  return request('/api/v1/identity/memberships', { method: 'POST', body: JSON.stringify(input) })
}

export function createRole(input: {
  name: string
  description?: string
  permissions: PermissionKey[]
}): Promise<RoleView> {
  return request('/api/v1/identity/roles', { method: 'POST', body: JSON.stringify({ ...input, expectedVersion: 0 }) })
}

export function createBranch(input: { code: string; displayName: string; timezone: string }): Promise<BranchView> {
  return request('/api/v1/organization/branches', { method: 'POST', body: JSON.stringify(input) })
}

export function updateOrganizationSettings(input: OrganizationSettings): Promise<OrganizationSettings> {
  return request('/api/v1/organization/settings', {
    method: 'PUT',
    body: JSON.stringify({
      defaultTimezone: input.defaultTimezone,
      defaultLocale: input.defaultLocale,
      weekStartsOn: input.weekStartsOn,
      supportEmail: input.supportEmail,
      supportUrl: input.supportUrl,
      expectedVersion: input.version,
    }),
  })
}

export function revokeOwnSession(sessionId: string): Promise<void> {
  return request(`/api/v1/identity/sessions/me/${sessionId}`, { method: 'DELETE' })
}

export interface AcademicPeriodView {
  id: string
  code: string
  displayName: string
  startsOn: string
  endsOn: string
  status: 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'ARCHIVED'
  createdAt: string
  version: number
}

export interface ProgramView {
  id: string
  academicPeriodId?: string
  code: string
  displayName: string
  description?: string
  status: 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'
  createdAt: string
  version: number
}

export interface CourseView {
  id: string
  programId?: string
  code: string
  displayName: string
  description?: string
  status: 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'
  createdAt: string
  version: number
}

export interface SubjectView {
  id: string
  courseId?: string
  code: string
  displayName: string
  description?: string
  status: 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'
  createdAt: string
  version: number
}

export interface CurriculumModuleView {
  id: string
  programId?: string
  courseId?: string
  subjectId?: string
  code: string
  displayName: string
  description?: string
  sequenceNumber?: number
  status: 'ACTIVE' | 'INACTIVE' | 'ARCHIVED'
  createdAt: string
  version: number
}

export interface BatchView {
  id: string
  academicPeriodId?: string
  programId?: string
  courseId?: string
  branchId?: string
  code: string
  displayName: string
  sectionCode?: string
  startsOn?: string
  endsOn?: string
  capacity?: number
  status: 'PLANNED' | 'ACTIVE' | 'CLOSED' | 'ARCHIVED'
  createdAt: string
  version: number
}

export interface EnrollmentView {
  id: string
  membershipId: string
  displayName?: string
  programId?: string
  courseId?: string
  batchId?: string
  status: 'ENROLLED' | 'COMPLETED' | 'WITHDRAWN' | 'CANCELLED'
  enrolledAt: string
  endedAt?: string
  externalReference?: string
  version: number
}

export type TeacherAssignmentRole = 'LEAD_TEACHER' | 'TEACHER' | 'TEACHING_ASSISTANT' | 'MENTOR' | 'EVALUATOR'

export interface TeacherAssignmentView {
  id: string
  membershipId: string
  displayName?: string
  programId?: string
  courseId?: string
  subjectId?: string
  moduleId?: string
  batchId?: string
  assignmentRole: TeacherAssignmentRole
  startsOn?: string
  endsOn?: string
  status: 'ACTIVE' | 'ENDED'
  assignedAt: string
  version: number
}

export const fetchAcademicPeriods = () => request<PageResult<AcademicPeriodView>>('/api/v1/academic-periods?size=100')
export const fetchPrograms = () => request<PageResult<ProgramView>>('/api/v1/programs?size=100')
export const fetchCourses = () => request<PageResult<CourseView>>('/api/v1/courses?size=100')
export const fetchSubjects = () => request<PageResult<SubjectView>>('/api/v1/subjects?size=100')
export const fetchCurriculumModules = () => request<PageResult<CurriculumModuleView>>('/api/v1/modules?size=100')
export const fetchBatches = () => request<PageResult<BatchView>>('/api/v1/batches?size=100')
export const fetchEnrollments = () => request<PageResult<EnrollmentView>>('/api/v1/enrollments?size=100')
export const fetchMyEnrollments = () => request<PageResult<EnrollmentView>>('/api/v1/enrollments/me?size=100')
export const fetchTeacherAssignments = () => request<PageResult<TeacherAssignmentView>>('/api/v1/teacher-assignments?size=100')
export const fetchMyTeacherAssignments = () => request<PageResult<TeacherAssignmentView>>('/api/v1/teacher-assignments/me?size=100')

export function createAcademicPeriod(input: { code: string; displayName: string; startsOn: string; endsOn: string }): Promise<AcademicPeriodView> {
  return request('/api/v1/academic-periods', { method: 'POST', body: JSON.stringify(input) })
}

export function createProgram(input: { academicPeriodId?: string; code: string; displayName: string; description?: string }): Promise<ProgramView> {
  return request('/api/v1/programs', { method: 'POST', body: JSON.stringify(input) })
}

export function createCourse(input: { programId?: string; code: string; displayName: string; description?: string }): Promise<CourseView> {
  return request('/api/v1/courses', { method: 'POST', body: JSON.stringify(input) })
}

export function createSubject(input: { courseId?: string; code: string; displayName: string; description?: string }): Promise<SubjectView> {
  return request('/api/v1/subjects', { method: 'POST', body: JSON.stringify(input) })
}

export function createCurriculumModule(input: {
  programId?: string
  courseId?: string
  subjectId?: string
  code: string
  displayName: string
  description?: string
  sequenceNumber?: number
}): Promise<CurriculumModuleView> {
  return request('/api/v1/modules', { method: 'POST', body: JSON.stringify(input) })
}

export function createBatch(input: {
  academicPeriodId?: string
  programId?: string
  courseId?: string
  branchId?: string
  code: string
  displayName: string
  sectionCode?: string
  startsOn?: string
  endsOn?: string
  capacity?: number
}): Promise<BatchView> {
  return request('/api/v1/batches', { method: 'POST', body: JSON.stringify(input) })
}

export function createEnrollment(input: {
  membershipId: string
  programId?: string
  courseId?: string
  batchId?: string
  externalReference?: string
}): Promise<EnrollmentView> {
  return request('/api/v1/enrollments', { method: 'POST', body: JSON.stringify(input) })
}

export function createTeacherAssignment(input: {
  membershipId: string
  programId?: string
  courseId?: string
  subjectId?: string
  moduleId?: string
  batchId?: string
  assignmentRole: TeacherAssignmentRole
  startsOn?: string
}): Promise<TeacherAssignmentView> {
  return request('/api/v1/teacher-assignments', { method: 'POST', body: JSON.stringify(input) })
}
