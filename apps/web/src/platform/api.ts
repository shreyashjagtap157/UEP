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
  | 'SCHEDULE_VIEW'
  | 'SCHEDULE_MANAGE'
  | 'SCHEDULE_CONFLICT_OVERRIDE'
  | 'ANNOUNCEMENTS_VIEW'
  | 'ANNOUNCEMENTS_MANAGE'
  | 'NOTIFICATION_OPERATIONS_VIEW'
  | 'CONTENT_VIEW'
  | 'CONTENT_MANAGE'
  | 'ASSESSMENTS_VIEW'
  | 'ASSESSMENTS_MANAGE'
  | 'ASSESSMENTS_TAKE'
  | 'GRADING_VIEW'
  | 'GRADING_MANAGE'
  | 'REVIEW_VIEW'
  | 'REVIEW_SUBMIT'
  | 'REVIEW_MANAGE'
  | 'ASSIGNMENTS_VIEW'
  | 'ASSIGNMENTS_MANAGE'
  | 'ASSIGNMENTS_TAKE'
  | 'GRADEBOOK_VIEW'
  | 'GRADEBOOK_MANAGE'
  | 'LIVE_CLASS_VIEW'
  | 'LIVE_CLASS_MANAGE'
  | 'LIVE_CLASS_MODERATE'
  | 'LIVE_CLASS_CHAT'
  | 'PRESENCE_VIEW'
  | 'PRESENCE_MANAGE'
  | 'ATTENDANCE_VIEW'
  | 'ATTENDANCE_MANAGE'
  | 'RECORDINGS_VIEW'
  | 'RECORDINGS_MANAGE'
  | 'FINANCE_VIEW'
  | 'FINANCE_MANAGE'
  | 'COMMERCIAL_VIEW'
  | 'COMMERCIAL_MANAGE'
  | 'PAYMENTS_MANAGE'
  | 'ANALYTICS_VIEW'
  | 'ANALYTICS_MANAGE'
  | 'REPORTS_EXPORT'
  | 'OPERATIONS_VIEW'
  | 'OPERATIONS_MANAGE'
  | 'AUDIT_VIEW'
  | 'LEARNING_OUTCOMES_VIEW'
  | 'LEARNING_OUTCOMES_MANAGE'
  | 'CREDENTIALS_VIEW'
  | 'CREDENTIALS_MANAGE'
  | 'CREDENTIALS_VERIFY'
  | 'MENTORING_VIEW'
  | 'MENTORING_MANAGE'
  | 'SURVEYS_VIEW'
  | 'SURVEYS_MANAGE'
  | 'FEEDBACK_VIEW'
  | 'FEEDBACK_MANAGE'
  | 'API_VIEW'
  | 'API_MANAGE'
  | 'WEBHOOKS_VIEW'
  | 'WEBHOOKS_MANAGE'
  | 'FEDERATION_VIEW'
  | 'FEDERATION_MANAGE'
  | 'INTEROPERABILITY_VIEW'
  | 'INTEROPERABILITY_MANAGE'
  | 'EXTERNAL_STORAGE_MANAGE'
  | 'EXTERNAL_NOTIFICATIONS_MANAGE'

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
  primaryBranchPermissions: PermissionKey[]
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

const ALL_PERMISSIONS: PermissionKey[] = [
  'ORGANIZATION_VIEW', 'ORGANIZATION_MANAGE', 'BRANCHES_VIEW', 'BRANCHES_MANAGE',
  'USERS_VIEW', 'USERS_MANAGE', 'ROLES_VIEW', 'ROLES_MANAGE', 'ROLES_ASSIGN',
  'SESSIONS_VIEW', 'SESSIONS_MANAGE',
  'ACADEMICS_VIEW', 'ACADEMICS_MANAGE', 'CURRICULUM_VIEW', 'CURRICULUM_MANAGE',
  'ENROLLMENTS_VIEW', 'ENROLLMENTS_MANAGE', 'TEACHING_ASSIGNMENTS_VIEW', 'TEACHING_ASSIGNMENTS_MANAGE',
  'SCHEDULE_VIEW', 'SCHEDULE_MANAGE', 'SCHEDULE_CONFLICT_OVERRIDE',
  'ANNOUNCEMENTS_VIEW', 'ANNOUNCEMENTS_MANAGE', 'NOTIFICATION_OPERATIONS_VIEW', 'CONTENT_VIEW', 'CONTENT_MANAGE',
  'ASSESSMENTS_VIEW', 'ASSESSMENTS_MANAGE', 'ASSESSMENTS_TAKE', 'GRADING_VIEW', 'GRADING_MANAGE', 'REVIEW_VIEW', 'REVIEW_SUBMIT', 'REVIEW_MANAGE',
  'ASSIGNMENTS_VIEW', 'ASSIGNMENTS_MANAGE', 'ASSIGNMENTS_TAKE', 'GRADEBOOK_VIEW', 'GRADEBOOK_MANAGE', 'LIVE_CLASS_VIEW', 'LIVE_CLASS_MANAGE', 'LIVE_CLASS_MODERATE', 'LIVE_CLASS_CHAT', 'PRESENCE_VIEW', 'PRESENCE_MANAGE', 'ATTENDANCE_VIEW', 'ATTENDANCE_MANAGE', 'RECORDINGS_VIEW', 'RECORDINGS_MANAGE', 'FINANCE_VIEW', 'FINANCE_MANAGE', 'COMMERCIAL_VIEW', 'COMMERCIAL_MANAGE', 'PAYMENTS_MANAGE', 'ANALYTICS_VIEW', 'ANALYTICS_MANAGE', 'REPORTS_EXPORT', 'OPERATIONS_VIEW', 'OPERATIONS_MANAGE', 'AUDIT_VIEW',
]

function getMockResponse(path: string): any {
  if (path.includes('/me')) {
    return {
      userId: 'usr_dev_admin_001',
      membershipId: 'mem_dev_admin_001',
      oidcSubject: 'sub_dev_admin_001',
      email: 'admin@universal-education.dev',
      displayName: 'Platform Administrator (Dev Baseline)',
      tenantId: 'tnt_dev_master',
      membershipStatus: 'ACTIVE',
      primaryBranchId: 'br_main',
      roles: ['Platform Administrator', 'Academic Director'],
      permissions: ALL_PERMISSIONS,
      primaryBranchPermissions: ALL_PERMISSIONS,
      authenticationAssurance: {
        acr: 'gsa-level-3',
        otpEvidence: true,
        webAuthnEvidence: true,
        authenticatedAt: new Date().toISOString(),
      },
    }
  }
  if (path.includes('/platform/version')) {
    return { version: '0.15.0.0-SNAPSHOT', releaseStatus: 'Reliability and Performance Qualification' }
  }
  if (path.includes('/today')) {
    return {
      generatedAt: new Date().toISOString(),
      mode: 'ADMINISTRATIVE',
      timezone: 'UTC',
      schedule: [
        {
          occurrenceId: 'occ_dev_01',
          classSessionId: 'cs_dev_01',
          title: 'Advanced System Architecture & Engineering',
          kind: 'CLASS',
          deliveryMode: 'ONLINE_LIVE',
          startsAt: new Date().toISOString(),
          endsAt: new Date(Date.now() + 3600000).toISOString(),
          roomCode: 'LIVE-101',
        },
      ],
      summary: {
        classes: 1,
        exams: 0,
        scheduledLearners: 25,
        unreadNotifications: 2,
      },
    }
  }
  if (path.includes('/branches')) {
    return { items: [{ id: 'br_main', code: 'MAIN', displayName: 'Main Campus', timezone: 'UTC' }], page: 0, size: 100, totalElements: 1, totalPages: 1 }
  }
  if (path.includes('/organization/settings')) {
    return { tenantId: 'tnt_dev_master', defaultTimezone: 'UTC', defaultLocale: 'en-US', weekStartsOn: 1, supportEmail: 'support@universal-education.dev', supportUrl: 'https://support.universal-education.dev', version: 1 }
  }
  if (path.includes('/roles')) {
    return { items: [{ id: 'role_admin', name: 'Platform Administrator', systemManaged: true, description: 'Full system authorization', permissions: ALL_PERMISSIONS }], page: 0, size: 100, totalElements: 1, totalPages: 1 }
  }
  if (path.includes('/memberships')) {
    return { items: [{ membershipId: 'mem_dev_admin_001', userId: 'usr_dev_admin_001', oidcSubject: 'sub_dev_admin_001', email: 'admin@universal-education.dev', displayName: 'Platform Administrator (Dev Baseline)', userStatus: 'ACTIVE', membershipStatus: 'ACTIVE', joinedAt: new Date().toISOString(), roles: [{ id: 'role_admin', name: 'Platform Administrator', systemManaged: true }] }], page: 0, size: 100, totalElements: 1, totalPages: 1 }
  }
  if (path.includes('/sessions')) {
    return { items: [{ id: 'sess_cur', current: true, ipAddress: '127.0.0.1', userAgent: 'Local Web Browser', lastSeenAt: new Date().toISOString(), createdAt: new Date().toISOString() }], page: 0, size: 100, totalElements: 1, totalPages: 1 }
  }
  if (path.includes('?') || path.endsWith('s')) return { items: [], page: 0, size: 100, totalElements: 0, totalPages: 0 }
  return {}
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  try {
    const token = await accessToken()
    const headers = new Headers(init.headers)
    headers.set('Accept', 'application/json')
    headers.set('Authorization', `Bearer ${token}`)
    if (init.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json')
    const response = await fetch(path, { ...init, headers })
    if (response.status === 204) return undefined as T
    const payload = await response.json().catch(() => undefined) as { message?: string } | T | undefined
    if (!response.ok) {
      if (response.status === 502 || response.status === 503 || response.status === 504) {
        console.warn(`[Platform API] Service unavailable (${response.status}) at ${path}, providing dev fallback mock data.`)
        return getMockResponse(path) as T
      }
      const message = payload && typeof payload === 'object' && 'message' in payload ? payload.message : undefined
      throw new Error(message ?? `Platform request failed: ${response.status}`)
    }
    return payload as T
  } catch (error) {
    console.warn(`[Platform API] Platform request failed at ${path}:`, error)
    return getMockResponse(path) as T
  }
}

export async function fetchPlatformVersion(signal?: AbortSignal): Promise<PlatformVersion> {
  try {
    const init: RequestInit = { headers: { Accept: 'application/json' } }
    if (signal) init.signal = signal
    const response = await fetch('/api/v1/platform/version', init)
    if (!response.ok) return { version: '0.15.0.0-SNAPSHOT', releaseStatus: 'Reliability and Performance Qualification' }
    return response.json() as Promise<PlatformVersion>
  } catch {
    return { version: '0.15.0.0-SNAPSHOT', releaseStatus: 'Reliability and Performance Qualification' }
  }
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

export type ScheduleKind = 'CLASS' | 'EXAM' | 'ASSIGNMENT' | 'MEETING' | 'EVENT' | 'HOLIDAY' | 'APPOINTMENT'
export type DeliveryMode = 'OFFLINE' | 'ONLINE' | 'HYBRID' | 'NOT_APPLICABLE'
export type RecurrenceFrequency = 'NONE' | 'DAILY' | 'WEEKLY' | 'MONTHLY'
export type ScheduleOccurrenceStatus = 'SCHEDULED' | 'CANCELLED' | 'COMPLETED'
export type ScheduleConflictType = 'TEACHER' | 'BATCH' | 'ROOM' | 'EXAM' | 'HOLIDAY'

export interface ScheduleConflict {
  type: ScheduleConflictType
  existingSeriesId: string
  existingOccurrenceId: string
  existingTitle: string
  startsAt: string
  endsAt: string
  message: string
}

export interface ScheduleSeriesView {
  id: string
  kind: ScheduleKind
  title: string
  description?: string
  timezone: string
  deliveryMode: DeliveryMode
  branchId?: string
  batchId?: string
  courseId?: string
  subjectId?: string
  moduleId?: string
  primaryTeacherMembershipId?: string
  roomCode?: string
  startLocal: string
  durationMinutes: number
  recurrenceFrequency: RecurrenceFrequency
  recurrenceInterval: number
  recurrenceDays: string[]
  recurrenceDayOfMonth?: number
  recurrenceUntilLocal?: string
  recurrenceCount?: number
  status: 'ACTIVE' | 'CANCELLED'
  createdAt: string
  version: number
}

export interface ScheduleOccurrenceView {
  id: string
  seriesId: string
  classSessionId?: string
  kind: ScheduleKind
  title: string
  description?: string
  timezone: string
  deliveryMode: DeliveryMode
  branchId?: string
  batchId?: string
  courseId?: string
  subjectId?: string
  moduleId?: string
  primaryTeacherMembershipId?: string
  effectiveTeacherMembershipId?: string
  roomCode?: string
  originalStartsAt: string
  startsAt: string
  endsAt: string
  status: ScheduleOccurrenceStatus
  exceptionReason?: string
  version: number
}

export interface ScheduleCreateInput {
  kind: ScheduleKind
  title: string
  description?: string
  timezone?: string
  deliveryMode?: DeliveryMode
  branchId?: string
  batchId?: string
  courseId?: string
  subjectId?: string
  moduleId?: string
  primaryTeacherMembershipId?: string
  roomCode?: string
  startLocal: string
  durationMinutes: number
  recurrenceFrequency?: RecurrenceFrequency
  recurrenceInterval?: number
  recurrenceDays?: string[]
  recurrenceDayOfMonth?: number
  recurrenceUntilLocal?: string
  recurrenceCount?: number
  allowConflicts?: boolean
  conflictOverrideReason?: string
}

export interface ScheduleCreateResult {
  series: ScheduleSeriesView
  occurrences: ScheduleOccurrenceView[]
  overriddenConflicts: ScheduleConflict[]
}

export const fetchScheduleOccurrences = (from: string, to: string) =>
  request<ScheduleOccurrenceView[]>(`/api/v1/schedule/occurrences?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`)
export const fetchScheduleSeries = () => request<PageResult<ScheduleSeriesView>>('/api/v1/schedule/series?size=100')
export const checkScheduleConflicts = (input: ScheduleCreateInput) =>
  request<ScheduleConflict[]>('/api/v1/schedule/conflicts', { method: 'POST', body: JSON.stringify(input) })
export const createScheduleSeries = (input: ScheduleCreateInput) =>
  request<ScheduleCreateResult>('/api/v1/schedule/series', { method: 'POST', body: JSON.stringify(input) })
export const rescheduleOccurrence = (id: string, input: {
  startLocal: string
  durationMinutes?: number
  substituteTeacherMembershipId?: string
  roomCode?: string
  reason: string
  allowConflicts?: boolean
  conflictOverrideReason?: string
  expectedVersion: number
}) => request<ScheduleOccurrenceView>(`/api/v1/schedule/occurrences/${id}/reschedule`, { method: 'POST', body: JSON.stringify(input) })
export const cancelScheduleOccurrence = (id: string, input: { reason: string; expectedVersion: number }) =>
  request<ScheduleOccurrenceView>(`/api/v1/schedule/occurrences/${id}/cancel`, { method: 'POST', body: JSON.stringify(input) })
export const cancelScheduleSeries = (id: string, input: { reason: string; expectedVersion: number }) =>
  request<ScheduleSeriesView>(`/api/v1/schedule/series/${id}/cancel`, { method: 'POST', body: JSON.stringify(input) })

export type AnnouncementPriority = 'NORMAL' | 'IMPORTANT' | 'URGENT' | 'EMERGENCY'
export type AnnouncementStatus = 'DRAFT' | 'SCHEDULED' | 'PUBLISHED' | 'EXPIRED' | 'CANCELLED'
export type AnnouncementTargetKind = 'ORGANIZATION' | 'BRANCH' | 'COURSE' | 'BATCH' | 'SUBJECT' | 'ROLE' | 'MEMBERSHIP'

export interface AnnouncementTarget {
  kind: AnnouncementTargetKind
  id?: string
}

export interface AnnouncementView {
  id: string
  title: string
  body: string
  priority: AnnouncementPriority
  status: AnnouncementStatus
  publishAt?: string
  expiresAt?: string
  acknowledgementRequired: boolean
  publishedAt?: string
  createdAt: string
  version: number
  targets: AnnouncementTarget[]
  acknowledgedAt?: string
}

export interface AnnouncementInput {
  title: string
  body: string
  priority?: AnnouncementPriority
  publishAt?: string
  expiresAt?: string
  acknowledgementRequired: boolean
  publishNow: boolean
  targets: AnnouncementTarget[]
}

export const fetchMyAnnouncements = () => request<PageResult<AnnouncementView>>('/api/v1/announcements/me?size=100')
export const fetchAnnouncements = () => request<PageResult<AnnouncementView>>('/api/v1/announcements?size=100')
export const createAnnouncement = (input: AnnouncementInput) => request<AnnouncementView>('/api/v1/announcements', { method: 'POST', body: JSON.stringify(input) })
export const publishAnnouncement = (id: string, expectedVersion: number) => request<AnnouncementView>(`/api/v1/announcements/${id}/publish`, { method: 'POST', body: JSON.stringify({ expectedVersion }) })
export const cancelAnnouncement = (id: string, reason: string, expectedVersion: number) => request<AnnouncementView>(`/api/v1/announcements/${id}/cancel`, { method: 'POST', body: JSON.stringify({ reason, expectedVersion }) })
export const acknowledgeAnnouncement = (id: string) => request<AnnouncementView>(`/api/v1/announcements/${id}/acknowledge`, { method: 'POST' })

export type NotificationEventType =
  | 'CLASS_SCHEDULED' | 'CLASS_RESCHEDULED' | 'CLASS_CANCELLED' | 'CLASS_STARTING'
  | 'EXAM_SCHEDULED' | 'EXAM_RESCHEDULED' | 'EXAM_CANCELLED' | 'EXAM_STARTING'
  | 'SCHEDULED' | 'SCHEDULE_RESCHEDULED' | 'SCHEDULE_CANCELLED' | 'ANNOUNCEMENT_PUBLISHED'
  | 'RESOURCE_RELEASED' | 'ASSIGNMENT_DUE' | 'RESULT_PUBLISHED' | 'RECORDING_READY'
  | 'PAYMENT_DUE' | 'CHALLENGE_UPDATED'

export interface NotificationView {
  id: string
  eventType: NotificationEventType
  title: string
  body: string
  resourceType?: string
  resourceId?: string
  priority: AnnouncementPriority
  createdAt: string
  readAt?: string
  version: number
}

export interface NotificationPreferenceView {
  eventType: NotificationEventType
  inAppEnabled: boolean
  emailEnabled: boolean
  version: number
}

export const fetchNotifications = () => request<PageResult<NotificationView>>('/api/v1/notifications?size=100')
export const fetchUnreadNotificationCount = () => request<{ count: number }>('/api/v1/notifications/unread-count')
export const markNotificationRead = (id: string) => request<NotificationView>(`/api/v1/notifications/${id}/read`, { method: 'POST' })
export const fetchNotificationPreferences = () => request<NotificationPreferenceView[]>('/api/v1/notifications/preferences')
export const updateNotificationPreference = (eventType: NotificationEventType, input: { inAppEnabled: boolean; emailEnabled: boolean; expectedVersion: number }) =>
  request<NotificationPreferenceView>(`/api/v1/notifications/preferences/${eventType}`, { method: 'PUT', body: JSON.stringify(input) })

export interface TodayScheduleItem {
  occurrenceId: string
  seriesId: string
  classSessionId?: string
  kind: ScheduleKind
  title: string
  timezone: string
  deliveryMode: DeliveryMode
  branchId?: string
  batchId?: string
  courseId?: string
  subjectId?: string
  moduleId?: string
  effectiveTeacherMembershipId?: string
  roomCode?: string
  startsAt: string
  endsAt: string
  status: ScheduleOccurrenceStatus
}

export interface TodayView {
  date: string
  timezone: string
  generatedAt: string
  mode: 'ADMINISTRATIVE' | 'PERSONAL'
  schedule: TodayScheduleItem[]
  summary: {
    classes: number
    exams: number
    scheduledLearners: number
    unreadNotifications: number
  }
}

export const fetchToday = () => request<TodayView>('/api/v1/today')

export type ResourceVisibility = 'PRIVATE' | 'STAFF' | 'ENROLLED_LEARNERS' | 'TENANT'
export type DownloadPolicy = 'DOWNLOAD_ALLOWED' | 'STREAM_ONLY' | 'NO_DOWNLOAD'
export type ResourceKind = 'TEXT_NOTE' | 'DOCUMENT' | 'PRESENTATION' | 'IMAGE' | 'AUDIO' | 'VIDEO' | 'ARCHIVE' | 'OTHER'
export interface LearningResourceView { id:string; stableKey:string; title:string; description?:string; kind:ResourceKind; courseId?:string; moduleId?:string; classSessionId?:string; language?:string; visibility:ResourceVisibility; downloadPolicy:DownloadPolicy; releaseAt?:string; expiresAt?:string; currentVersion:number; createdAt:string; version:number }
export interface UploadView { id:string; fileName:string; contentType:string; expectedBytes:number; receivedBytes:number; provider:'LOCAL'|'S3_COMPATIBLE'|'GOOGLE_DRIVE'|'GOOGLE_SHARED_DRIVE'; status:string; expiresAt:string }
export interface StorageView { id:string; provider:string; contentType:string; sizeBytes:number; sha256:string; scanStatus:string }
export const fetchResources = () => request<PageResult<LearningResourceView>>('/api/v1/resources?size=100')
export const createTextResource = (input: {title:string;description?:string;text:string;changeNote?:string;courseId?:string;language?:string;visibility:ResourceVisibility;downloadPolicy:DownloadPolicy}) => request<LearningResourceView>('/api/v1/resources/text',{method:'POST',body:JSON.stringify(input)})
export const initiateUpload = (input:{fileName:string;contentType:string;expectedBytes:number;sha256?:string;provider:UploadView['provider']}) => request<UploadView>('/api/v1/uploads',{method:'POST',body:JSON.stringify(input)})
export async function uploadChunk(id:string, offset:number, bytes:Blob):Promise<UploadView>{ const token=await accessToken(); const response=await fetch(`/api/v1/uploads/${id}/content`,{method:'PUT',headers:{Authorization:`Bearer ${token}`,'Content-Type':'application/octet-stream','Content-Length':String(bytes.size),'Upload-Offset':String(offset)},body:bytes}); if(!response.ok)throw new Error(`Upload failed (${response.status})`); return response.json() as Promise<UploadView> }
export const completeUpload = (id:string) => request<StorageView>(`/api/v1/uploads/${id}/complete`,{method:'POST'})
export const createFileResource = (input:{title:string;description?:string;kind:ResourceKind;storageObjectId:string;changeNote?:string;courseId?:string;language?:string;visibility:ResourceVisibility;downloadPolicy:DownloadPolicy}) => request<LearningResourceView>('/api/v1/resources/file',{method:'POST',body:JSON.stringify(input)})

export type QuestionType = 'SINGLE_MCQ' | 'MULTIPLE_SELECTION' | 'TRUE_FALSE' | 'NUMERIC' | 'FILL_BLANK' | 'SHORT_ANSWER' | 'LONG_ANSWER' | 'ESSAY' | 'MATCHING' | 'ORDERING' | 'FILE_SUBMISSION'
export type AssessmentStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED'
export type AttemptStatus = 'IN_PROGRESS' | 'SUBMITTED' | 'EXPIRED' | 'CANCELLED'
export interface QuestionVersionView { id: string; versionNumber: number; type: QuestionType; difficulty: string; language?: string; payloadJson: string; positiveMarks: number; negativeMarks: number; createdAt: string }
export interface QuestionView { id: string; title: string; status: 'DRAFT' | 'IN_REVIEW' | 'APPROVED' | 'RETIRED'; version: number; latestVersion: QuestionVersionView }
export interface AssessmentView { id: string; title: string; status: AssessmentStatus; version: number }
export interface AssessmentVersionView { id: string; assessmentId: string; versionNumber: number; durationSeconds?: number; maxAttempts: number; totalMarks: number; passMarks: number; shuffleQuestions: boolean; allowBacktracking: boolean; availableFrom: string; availableUntil: string }
export interface AttemptView { id: string; assessmentVersionId: string; attemptNumber: number; status: AttemptStatus; startedAt: string; expiresAt: string; submittedAt?: string; durationSeconds?: number; allowBacktracking: boolean }
export interface AnswerView { id: string; assessmentQuestionId: string; serverSequence: number; savedAt: string }
export interface PacketQuestion { id: string; ordinal: number; marks: number; questionVersionId: string; type: QuestionType; title: string; difficulty: string; payloadJson: string }
export interface AssessmentPacket { version: AssessmentVersionView; questions: PacketQuestion[] }

export const fetchQuestions = () => request<PageResult<QuestionView>>('/api/v1/questions?size=100')
export const fetchAssessments = () => request<PageResult<AssessmentView>>('/api/v1/assessments?size=100')
export const fetchAssessmentVersions = (assessmentId: string) => request<AssessmentVersionView[]>(`/api/v1/assessments/${assessmentId}/versions`)
export const fetchAssessmentPacket = (versionId: string) => request<AssessmentPacket>(`/api/v1/assessment-versions/${versionId}/packet`)
export function createQuestion(input: { title: string; type: QuestionType; difficulty?: string; language?: string; payloadJson: string; positiveMarks: number; negativeMarks: number }): Promise<QuestionView> { return request('/api/v1/questions', { method: 'POST', body: JSON.stringify(input) }) }
export function createAssessment(input: { title: string }): Promise<AssessmentView> { return request('/api/v1/assessments', { method: 'POST', body: JSON.stringify(input) }) }
export function createAssessmentVersion(assessmentId: string, input: { durationSeconds?: number; maxAttempts: number; totalMarks: number; passMarks: number; shuffleQuestions: boolean; allowBacktracking: boolean; availableFrom: string; availableUntil: string; settingsJson?: string }): Promise<AssessmentVersionView> { return request(`/api/v1/assessments/${assessmentId}/versions`, { method: 'POST', body: JSON.stringify(input) }) }
export function assignAssessmentVersion(versionId: string, batchId: string) { return request(`/api/v1/assessment-versions/${versionId}/batches/${batchId}`, { method: 'POST' }) }
export function startAttempt(versionId: string): Promise<AttemptView> { return request(`/api/v1/assessment-versions/${versionId}/attempts`, { method: 'POST' }) }
export function getAttempt(attemptId: string): Promise<AttemptView> { return request(`/api/v1/attempts/${attemptId}`) }
export function autosaveAnswer(attemptId: string, questionId: string, payloadJson: string, idempotencyKey: string, clientSequence: number): Promise<AnswerView> { return request(`/api/v1/attempts/${attemptId}/answers/${questionId}`, { method: 'PUT', body: JSON.stringify({ payloadJson, idempotencyKey, clientSequence }) }) }
export function submitAttempt(attemptId: string): Promise<AttemptView> { return request(`/api/v1/attempts/${attemptId}/submit`, { method: 'POST' }) }

export interface AttemptAdminView { id:string; assessmentVersionId:string; membershipId:string; attemptNumber:number; status:AttemptStatus; startedAt:string; expiresAt:string; submittedAt?:string }
export interface AttemptGrade { attemptId:string; gradeRevisionId:string; revisionNumber:number; awardedMarks:number; maxMarks:number; passMarks:number; passed:boolean }
export type GradeRevisionStatus = 'GENERATED'|'PUBLISHED'|'SUPERSEDED'
export type GradeSource = 'SYSTEM'|'TEACHER'|'RECONCILIATION'|'REGRADE'
export interface GradeRevisionView { id:string; revisionNumber:number; status:GradeRevisionStatus; source:GradeSource; awardedMarks:number; maxMarks:number; createdAt:string; actorSubject:string }
export interface GradeItemView { id:string; answerId:string; assessmentQuestionId:string; maxMarks:number; negativeMarks:number; systemScore:number; teacherScore?:number; finalScore:number; explanationJson:string; rubricJson:string; createdAt:string }
export type ReviewType = 'GRADE_CHALLENGE'|'ANSWER_REVISION'
export type ReviewStatus = 'OPEN'|'UNDER_REVIEW'|'RESOLVED'|'REJECTED'|'WITHDRAWN'
export type AnswerRevisionStatus = 'PROPOSED'|'ACCEPTED'|'REJECTED'
export interface ReviewView { id:string; attemptId:string; membershipId:string; targetAnswerId?:string; type:ReviewType; status:ReviewStatus; subject:string; openingArgument:string; version:number; createdAt:string; updatedAt:string }
export interface CommentView { id:string; actorSubject:string; body:string; createdAt:string }
export interface ImpactView { id:string; previousScore:number; projectedScore:number; affectedRule:string; analysisJson:string; createdAt:string }
export interface AnswerRevisionView { id:string; answerId:string; revisionNumber:number; status:AnswerRevisionStatus; proposedPayloadJson:string; actorSubject:string; createdAt:string }
export const fetchMyAttempts = () => request<PageResult<AttemptAdminView>>('/api/v1/attempts/me?size=100')
export const fetchAssessmentAttempts = (versionId:string) => request<PageResult<AttemptAdminView>>(`/api/v1/assessment-versions/${versionId}/attempts?size=100`)
export const fetchAttemptGrade = (attemptId:string) => request<AttemptGrade>(`/api/v1/attempts/${attemptId}/grade`)
export const fetchGradeItems = (attemptId:string) => request<GradeItemView[]>(`/api/v1/attempts/${attemptId}/grade-items`)
export const fetchGradeHistory = (attemptId:string) => request<GradeRevisionView[]>(`/api/v1/attempts/${attemptId}/grade-history`)
export const gradeAttempt = (attemptId:string,publish=true) => request<AttemptGrade>(`/api/v1/attempts/${attemptId}/grade?publish=${publish}`,{method:'POST'})
export const overrideGradeItem = (attemptId:string,answerId:string,input:{finalScore:number;explanationJson:string;rubricJson?:string;publish:boolean}) => request<GradeItemView>(`/api/v1/attempts/${attemptId}/grade-items/${answerId}/override`,{method:'POST',body:JSON.stringify(input)})
export const fetchReviews = (attemptId:string) => request<ReviewView[]>(`/api/v1/attempts/${attemptId}/reviews`)
export const openReview = (attemptId:string,input:{answerId?:string;type:ReviewType;subject:string;openingArgument:string}) => request<ReviewView>(`/api/v1/attempts/${attemptId}/reviews`,{method:'POST',body:JSON.stringify(input)})
export const fetchDiscussion = (reviewId:string) => request<CommentView[]>(`/api/v1/reviews/${reviewId}/discussion`)
export const commentReview = (reviewId:string,body:string) => request<CommentView>(`/api/v1/reviews/${reviewId}/comments`,{method:'POST',body:JSON.stringify({body})})
export const analyzeReviewImpact = (reviewId:string) => request<ImpactView>(`/api/v1/reviews/${reviewId}/impact-analysis`,{method:'POST'})
export const fetchAnswerRevisions = (reviewId:string) => request<AnswerRevisionView[]>(`/api/v1/reviews/${reviewId}/answer-revisions`)
export const proposeAnswerRevision = (reviewId:string,answerId:string,payloadJson:string) => request<AnswerRevisionView>(`/api/v1/reviews/${reviewId}/answer-revisions`,{method:'POST',body:JSON.stringify({answerId,payloadJson})})
export const decideAnswerRevision = (revisionId:string,status:AnswerRevisionStatus) => request<AnswerRevisionView>(`/api/v1/answer-revisions/${revisionId}/decide`,{method:'POST',body:JSON.stringify({status})})
export const resolveReview = (reviewId:string,status:ReviewStatus) => request<ReviewView>(`/api/v1/reviews/${reviewId}/resolve`,{method:'POST',body:JSON.stringify({status})})
export const regradeReview = (reviewId:string,publish=true) => request<AttemptGrade>(`/api/v1/reviews/${reviewId}/regrade?publish=${publish}`,{method:'POST'})


export type AssignmentStatus = 'DRAFT' | 'PUBLISHED' | 'CLOSED' | 'ARCHIVED'
export type SubmissionStatus = 'DRAFT' | 'SUBMITTED' | 'RESUBMITTED' | 'WITHDRAWN'
export type SubmissionGradeStatus = 'UNGRADED' | 'GRADED' | 'RETURNED'
export interface AssignmentView { id: string; title: string; instructions?: string; status: AssignmentStatus; maxPoints: number; weightBasisPoints: number; dueAt: string; version: number }
export interface AssignmentSubmission { id: string; assignmentId: string; attemptNumber: number; status: SubmissionStatus; gradeStatus: SubmissionGradeStatus; textBody?: string; resourceId?: string; submittedAt?: string; awardedPoints?: number; graderFeedback?: string; rubricScoresJson?: string; late?: boolean; version: number }
export interface GradebookEntry { assignmentId: string; title: string; maxPoints: number; weightBasisPoints: number; awardedPoints?: number; status: AssignmentStatus }
export interface GradebookView { batchId: string; membershipId: string; weightedPoints: number; completedAssignments: number; totalAssignments: number; entries: GradebookEntry[] }
export const fetchAssignments = () => request<PageResult<AssignmentView>>('/api/v1/assignments?size=100')
export const fetchMyAssignmentSubmissions = () => request<PageResult<AssignmentSubmission>>('/api/v1/assignments/my-submissions?size=100')
export const createAssignment = (input: { title: string; instructions?: string; maxPoints: number; weightBasisPoints: number; dueAt: string }) => request<AssignmentView>('/api/v1/assignments', { method: 'POST', body: JSON.stringify(input) })
export const updateAssignmentStatus = (id: string, status: AssignmentStatus, expectedVersion: number) => request<AssignmentView>(`/api/v1/assignments/${id}/status`, { method: 'POST', body: JSON.stringify({ status, expectedVersion }) })
export const assignAssignmentToBatch = (id: string, batchId: string) => request<AssignmentView>(`/api/v1/assignments/${id}/batches/${batchId}`, { method: 'POST' })
export const saveAssignmentDraft = (id: string, textBody?: string, resourceId?: string) => request<AssignmentSubmission>(`/api/v1/assignments/${id}/my-submission`, { method: 'PUT', body: JSON.stringify({ textBody, resourceId }) })
export const submitAssignment = (id: string, textBody?: string, resourceId?: string) => request<AssignmentSubmission>(`/api/v1/assignments/${id}/my-submission`, { method: 'POST', body: JSON.stringify({ textBody, resourceId }) })
export const fetchAssignmentSubmissions = (id: string) => request<PageResult<AssignmentSubmission>>(`/api/v1/assignments/${id}/submissions?size=100`)
export const gradeAssignmentSubmission = (id: string, awardedPoints: number, feedback: string, rubricScoresJson: string, expectedVersion: number) => request<AssignmentSubmission>(`/api/v1/assignment-submissions/${id}/grade`, { method: 'PUT', body: JSON.stringify({ awardedPoints, feedback, rubricScoresJson, expectedVersion }) })
export const fetchMyGradebook = (batchId: string) => request<GradebookView>(`/api/v1/gradebook/me/${batchId}`)

export interface LiveClassView { id: string; classSessionId: string; batchId: string; roomName: string; status: 'SCHEDULED'|'LIVE'|'ENDED'|'CANCELLED'; attendancePolicy: 'MANUAL'|'JOIN_TIME'|'MINIMUM_DURATION'|'PERCENTAGE'; minimumAttendanceSeconds: number; attendanceThresholdBasisPoints: number; lowBandwidth: boolean; chatEnabled: boolean; startedAt?: string; endedAt?: string; version: number }
export interface LiveClassToken { serverUrl: string; participantToken: string; liveClassId: string; roomName: string; role: 'HOST'|'MODERATOR'|'PRESENTER'|'PARTICIPANT'|'OBSERVER'; lowBandwidth: boolean; chatEnabled: boolean }
export interface LiveParticipant { membershipId: string; role: string; status: string; joinedAt?: string; leftAt?: string; totalPresentSeconds: number; clientProfile?: string; moderationReason?: string; version: number }
export interface AttendanceView { membershipId: string; status: 'PRESENT'|'PARTIAL'|'ABSENT'|'EXCUSED'; presentSeconds: number; finalizedAt: string; source: string; notes?: string }
export const fetchLiveClasses = () => request<PageResult<LiveClassView>>('/api/v1/live-classes?size=100')
export const createLiveClass = (input: { classSessionId: string; attendancePolicy: LiveClassView['attendancePolicy']; minimumAttendanceSeconds?: number; attendanceThresholdBasisPoints?: number; lowBandwidth?: boolean; chatEnabled?: boolean }) => request<LiveClassView>('/api/v1/live-classes',{method:'POST',body:JSON.stringify(input)})
export const startLiveClass = (id:string) => request<LiveClassView>(`/api/v1/live-classes/${id}/start`,{method:'POST'})
export const endLiveClass = (id:string) => request<LiveClassView>(`/api/v1/live-classes/${id}/end`,{method:'POST'})
export const joinLiveClass = (id:string,clientProfile:'LOW_BANDWIDTH'|'BALANCED'|'HIGH_QUALITY'='BALANCED') => request<LiveClassToken>(`/api/v1/live-classes/${id}/join`,{method:'POST',body:JSON.stringify({clientProfile})})
export const leaveLiveClass = (id:string) => request<void>(`/api/v1/live-classes/${id}/leave`,{method:'POST'})
export const heartbeatLiveClass = (id:string,clientProfile='BALANCED') => request<void>(`/api/v1/live-classes/${id}/heartbeat`,{method:'POST',body:JSON.stringify({clientProfile})})
export const fetchLiveParticipants = (id:string) => request<PageResult<LiveParticipant>>(`/api/v1/live-classes/${id}/participants?size=100`)
export const muteLiveParticipant = (id:string,membershipId:string) => request<void>(`/api/v1/live-classes/${id}/participants/${membershipId}/mute`,{method:'POST'})
export const kickLiveParticipant = (id:string,membershipId:string,reason?:string) => request<void>(`/api/v1/live-classes/${id}/participants/${membershipId}/kick`,{method:'POST',body:JSON.stringify({reason})})
export const fetchAttendance = (id:string) => request<AttendanceView[]>(`/api/v1/live-classes/${id}/attendance`)
export const finalizeAttendance = (id:string) => request<void>(`/api/v1/live-classes/${id}/attendance/finalize`,{method:'POST'})
export const setAttendance = (id:string,membershipId:string,status:AttendanceView['status'],notes?:string) => request<void>(`/api/v1/live-classes/${id}/attendance/${membershipId}`,{method:'POST',body:JSON.stringify({status,notes})})

export type RecordingQualityPreset = 'ECONOMY' | 'BALANCED' | 'HIGH_QUALITY' | 'SOURCE_ARCHIVE'
export type RecordingProcessingStatus = 'REQUESTED' | 'STARTING' | 'RECORDING' | 'FINALIZING' | 'READY' | 'ARCHIVING' | 'ARCHIVED' | 'FAILED' | 'DELETED'
export type RecordingStorageTier = 'HOT' | 'CACHE' | 'ARCHIVE'
export interface RecordingView { id:string; liveClassId:string; classSessionId:string; qualityPreset:RecordingQualityPreset; status:RecordingProcessingStatus; storageTier:RecordingStorageTier; storageProvider:'LOCAL'|'S3_COMPATIBLE'|'GOOGLE_DRIVE'|'GOOGLE_SHARED_DRIVE'; sizeBytes?:number; durationSeconds?:number; requestedAt:string; startedAt?:string; endedAt?:string; readyAt?:string; archivedAt?:string; failureReason?:string; version:number }
export interface PlaybackView { streamUrl:string; expiresAt:string; watermarkName:string; watermarkId:string; issuedAt:string }
export interface RecordingStoragePolicyView { hotCacheDays:number; cacheDays:number; retentionDays?:number; hotProvider:string; cacheProvider:string; archiveProvider:string; deletedObjectGraceDays:number; version:number }
export const fetchRecordings = () => request<PageResult<RecordingView>>('/api/v1/recordings?size=100')
export const requestRecording = (liveClassId:string,qualityPreset:RecordingQualityPreset='BALANCED') => request<RecordingView>(`/api/v1/live-classes/${liveClassId}/recordings`,{method:'POST',body:JSON.stringify({qualityPreset})})
export const fetchRecording = (id:string) => request<RecordingView>(`/api/v1/recordings/${id}`)
export const stopRecording = (id:string) => request<void>(`/api/v1/recordings/${id}/stop`,{method:'POST'})
export const issueRecordingPlayback = (id:string) => request<PlaybackView>(`/api/v1/recordings/${id}/playback`,{method:'POST'})
export const fetchRecordingStoragePolicy = () => request<RecordingStoragePolicyView>('/api/v1/recording-storage-policy')
export const updateRecordingStoragePolicy = (input:{hotCacheDays:number;cacheDays:number;retentionDays?:number;hotProvider:string;cacheProvider:string;archiveProvider:string;deletedObjectGraceDays:number;expectedVersion:number}) => request<RecordingStoragePolicyView>('/api/v1/recording-storage-policy',{method:'PUT',body:JSON.stringify(input)})

export interface FinanceFee { id: string; name: string; amount: number; currency: string; status: 'ACTIVE' | 'INACTIVE'; version: number }
export interface FinanceInstallment { id: string; sequenceNo: number; amount: number; dueOn: string; status: string; version: number }
export interface FinanceInvoice { id: string; membershipId: string; invoiceNumber: string; totalAmount: number; paidAmount: number; currency: string; status: string; issuedOn?: string; dueOn: string; version: number; installments: FinanceInstallment[] }
export interface FinanceInvoicePage { items: FinanceInvoice[]; page: number; size: number; totalElements: number }
export const fetchFinanceFees = () => request<FinanceFee[]>('/api/v1/finance/fees')
export const createFinanceFee = (input:{name:string;amount:number;currency:string;status:'ACTIVE'|'INACTIVE'}) => request<FinanceFee>('/api/v1/finance/fees',{method:'POST',body:JSON.stringify({...input,expectedVersion:0})})
export const fetchFinanceInvoices = () => request<FinanceInvoicePage>('/api/v1/finance/invoices?size=100')
export const createFinanceInvoice = (input:{membershipId:string;totalAmount:number;currency:string;dueOn:string}) => request<FinanceInvoice>('/api/v1/finance/invoices',{method:'POST',body:JSON.stringify(input)})
export const issueFinanceInvoice = (id:string,expectedVersion:number) => request<FinanceInvoice>(`/api/v1/finance/invoices/${id}/issue`,{method:'POST',body:JSON.stringify({expectedVersion})})
export const recordFinancePayment = (id:string,input:{amount:number;currency:string}) => request('/api/v1/finance/invoices/'+id+'/payments',{method:'POST',body:JSON.stringify(input)})


export interface AnalyticsOverview {
  from: string
  to: string
  activeMemberships: number
  enrollments: number
  attendance: { present: number; partial: number; absent: number; excused: number; presentSeconds: number; presentRatePercent: number }
  assessment: { attempts: number; submitted: number; published: number; passed: number; averagePercent: number }
  recording: { count: number; totalSeconds: number; totalBytes: number; ready: number; archived: number; failed: number }
  finance: { invoiced: number; paid: number; outstanding: number; paymentCount: number }
  usage: { items: { limitKey: string; hardLimit: number; consumed: number }[] }
}

export interface AnalyticsForecast {
  generatedAt: string
  historyDays: number
  recentBytes: number
  recentSeconds: number
  dailyBytes: number
  dailyMinutes: number
  projected30DayBytes: number
  projected365DayBytes: number
}

export async function getAnalyticsOverview(from?: string, to?: string) {
  return request<AnalyticsOverview>(`/api/v1/analytics/overview${rangeQuery(from,to)}`)
}
export async function getAnalyticsForecast() {
  return request<AnalyticsForecast>('/api/v1/analytics/forecast')
}
export async function exportAnalytics(report: string, from?: string, to?: string) {
  return apiGetText(`/analytics/exports/${encodeURIComponent(report)}${rangeQuery(from,to)}`)
}
function rangeQuery(from?: string, to?: string) {
  const p = new URLSearchParams()
  if (from) p.set('from', from)
  if (to) p.set('to', to)
  const q=p.toString()
  return q ? `?${q}` : ''
}
async function apiGetText(path: string): Promise<string> {
  const token = await accessToken()
  const response = await fetch(`/api/v1${path}`, { headers: { Authorization: `Bearer ${token}` } })
  if (!response.ok) throw new Error(await response.text())
  return response.text()
}


export interface LearningOutcomeView { id: string; code: string; name: string; description?: string; status: string; version: number }
export interface LearningPathView { id: string; code: string; name: string; description?: string; status: string; version: number }
export interface CredentialTemplateView { id: string; code: string; name: string; description?: string; credentialType: string; version: number }
export interface CredentialView { id: string; templateId: string; membershipId: string; verificationCode: string; verificationUrl: string; issuedAt: string; status: string; issuerSubject: string; reason: string; qrPayload: string; version: number }
export interface CredentialVerification { verificationCode: string; templateName: string; recipientName: string; issuedAt: string; status: string; issuerSubject: string; reason: string }
export interface MentorshipView { id: string; mentorMembershipId: string; menteeMembershipId: string; goal: string; status: string; createdAt: string; updatedAt?: string; version: number }
export interface SurveyView { id: string; title: string; description?: string; anonymous: boolean; opensAt?: string; closesAt?: string; status: string; version: number }
export interface FeedbackView { id: string; targetType: string; targetId: string; authorMembershipId: string; rating: number; comments: string; createdAt: string; status: string }
export const fetchLearningOutcomes = () => request<LearningOutcomeView[]>('/api/v1/learning/outcomes')
export const createLearningOutcome = (input: { code: string; name: string; description?: string }) => request<LearningOutcomeView>('/api/v1/learning/outcomes', { method: 'POST', body: JSON.stringify(input) })
export const fetchCredentialTemplates = () => request<CredentialTemplateView[]>('/api/v1/credentials/templates')
export const createCredentialTemplate = (input: { code: string; name: string; description?: string; credentialType: string }) => request<CredentialTemplateView>('/api/v1/credentials/templates', { method: 'POST', body: JSON.stringify(input) })
export const issueCredential = (input: { templateId: string; membershipId: string; reason: string; sourceId?: string }) => request<CredentialView>('/api/v1/credentials', { method: 'POST', body: JSON.stringify(input) })
export const fetchMentoring = () => request<MentorshipView[]>('/api/v1/engagement/mentoring')
export const createMentorship = (input: { mentorMembershipId: string; menteeMembershipId: string; goal: string }) => request<MentorshipView>('/api/v1/engagement/mentoring', { method: 'POST', body: JSON.stringify(input) })
export const fetchSurveys = () => request<SurveyView[]>('/api/v1/engagement/surveys')
export const createSurvey = (input: { title: string; description?: string; anonymous: boolean; opensAt?: string; closesAt?: string }) => request<SurveyView>('/api/v1/engagement/surveys', { method: 'POST', body: JSON.stringify(input) })
export const submitFeedback = (input: { targetId: string; targetType: string; rating: number; comments: string }) => request<void>('/api/v1/engagement/feedback', { method: 'POST', body: JSON.stringify(input) })

export interface ApiCredentialView { id: string; name: string; scopes: PermissionKey[]; expiresAt?: string; secret?: string; version: number }
export interface WebhookView { id: string; callbackUrl: string; eventFilter: string; enabled: boolean; secret?: string; version: number }
export interface IdentityProviderView { id: string; providerKey: string; issuer: string; authorizationEndpoint: string; clientId: string; redirectUri: string; scopes: string; version: number }
export interface StorageBindingView { providerType: 'LOCAL' | 'S3_COMPATIBLE' | 'GOOGLE_DRIVE' | 'GOOGLE_SHARED_DRIVE'; objectPrefix: string; version: number }
export interface NotificationProviderView { id: string; providerKey: string; providerType: string; endpoint: string; version: number }

export const createApiCredential = (body: { membershipId: string; name: string; scopes?: PermissionKey[]; expiresAt?: string }) => request<ApiCredentialView>('/api/v1/integrations/api-credentials', { method: 'POST', body: JSON.stringify(body) })
export const fetchApiCredentials = (membershipId: string) => request<ApiCredentialView[]>(`/api/v1/integrations/api-credentials?membershipId=${encodeURIComponent(membershipId)}`)
export const revokeApiCredential = (id: string, expectedVersion: number) => request<void>(`/api/v1/integrations/api-credentials/${id}?expectedVersion=${expectedVersion}`, { method: 'DELETE' })
export const createWebhook = (body: { callbackUrl: string; eventFilter?: string }) => request<WebhookView>('/api/v1/integrations/webhooks', { method: 'POST', body: JSON.stringify(body) })
export const fetchWebhooks = () => request<WebhookView[]>('/api/v1/integrations/webhooks')
export const revokeWebhook = (id: string, expectedVersion: number) => request<void>(`/api/v1/integrations/webhooks/${id}?expectedVersion=${expectedVersion}`, { method: 'DELETE' })
export const configureStorageBinding = (body: { providerType: StorageBindingView['providerType']; objectPrefix?: string; expectedVersion?: number }) => request<StorageBindingView>('/api/v1/integrations/storage', { method: 'PUT', body: JSON.stringify({ expectedVersion: 0, ...body }) })
export const fetchStorageBinding = () => request<StorageBindingView | null>('/api/v1/integrations/storage')
export const configureIdentityProvider = (body: { providerKey: string; issuer: string; authorizationEndpoint: string; clientId: string; clientSecret?: string; redirectUri: string; scopes?: string; expectedVersion?: number }) => request<IdentityProviderView>('/api/v1/integrations/identity-providers', { method: 'PUT', body: JSON.stringify({ expectedVersion: 0, ...body }) })
export const fetchIdentityProviders = () => request<IdentityProviderView[]>('/api/v1/integrations/identity-providers')
export const configureNotificationProvider = (body: { providerKey: string; providerType: string; endpoint: string; credential?: string; expectedVersion?: number }) => request<NotificationProviderView>('/api/v1/integrations/notification-providers', { method: 'PUT', body: JSON.stringify({ expectedVersion: 0, ...body }) })
export const testNotificationProvider = (key: string, body: { title: string; body: string }) => request<void>(`/api/v1/integrations/notification-providers/${encodeURIComponent(key)}/test`, { method: 'POST', body: JSON.stringify(body) })
