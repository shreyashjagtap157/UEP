import { request, type PageResult } from './api-core'
export type AssignmentStatus='DRAFT'|'PUBLISHED'|'CLOSED'|'ARCHIVED'
export type SubmissionStatus='DRAFT'|'SUBMITTED'|'RESUBMITTED'|'WITHDRAWN'
export type SubmissionGradeStatus='UNGRADED'|'GRADED'|'RETURNED'
export interface AssignmentView{id:string;title:string;instructions?:string;status:AssignmentStatus;maxPoints:number;weightBasisPoints:number;dueAt:string;version:number}
export interface AssignmentSubmission{id:string;assignmentId:string;attemptNumber:number;status:SubmissionStatus;gradeStatus:SubmissionGradeStatus;textBody?:string;resourceId?:string;submittedAt?:string;awardedPoints?:number;graderFeedback?:string;rubricScoresJson?:string;late?:boolean;version:number}
export interface GradebookEntry{assignmentId:string;title:string;maxPoints:number;weightBasisPoints:number;awardedPoints?:number;status:AssignmentStatus}
export interface GradebookView{batchId:string;membershipId:string;weightedPoints:number;completedAssignments:number;totalAssignments:number;entries:GradebookEntry[]}
export const fetchAssignments=()=>request<PageResult<AssignmentView>>('/api/v1/assignments?size=100')
export const fetchMyAssignmentSubmissions=()=>request<PageResult<AssignmentSubmission>>('/api/v1/assignments/my-submissions?size=100')
export const createAssignment=(input:{title:string;instructions?:string;maxPoints:number;weightBasisPoints:number;dueAt:string})=>request<AssignmentView>('/api/v1/assignments',{method:'POST',body:JSON.stringify(input)})
export const updateAssignmentStatus=(id:string,status:AssignmentStatus,expectedVersion:number)=>request<AssignmentView>(`/api/v1/assignments/${id}/status`,{method:'POST',body:JSON.stringify({status,expectedVersion})})
export const assignAssignmentToBatch=(id:string,batchId:string)=>request<AssignmentView>(`/api/v1/assignments/${id}/batches/${batchId}`,{method:'POST'})
export const saveAssignmentDraft=(id:string,textBody?:string,resourceId?:string)=>request<AssignmentSubmission>(`/api/v1/assignments/${id}/my-submission`,{method:'PUT',body:JSON.stringify({textBody,resourceId})})
export const submitAssignment=(id:string,textBody?:string,resourceId?:string)=>request<AssignmentSubmission>(`/api/v1/assignments/${id}/my-submission`,{method:'POST',body:JSON.stringify({textBody,resourceId})})
export const fetchAssignmentSubmissions=(id:string)=>request<PageResult<AssignmentSubmission>>(`/api/v1/assignments/${id}/submissions?size=100`)
export const gradeAssignmentSubmission=(id:string,awardedPoints:number,feedback:string,rubricScoresJson:string,expectedVersion:number)=>request<AssignmentSubmission>(`/api/v1/assignment-submissions/${id}/grade`,{method:'PUT',body:JSON.stringify({awardedPoints,feedback,rubricScoresJson,expectedVersion})})
export const fetchMyGradebook=(batchId:string)=>request<GradebookView>(`/api/v1/gradebook/me/${batchId}`)
