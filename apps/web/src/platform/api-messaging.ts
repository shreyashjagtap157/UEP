import { request, type PageResult } from './api-core'
export interface MessageConversationView{id:string;subject?:string;createdByMembershipId:string;createdAt:string;updatedAt:string;version:number;participantCount:number;unreadCount:number}
export interface MessageBlockView{membershipId:string;createdAt:string}
export interface MessageView{id:string;conversationId:string;senderMembershipId:string;body:string;createdAt:string;editedAt?:string;deletedAt?:string;version:number}
export interface MessagePage{items:MessageView[];page:number;size:number;totalElements:number;totalPages:number}
export const fetchMessageConversations=(page=0,size=25)=>request<MessageConversationView[]>(`/api/v1/messaging/conversations?page=${page}&size=${size}`)
export const createMessageConversation=(participantMembershipIds:string[],subject?:string)=>request<MessageConversationView>('/api/v1/messaging/conversations',{method:'POST',body:JSON.stringify({participantMembershipIds,subject})})
export const fetchMessages=(conversationId:string,page=0,size=50)=>request<MessagePage>(`/api/v1/messaging/conversations/${encodeURIComponent(conversationId)}/messages?page=${page}&size=${size}`)
export const sendMessage=(conversationId:string,body:string)=>request<MessageView>(`/api/v1/messaging/conversations/${encodeURIComponent(conversationId)}/messages`,{method:'POST',body:JSON.stringify({body})})
export const fetchMessageBlocks=()=>request<MessageBlockView[]>('/api/v1/messaging/blocks')
export const blockMessageMembership=(membershipId:string)=>request<void>(`/api/v1/messaging/blocks/${encodeURIComponent(membershipId)}`,{method:'PUT'})
export const unblockMessageMembership=(membershipId:string)=>request<void>(`/api/v1/messaging/blocks/${encodeURIComponent(membershipId)}`,{method:'DELETE'})
export const markConversationRead=(conversationId:string)=>request<void>(`/api/v1/messaging/conversations/${encodeURIComponent(conversationId)}/read`,{method:'POST'})
export const editMessage=(messageId:string,body:string,expectedVersion:number)=>request<MessageView>(`/api/v1/messaging/messages/${encodeURIComponent(messageId)}`,{method:'PUT',body:JSON.stringify({body,expectedVersion})})
export const deleteMessage=(messageId:string,expectedVersion:number)=>request<void>(`/api/v1/messaging/messages/${encodeURIComponent(messageId)}?expectedVersion=${expectedVersion}`,{method:'DELETE'})
