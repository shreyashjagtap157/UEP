import { accessToken } from '../auth/keycloak'

export interface PlatformVersion { version: string; releaseStatus: string }
export type PermissionKey =
  | 'ORGANIZATION_VIEW' | 'ORGANIZATION_MANAGE' | 'BRANCHES_VIEW' | 'BRANCHES_MANAGE' | 'USERS_VIEW' | 'USERS_MANAGE'
  | 'ROLES_VIEW' | 'ROLES_MANAGE' | 'ROLES_ASSIGN' | 'SESSIONS_VIEW' | 'SESSIONS_MANAGE' | 'ACADEMICS_VIEW' | 'ACADEMICS_MANAGE'
  | 'CURRICULUM_VIEW' | 'CURRICULUM_MANAGE' | 'ENROLLMENTS_VIEW' | 'ENROLLMENTS_MANAGE' | 'TEACHING_ASSIGNMENTS_VIEW' | 'TEACHING_ASSIGNMENTS_MANAGE'
  | 'SCHEDULE_VIEW' | 'SCHEDULE_MANAGE' | 'SCHEDULE_CONFLICT_OVERRIDE' | 'ANNOUNCEMENTS_VIEW' | 'ANNOUNCEMENTS_MANAGE'
  | 'MESSAGING_VIEW' | 'MESSAGING_SEND' | 'NOTIFICATION_OPERATIONS_VIEW' | 'CONTENT_VIEW' | 'CONTENT_MANAGE'
  | 'ASSESSMENTS_VIEW' | 'ASSESSMENTS_MANAGE' | 'ASSESSMENTS_TAKE' | 'GRADING_VIEW' | 'GRADING_MANAGE' | 'REVIEW_VIEW' | 'REVIEW_SUBMIT' | 'REVIEW_MANAGE'
  | 'ASSIGNMENTS_VIEW' | 'ASSIGNMENTS_MANAGE' | 'ASSIGNMENTS_TAKE' | 'GRADEBOOK_VIEW' | 'GRADEBOOK_MANAGE'
  | 'LIVE_CLASS_VIEW' | 'LIVE_CLASS_MANAGE' | 'LIVE_CLASS_MODERATE' | 'LIVE_CLASS_CHAT' | 'PRESENCE_VIEW' | 'PRESENCE_MANAGE'
  | 'ATTENDANCE_VIEW' | 'ATTENDANCE_MANAGE' | 'RECORDINGS_VIEW' | 'RECORDINGS_MANAGE' | 'FINANCE_VIEW' | 'FINANCE_MANAGE'
  | 'COMMERCIAL_VIEW' | 'COMMERCIAL_MANAGE' | 'PAYMENTS_MANAGE' | 'ANALYTICS_VIEW' | 'ANALYTICS_MANAGE' | 'REPORTS_EXPORT'
  | 'OPERATIONS_VIEW' | 'OPERATIONS_MANAGE' | 'AUDIT_VIEW' | 'LEARNING_OUTCOMES_VIEW' | 'LEARNING_OUTCOMES_MANAGE'
  | 'CREDENTIALS_VIEW' | 'CREDENTIALS_MANAGE' | 'CREDENTIALS_VERIFY' | 'MENTORING_VIEW' | 'MENTORING_MANAGE' | 'SURVEYS_VIEW' | 'SURVEYS_MANAGE'
  | 'FEEDBACK_VIEW' | 'FEEDBACK_MANAGE' | 'API_VIEW' | 'API_MANAGE' | 'WEBHOOKS_VIEW' | 'WEBHOOKS_MANAGE' | 'FEDERATION_VIEW' | 'FEDERATION_MANAGE'
  | 'INTEROPERABILITY_VIEW' | 'INTEROPERABILITY_MANAGE' | 'EXTERNAL_STORAGE_MANAGE' | 'EXTERNAL_NOTIFICATIONS_MANAGE'
export interface AuthenticationAssurance { acr: string; methods: string[]; otpEvidence: boolean; webAuthnEvidence: boolean; multiFactorEvidence: boolean }
export interface CurrentIdentity { userId: string; membershipId: string; oidcSubject: string; email?: string; displayName: string; tenantId: string; membershipStatus: 'ACTIVE'|'SUSPENDED'|'ENDED'; primaryBranchId?: string; roles: string[]; permissions: PermissionKey[]; primaryBranchPermissions: PermissionKey[]; authenticationAssurance: AuthenticationAssurance }
export interface PageResult<T> { items: T[]; page: number; size: number; totalElements: number; totalPages: number }
export interface RoleReference { id: string; systemKey?: string; name: string; systemManaged: boolean }
export interface MemberView { membershipId: string; userId: string; oidcSubject: string; email?: string; displayName: string; userStatus: 'ACTIVE'|'DISABLED'; membershipStatus: 'ACTIVE'|'SUSPENDED'|'ENDED'; primaryBranchId?: string; externalReference?: string; joinedAt: string; roles: RoleReference[]; version: number }
export interface RoleView { id: string; systemKey?: string; name: string; description?: string; systemManaged: boolean; permissions: PermissionKey[]; version: number }
export interface BranchView { id: string; code: string; displayName: string; timezone: string; status: 'ACTIVE'|'INACTIVE'|'CLOSED'; createdAt: string; version: number }
export interface OrganizationSettings { tenantId: string; defaultTimezone: string; defaultLocale: string; weekStartsOn: number; supportEmail?: string; supportUrl?: string; version: number }
export interface SessionView { id: string; startedAt: string; lastSeenAt: string; expiresAt?: string; revokedAt?: string; revocationReason?: string; current: boolean }
export class PlatformApiError extends Error { readonly status: number; readonly problemDetail: unknown; constructor(status: number, message: string, problemDetail?: unknown) { super(message); this.name='PlatformApiError'; this.status=status; this.problemDetail=problemDetail } }
function extractServerMessage(body: unknown, fallback: string): string { if (body && typeof body === 'object') { const detail=(body as {detail?:unknown}).detail; const message=(body as {message?:unknown}).message; const title=(body as {title?:unknown}).title; if(typeof detail==='string'&&detail.trim())return detail; if(typeof message==='string'&&message.trim())return message; if(typeof title==='string'&&title.trim())return title } return fallback }
export async function request<T>(path: string, init: RequestInit = {}): Promise<T> { let token:string; try{token=await accessToken()}catch(error){const reason=error instanceof Error&&error.message?error.message:String(error);throw new PlatformApiError(0,`Network error: the authentication session could not provide an access token (${reason})`)} const headers=new Headers(init.headers); headers.set('Accept','application/json'); headers.set('Authorization',`Bearer ${token}`); if(init.body&&!headers.has('Content-Type'))headers.set('Content-Type','application/json'); let response:Response; try{response=await fetch(path,{...init,headers})}catch(error){const reason=error instanceof Error&&error.message?error.message:String(error);throw new PlatformApiError(0,`Network error: the platform service could not be reached for ${path} (${reason})`)} if(response.status===204)return undefined as T; const text=await response.text(); let body:unknown; if(text){try{body=JSON.parse(text) as unknown}catch{body=text}} if(!response.ok)throw new PlatformApiError(response.status,extractServerMessage(body,`Platform request failed: ${response.status} ${response.statusText}`),body); return body as T }
export async function fetchPlatformVersion(signal?: AbortSignal): Promise<PlatformVersion|undefined>{try{const init:RequestInit={headers:{Accept:'application/json'}};if(signal)init.signal=signal;const response=await fetch('/api/v1/platform/version',init);if(!response.ok)return undefined;return await response.json() as PlatformVersion}catch{return undefined}}
export const fetchCurrentIdentity=()=>request<CurrentIdentity>('/api/v1/me')
export const fetchMembers=()=>request<PageResult<MemberView>>('/api/v1/identity/memberships?size=100')
export const fetchRoles=()=>request<PageResult<RoleView>>('/api/v1/identity/roles?size=100')
export const fetchBranches=()=>request<PageResult<BranchView>>('/api/v1/organization/branches?size=100')
export const fetchOrganizationSettings=()=>request<OrganizationSettings>('/api/v1/organization/settings')
export const fetchMySessions=()=>request<PageResult<SessionView>>('/api/v1/identity/sessions/me?size=100')
export function provisionMembership(input:{subject:string;email?:string;displayName:string;roleIds:string[]}):Promise<MemberView>{return request('/api/v1/identity/memberships',{method:'POST',body:JSON.stringify(input)})}
export function createRole(input:{name:string;description?:string;permissions:PermissionKey[]}):Promise<RoleView>{return request('/api/v1/identity/roles',{method:'POST',body:JSON.stringify({...input,expectedVersion:0})})}
export function createBranch(input:{code:string;displayName:string;timezone:string}):Promise<BranchView>{return request('/api/v1/organization/branches',{method:'POST',body:JSON.stringify(input)})}
export function updateOrganizationSettings(input:OrganizationSettings):Promise<OrganizationSettings>{return request('/api/v1/organization/settings',{method:'PUT',body:JSON.stringify({defaultTimezone:input.defaultTimezone,defaultLocale:input.defaultLocale,weekStartsOn:input.weekStartsOn,supportEmail:input.supportEmail,supportUrl:input.supportUrl,expectedVersion:input.version})})}
export function revokeOwnSession(sessionId:string):Promise<void>{return request(`/api/v1/identity/sessions/me/${sessionId}`,{method:'DELETE'})}
