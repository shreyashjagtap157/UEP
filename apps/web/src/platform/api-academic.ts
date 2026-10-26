import { request, type PageResult } from './api-core'
export interface AcademicPeriodView { id:string; code:string; displayName:string; startsOn:string; endsOn:string; status:'PLANNED'|'ACTIVE'|'CLOSED'|'ARCHIVED'; createdAt:string; version:number }
export interface ProgramView { id:string; academicPeriodId?:string; code:string; displayName:string; description?:string; status:'ACTIVE'|'INACTIVE'|'ARCHIVED'; createdAt:string; version:number }
export interface CourseView { id:string; programId?:string; code:string; displayName:string; description?:string; status:'ACTIVE'|'INACTIVE'|'ARCHIVED'; createdAt:string; version:number }
export interface SubjectView { id:string; courseId?:string; code:string; displayName:string; description?:string; status:'ACTIVE'|'INACTIVE'|'ARCHIVED'; createdAt:string; version:number }
export interface CurriculumModuleView { id:string; programId?:string; courseId?:string; subjectId?:string; code:string; displayName:string; description?:string; sequenceNumber?:number; status:'ACTIVE'|'INACTIVE'|'ARCHIVED'; createdAt:string; version:number }
export interface BatchView { id:string; academicPeriodId?:string; programId?:string; courseId?:string; branchId?:string; code:string; displayName:string; sectionCode?:string; startsOn?:string; endsOn?:string; capacity?:number; status:'PLANNED'|'ACTIVE'|'CLOSED'|'ARCHIVED'; createdAt:string; version:number }
export interface EnrollmentView { id:string; membershipId:string; displayName?:string; programId?:string; courseId?:string; batchId?:string; status:'ENROLLED'|'COMPLETED'|'WITHDRAWN'|'CANCELLED'; enrolledAt:string; endedAt?:string; externalReference?:string; version:number }
export type TeacherAssignmentRole='LEAD_TEACHER'|'TEACHER'|'TEACHING_ASSISTANT'|'MENTOR'|'EVALUATOR'
export interface TeacherAssignmentView { id:string; membershipId:string; displayName?:string; programId?:string; courseId?:string; subjectId?:string; moduleId?:string; batchId?:string; assignmentRole:TeacherAssignmentRole; startsOn?:string; endsOn?:string; status:'ACTIVE'|'ENDED'; assignedAt:string; version:number }
export const fetchAcademicPeriods=()=>request<PageResult<AcademicPeriodView>>('/api/v1/academic-periods?size=100')
export const fetchPrograms=()=>request<PageResult<ProgramView>>('/api/v1/programs?size=100')
export const fetchCourses=()=>request<PageResult<CourseView>>('/api/v1/courses?size=100')
export const fetchSubjects=()=>request<PageResult<SubjectView>>('/api/v1/subjects?size=100')
export const fetchCurriculumModules=()=>request<PageResult<CurriculumModuleView>>('/api/v1/modules?size=100')
export const fetchBatches=()=>request<PageResult<BatchView>>('/api/v1/batches?size=100')
export const fetchEnrollments=()=>request<PageResult<EnrollmentView>>('/api/v1/enrollments?size=100')
export const fetchMyEnrollments=()=>request<PageResult<EnrollmentView>>('/api/v1/enrollments/me?size=100')
export const fetchTeacherAssignments=()=>request<PageResult<TeacherAssignmentView>>('/api/v1/teacher-assignments?size=100')
export const fetchMyTeacherAssignments=()=>request<PageResult<TeacherAssignmentView>>('/api/v1/teacher-assignments/me?size=100')
export const createAcademicPeriod=(input:{code:string;displayName:string;startsOn:string;endsOn:string})=>request<AcademicPeriodView>('/api/v1/academic-periods',{method:'POST',body:JSON.stringify(input)})
export const createProgram=(input:{academicPeriodId?:string;code:string;displayName:string;description?:string})=>request<ProgramView>('/api/v1/programs',{method:'POST',body:JSON.stringify(input)})
export const createCourse=(input:{programId?:string;code:string;displayName:string;description?:string})=>request<CourseView>('/api/v1/courses',{method:'POST',body:JSON.stringify(input)})
export const createSubject=(input:{courseId?:string;code:string;displayName:string;description?:string})=>request<SubjectView>('/api/v1/subjects',{method:'POST',body:JSON.stringify(input)})
export const createCurriculumModule=(input:{programId?:string;courseId?:string;subjectId?:string;code:string;displayName:string;description?:string;sequenceNumber?:number})=>request<CurriculumModuleView>('/api/v1/modules',{method:'POST',body:JSON.stringify(input)})
export const createBatch=(input:{academicPeriodId?:string;programId?:string;courseId?:string;branchId?:string;code:string;displayName:string;sectionCode?:string;startsOn?:string;endsOn?:string;capacity?:number})=>request<BatchView>('/api/v1/batches',{method:'POST',body:JSON.stringify(input)})
export const createEnrollment=(input:{membershipId:string;programId?:string;courseId?:string;batchId?:string;externalReference?:string})=>request<EnrollmentView>('/api/v1/enrollments',{method:'POST',body:JSON.stringify(input)})
export const createTeacherAssignment=(input:{membershipId:string;programId?:string;courseId?:string;subjectId?:string;moduleId?:string;batchId?:string;assignmentRole:TeacherAssignmentRole;startsOn?:string})=>request<TeacherAssignmentView>('/api/v1/teacher-assignments',{method:'POST',body:JSON.stringify(input)})
