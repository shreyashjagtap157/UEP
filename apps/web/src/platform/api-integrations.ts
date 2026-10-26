import { request, type PermissionKey } from './api-core'
export interface ApiCredentialView{id:string;name:string;scopes:PermissionKey[];expiresAt?:string;secret?:string;version:number}
export interface WebhookView{id:string;callbackUrl:string;eventFilter:string;enabled:boolean;secret?:string;version:number}
export interface IdentityProviderView{id:string;providerKey:string;issuer:string;authorizationEndpoint:string;clientId:string;redirectUri:string;scopes:string;version:number}
export interface StorageBindingView{providerType:'LOCAL'|'S3_COMPATIBLE'|'GOOGLE_DRIVE'|'GOOGLE_SHARED_DRIVE';objectPrefix:string;version:number}
export interface NotificationProviderView{id:string;providerKey:string;providerType:string;endpoint:string;version:number}
export const createApiCredential=(body:{membershipId:string;name:string;scopes:PermissionKey[];expiresAt?:string})=>request<ApiCredentialView>('/api/v1/integrations/api-credentials',{method:'POST',body:JSON.stringify(body)})
export const fetchApiCredentials=(membershipId:string)=>request<ApiCredentialView[]>(`/api/v1/integrations/api-credentials?membershipId=${encodeURIComponent(membershipId)}`)
export const revokeApiCredential=(id:string,expectedVersion:number)=>request<void>(`/api/v1/integrations/api-credentials/${id}?expectedVersion=${expectedVersion}`,{method:'DELETE'})
export const createWebhook=(body:{callbackUrl:string;eventFilter?:string})=>request<WebhookView>('/api/v1/integrations/webhooks',{method:'POST',body:JSON.stringify(body)})
export const fetchWebhooks=()=>request<WebhookView[]>('/api/v1/integrations/webhooks')
export const revokeWebhook=(id:string,expectedVersion:number)=>request<void>(`/api/v1/integrations/webhooks/${id}?expectedVersion=${expectedVersion}`,{method:'DELETE'})
export const configureStorageBinding=(body:{providerType:StorageBindingView['providerType'];objectPrefix?:string;expectedVersion?:number})=>request<StorageBindingView>('/api/v1/integrations/storage',{method:'PUT',body:JSON.stringify({expectedVersion:0,...body})})
export const fetchStorageBinding=()=>request<StorageBindingView|null>('/api/v1/integrations/storage')
export const configureIdentityProvider=(body:{providerKey:string;issuer:string;authorizationEndpoint:string;clientId:string;clientSecret?:string;redirectUri:string;scopes?:string;expectedVersion?:number})=>request<IdentityProviderView>('/api/v1/integrations/identity-providers',{method:'PUT',body:JSON.stringify({expectedVersion:0,...body})})
export const fetchIdentityProviders=()=>request<IdentityProviderView[]>('/api/v1/integrations/identity-providers')
export const configureNotificationProvider=(body:{providerKey:string;providerType:string;endpoint:string;credential?:string;expectedVersion?:number})=>request<NotificationProviderView>('/api/v1/integrations/notification-providers',{method:'PUT',body:JSON.stringify({expectedVersion:0,...body})})
export const testNotificationProvider=(key:string,body:{title:string;body:string})=>request<void>(`/api/v1/integrations/notification-providers/${encodeURIComponent(key)}/test`,{method:'POST',body:JSON.stringify(body)})
