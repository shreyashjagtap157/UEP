import { accessToken, localApiMode } from '../auth/keycloak'
import { joinBackendUrl, resolveBackendBaseUrl } from './api-url'
import type { PermissionKey } from './api'
export type { PermissionKey } from './api'
export interface PlatformVersion { version: string; releaseStatus: string }
export interface AuthenticationAssurance { acr:string; methods:string[]; otpEvidence:boolean; webAuthnEvidence:boolean; multiFactorEvidence:boolean }
export interface CurrentIdentity { userId:string; membershipId:string; oidcSubject:string; email?:string; displayName:string; tenantId:string; membershipStatus:'ACTIVE'|'SUSPENDED'|'ENDED'; primaryBranchId?:string; roles:string[]; permissions:PermissionKey[]; primaryBranchPermissions:PermissionKey[]; authenticationAssurance:AuthenticationAssurance }
export interface PageResult<T>{items:T[];page:number;size:number;totalElements:number;totalPages:number}
export interface RoleReference{id:string;systemKey?:string;name:string;systemManaged:boolean}
export interface MemberView{membershipId:string;userId:string;oidcSubject:string;email?:string;displayName:string;userStatus:'ACTIVE'|'DISABLED';membershipStatus:'ACTIVE'|'SUSPENDED'|'ENDED';primaryBranchId?:string;externalReference?:string;joinedAt:string;roles:RoleReference[];version:number}
export interface RoleView{id:string;systemKey?:string;name:string;description?:string;systemManaged:boolean;permissions:PermissionKey[];version:number}
export interface BranchView{id:string;code:string;displayName:string;timezone:string;status:'ACTIVE'|'INACTIVE'|'CLOSED';createdAt:string;version:number}
export interface OrganizationSettings{tenantId:string;defaultTimezone:string;defaultLocale:string;weekStartsOn:number;supportEmail?:string;supportUrl?:string;version:number}
export interface SessionView{id:string;startedAt:string;lastSeenAt:string;expiresAt?:string;revokedAt?:string;revocationReason?:string;current:boolean}
export interface RoleAssignmentView{id:string;membershipId:string;roleId:string;roleName:string;scopeKind:'TENANT'|'BRANCH';scopeId?:string}
export interface DeploymentInfo { platform: string; status: string; url: string; lastChecked: string }
export class PlatformApiError extends Error{readonly status:number;readonly problemDetail:unknown;constructor(status:number,message:string,problemDetail?:unknown){super(message);this.status=status;this.problemDetail=problemDetail}}
function extractServerMessage(body:unknown,fallback:string):string{if(body&&typeof body==='object'){const detail=(body as {detail?:unknown}).detail;const message=(body as {message?:unknown}).message;const title=(body as {title?:unknown}).title;if(typeof detail==='string'&&detail.trim())return detail;if(typeof message==='string'&&message.trim())return message;if(typeof title==='string'&&title.trim())return title}return fallback}
export const localMode = import.meta.env.VITE_LOCAL_MODE === 'true'
// Prefer explicitly‑set backend URL; fall back to per‑mode defaults only if omitted.
// An empty base URL keeps requests same-origin so the active reverse proxy
// (the local Vite development proxy in Real mode) can route /api/* to Spring Boot.
export const backendUrl = () => resolveBackendBaseUrl(
  import.meta.env.VITE_BACKEND_URL,
  localMode,
  import.meta.env.VITE_LOCAL_BACKEND_URL,
)
async function authorizedResponse(path:string,init:RequestInit,accept:string):Promise<Response>{
  const headers=new Headers(init.headers)
  headers.set('Accept',accept)
  if(localApiMode){
    const localToken=import.meta.env.VITE_LOCAL_API_AUTH_TOKEN
    if(!localToken)throw new PlatformApiError(0,'The local API session is not configured. Restart UEP with the native launcher.')
    headers.set('X-UEP-Local-Auth',localToken)
  }else{
    let token:string
    try{token=await accessToken()}catch(error){
      const reason=error instanceof Error&&error.message?error.message:String(error)
      throw new PlatformApiError(0,`Network error: the authentication session could not provide an access token (${reason})`)
    }
    headers.set('Authorization',`Bearer ${token}`)
  }
  if(init.body&&!headers.has('Content-Type'))headers.set('Content-Type','application/json')
  const fullPath = joinBackendUrl(backendUrl(),path)
  try{
    return await fetch(fullPath,{...init,headers})
  }catch(error){
    const reason=error instanceof Error&&error.message?error.message:String(error)
    throw new PlatformApiError(0,`Network error: the platform service could not be reached for ${fullPath} (${reason})`)
  }
}
function parseResponseBody(text:string):unknown{
  if(!text)return undefined
  try{return JSON.parse(text) as unknown}catch{return text}
}
export async function request<T>(path:string,init:RequestInit={}):Promise<T>{
  const response=await authorizedResponse(path,init,'application/json')
  if(response.status===204)return undefined as T
  const text=await response.text()
  const body=parseResponseBody(text)
  if(!response.ok)throw new PlatformApiError(response.status,extractServerMessage(body,`Platform request failed: ${response.status} ${response.statusText}`),body)
  return body as T
}
export async function requestText(path:string,init:RequestInit={}):Promise<string>{
  const response=await authorizedResponse(path,init,'text/csv')
  if(response.status===204)return ''
  const text=await response.text()
  const body=parseResponseBody(text)
  if(!response.ok)throw new PlatformApiError(response.status,extractServerMessage(body,`Platform request failed: ${response.status} ${response.statusText}`),body)
  return text
}
export async function fetchPlatformVersion(signal?:AbortSignal):Promise<PlatformVersion|undefined>{
  try{
    const init:RequestInit={headers:{Accept:'application/json'}}
    if(signal)init.signal=signal
    const response=await request('/api/v1/platform/version',init)
    if(!response)return undefined
    return response as PlatformVersion
  }catch{return undefined}
}
export const fetchCurrentIdentity=()=>request<CurrentIdentity>('/api/v1/me')
export const fetchMembers=()=>request<PageResult<MemberView>>('/api/v1/identity/memberships?size=100')
export const fetchRoles=()=>request<PageResult<RoleView>>('/api/v1/identity/roles?size=100')
export const fetchBranches=()=>request<PageResult<BranchView>>('/api/v1/organization/branches?size=100')
export const fetchOrganizationSettings=()=>request<OrganizationSettings>('/api/v1/organization/settings')
export const fetchMySessions=()=>request<PageResult<SessionView>>('/api/v1/identity/sessions/me?size=100')
export const fetchMemberSessions=(membershipId:string)=>request<PageResult<SessionView>>(`/api/v1/identity/sessions/memberships/${encodeURIComponent(membershipId)}?size=100`)
export function provisionMembership(input:{subject:string;email?:string;displayName:string;roleIds:string[]}):Promise<MemberView>{
  return request('/api/v1/identity/memberships',{method:'POST',body:JSON.stringify(input)})
}
export function updateMembership(membershipId:string,input:{status:MemberView['membershipStatus'];primaryBranchId?:string|null;externalReference?:string|null;expectedVersion:number}):Promise<MemberView>{
  return request(`/api/v1/identity/memberships/${encodeURIComponent(membershipId)}`,{method:'PATCH',body:JSON.stringify(input)})
}
export const fetchRoleAssignments=(membershipId:string)=>request<PageResult<RoleAssignmentView>>(`/api/v1/identity/roles/memberships/${encodeURIComponent(membershipId)}?size=100`)
export function assignRole(input:{membershipId:string;roleId:string;scopeKind:'TENANT'|'BRANCH';scopeId?:string|null}):Promise<RoleAssignmentView>{
  return request('/api/v1/identity/roles/assignments',{method:'POST',body:JSON.stringify(input)})
}
export function unassignRole(assignmentId:string):Promise<void>{
  return request(`/api/v1/identity/roles/assignments/${encodeURIComponent(assignmentId)}`,{method:'DELETE'})
}
export function createRole(input:{name:string;description?:string;permissions:PermissionKey[]}):Promise<RoleView>{
  return request('/api/v1/identity/roles',{method:'POST',body:JSON.stringify({...input,expectedVersion:0})})
}
export function updateRole(roleId:string,input:{name:string;description?:string;permissions:PermissionKey[];expectedVersion:number}):Promise<RoleView>{
  return request(`/api/v1/identity/roles/${encodeURIComponent(roleId)}`,{method:'PUT',body:JSON.stringify(input)})
}
export function deleteRole(roleId:string):Promise<void>{
  return request(`/api/v1/identity/roles/${encodeURIComponent(roleId)}`,{method:'DELETE'})
}
export function createBranch(input:{code:string;displayName:string;timezone:string}):Promise<BranchView>{
  return request('/api/v1/organization/branches',{method:'POST',body:JSON.stringify(input)})
}
export function updateOrganizationSettings(input:OrganizationSettings):Promise<OrganizationSettings>{
  return request('/api/v1/organization/settings',{method:'PUT',body:JSON.stringify({defaultTimezone:input.defaultTimezone,defaultLocale:input.defaultLocale,weekStartsOn:input.weekStartsOn,supportEmail:input.supportEmail,supportUrl:input.supportUrl,expectedVersion:input.version})})
}
export function revokeOwnSession(sessionId:string):Promise<void>{
  return request(`/api/v1/identity/sessions/me/${encodeURIComponent(sessionId)}`,{method:'DELETE'})
}
export function revokeMemberSession(sessionId:string,reason:string):Promise<void>{
  return request(`/api/v1/identity/sessions/${encodeURIComponent(sessionId)}`,{method:'DELETE',body:JSON.stringify({reason})})
}
