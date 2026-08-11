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
