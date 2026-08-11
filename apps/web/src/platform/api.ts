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
