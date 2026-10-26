import { request } from './api-core'
export interface FinanceFee{id:string;name:string;amount:number;currency:string;status:'ACTIVE'|'INACTIVE';version:number}
export interface FinanceInstallment{id:string;sequenceNo:number;amount:number;dueOn:string;status:string;version:number}
export interface FinanceInvoice{id:string;membershipId:string;invoiceNumber:string;totalAmount:number;paidAmount:number;currency:string;status:string;issuedOn?:string;dueOn:string;version:number;installments:FinanceInstallment[]}
export interface FinanceInvoicePage{items:FinanceInvoice[];page:number;size:number;totalElements:number}
export const fetchFinanceFees=()=>request<FinanceFee[]>('/api/v1/finance/fees')
export const createFinanceFee=(input:{name:string;amount:number;currency:string;status:'ACTIVE'|'INACTIVE'})=>request<FinanceFee>('/api/v1/finance/fees',{method:'POST',body:JSON.stringify({...input,expectedVersion:0})})
export const fetchFinanceInvoices=()=>request<FinanceInvoicePage>('/api/v1/finance/invoices?size=100')
export const createFinanceInvoice=(input:{membershipId:string;totalAmount:number;currency:string;dueOn:string})=>request<FinanceInvoice>('/api/v1/finance/invoices',{method:'POST',body:JSON.stringify(input)})
export const issueFinanceInvoice=(id:string,expectedVersion:number)=>request<FinanceInvoice>(`/api/v1/finance/invoices/${id}/issue`,{method:'POST',body:JSON.stringify({expectedVersion})})
export const recordFinancePayment=(id:string,input:{amount:number;currency:string;settlementReference:string;confirmSettlement:true})=>request(`/api/v1/finance/invoices/${id}/payments`,{method:'POST',body:JSON.stringify(input)})
export interface AnalyticsOverview{from:string;to:string;activeMemberships:number;enrollments:number;attendance:{present:number;partial:number;absent:number;excused:number;presentSeconds:number;presentRatePercent:number};assessment:{attempts:number;submitted:number;published:number;passed:number;averagePercent:number};recording:{count:number;totalSeconds:number;totalBytes:number;ready:number;archived:number;failed:number};finance:{invoiced:number;paid:number;outstanding:number;paymentCount:number};usage:{items:{limitKey:string;hardLimit:number;consumed:number}[]}}
export interface AnalyticsForecast{generatedAt:string;historyDays:number;recentBytes:number;recentSeconds:number;dailyBytes:number;dailyMinutes:number;projected30DayBytes:number;projected365DayBytes:number}
const rangeQuery=(from?:string,to?:string)=>{const p=new URLSearchParams();if(from)p.set('from',from);if(to)p.set('to',to);const q=p.toString();return q?`?${q}`:''}
export const getAnalyticsOverview=(from?:string,to?:string)=>request<AnalyticsOverview>(`/api/v1/analytics/overview${rangeQuery(from,to)}`)
export const getAnalyticsForecast=()=>request<AnalyticsForecast>('/api/v1/analytics/forecast')
export const exportAnalytics=async(report:string,from?:string,to?:string)=>{const response=await fetch(`/api/v1/analytics/exports/${encodeURIComponent(report)}${rangeQuery(from,to)}`);if(!response.ok)throw new Error(await response.text());return response.text()}
export interface LearningOutcomeView{id:string;code:string;name:string;description?:string;status:string;version:number}
export interface LearningPathView{id:string;code:string;name:string;description?:string;status:string;version:number}
export interface CredentialTemplateView{id:string;code:string;name:string;description?:string;credentialType:string;version:number}
export interface CredentialView{id:string;templateId:string;membershipId:string;verificationCode:string;verificationUrl:string;issuedAt:string;status:string;issuerSubject:string;reason:string;qrPayload:string;version:number}
export interface CredentialVerification{verificationCode:string;templateName:string;recipientName:string;issuedAt:string;status:string;issuerSubject:string;reason:string}
export interface MentorshipView{id:string;mentorMembershipId:string;menteeMembershipId:string;goal:string;status:string;createdAt:string;updatedAt?:string;version:number}
export interface SurveyView{id:string;title:string;description?:string;anonymous:boolean;opensAt?:string;closesAt?:string;status:string;version:number}
export interface FeedbackView{id:string;targetType:string;targetId:string;authorMembershipId:string;rating:number;comments:string;createdAt:string;status:string}
export const fetchLearningOutcomes=()=>request<LearningOutcomeView[]>('/api/v1/learning/outcomes')
export const createLearningOutcome=(input:{code:string;name:string;description?:string})=>request<LearningOutcomeView>('/api/v1/learning/outcomes',{method:'POST',body:JSON.stringify(input)})
export const fetchCredentialTemplates=()=>request<CredentialTemplateView[]>('/api/v1/credentials/templates')
export const createCredentialTemplate=(input:{code:string;name:string;description?:string;credentialType:string})=>request<CredentialTemplateView>('/api/v1/credentials/templates',{method:'POST',body:JSON.stringify(input)})
export const issueCredential=(input:{templateId:string;membershipId:string;reason:string;sourceId?:string})=>request<CredentialView>('/api/v1/credentials',{method:'POST',body:JSON.stringify(input)})
export const fetchMentoring=()=>request<MentorshipView[]>('/api/v1/engagement/mentoring')
export const createMentorship=(input:{mentorMembershipId:string;menteeMembershipId:string;goal:string})=>request<MentorshipView>('/api/v1/engagement/mentoring',{method:'POST',body:JSON.stringify(input)})
export const fetchSurveys=()=>request<SurveyView[]>('/api/v1/engagement/surveys')
export const createSurvey=(input:{title:string;description?:string;anonymous:boolean;opensAt?:string;closesAt?:string})=>request<SurveyView>('/api/v1/engagement/surveys',{method:'POST',body:JSON.stringify(input)})
export const submitFeedback=(input:{targetId:string;targetType:string;rating:number;comments:string})=>request<void>('/api/v1/engagement/feedback',{method:'POST',body:JSON.stringify(input)})
