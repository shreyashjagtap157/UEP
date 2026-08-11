import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { UseQueryResult } from '@tanstack/react-query'
import {
  createEnrollment,
  createTeacherAssignment,
  fetchBatches,
  fetchCourses,
  fetchCurriculumModules,
  fetchEnrollments,
  fetchMembers,
  fetchPrograms,
  fetchSubjects,
  fetchTeacherAssignments,
} from '../platform/api'
import type { CurrentIdentity, EnrollmentView, PageResult, PermissionKey, TeacherAssignmentRole, TeacherAssignmentView } from '../platform/api'

type Tab = 'enrollments' | 'teaching'

export function EnrollmentWorkspace({ me }: { me: CurrentIdentity }) {
  const [tab, setTab] = useState<Tab>('enrollments')
  const canViewEnrollments = has(me, 'ENROLLMENTS_VIEW')
  const canViewTeaching = has(me, 'TEACHING_ASSIGNMENTS_VIEW')
  const enrollments = useQuery({ queryKey: ['enrollments'], queryFn: fetchEnrollments, enabled: canViewEnrollments })
  const teaching = useQuery({ queryKey: ['teacher-assignments'], queryFn: fetchTeacherAssignments, enabled: canViewTeaching })
  const members = useQuery({ queryKey: ['members'], queryFn: fetchMembers, enabled: has(me, 'USERS_VIEW') })
  const programs = useQuery({ queryKey: ['programs'], queryFn: fetchPrograms, enabled: has(me, 'ACADEMICS_VIEW') })
  const courses = useQuery({ queryKey: ['courses'], queryFn: fetchCourses, enabled: has(me, 'CURRICULUM_VIEW') })
  const subjects = useQuery({ queryKey: ['subjects'], queryFn: fetchSubjects, enabled: has(me, 'CURRICULUM_VIEW') })
  const modules = useQuery({ queryKey: ['curriculum-modules'], queryFn: fetchCurriculumModules, enabled: has(me, 'CURRICULUM_VIEW') })
  const batches = useQuery({ queryKey: ['batches'], queryFn: fetchBatches, enabled: has(me, 'ENROLLMENTS_VIEW') })

  return <div className="content-stack">
    <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Academic operations</p><h3>Enrollment & teaching</h3></div></div><div className="tab-row" role="tablist">
      {canViewEnrollments && <button role="tab" aria-selected={tab === 'enrollments'} className={tab === 'enrollments' ? 'tab-button active' : 'tab-button'} onClick={() => setTab('enrollments')}>Enrollments<span>{enrollments.data?.totalElements ?? '—'}</span></button>}
      {canViewTeaching && <button role="tab" aria-selected={tab === 'teaching'} className={tab === 'teaching' ? 'tab-button active' : 'tab-button'} onClick={() => setTab('teaching')}>Teaching<span>{teaching.data?.totalElements ?? '—'}</span></button>}
    </div></section>
    {tab === 'enrollments' && canViewEnrollments && <EnrollmentPanel me={me} members={members.data?.items ?? []} programs={programs.data?.items ?? []} courses={courses.data?.items ?? []} batches={batches.data?.items ?? []} data={enrollments} />}
    {tab === 'teaching' && canViewTeaching && <TeachingPanel me={me} members={members.data?.items ?? []} programs={programs.data?.items ?? []} courses={courses.data?.items ?? []} subjects={subjects.data?.items ?? []} modules={modules.data?.items ?? []} batches={batches.data?.items ?? []} data={teaching} />}
  </div>
}

function EnrollmentPanel({ me, members, programs, courses, batches, data }: {
  me: CurrentIdentity
  members: Array<{ membershipId: string; displayName: string }>
  programs: Array<{ id: string; displayName: string }>
  courses: Array<{ id: string; displayName: string }>
  batches: Array<{ id: string; displayName: string }>
  data: UseQueryResult<PageResult<EnrollmentView>, Error>
}) {
  const queryClient = useQueryClient()
  const [targetType, setTargetType] = useState<'PROGRAM' | 'COURSE' | 'BATCH'>('BATCH')
  const mutation = useMutation({ mutationFn: createEnrollment, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['enrollments'] }) })
  const options = targetType === 'PROGRAM' ? programs : targetType === 'COURSE' ? courses : batches
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const value = new FormData(form); const targetId = text(value, 'targetId'); const externalReference = optional(value, 'externalReference')
    const target = targetType === 'PROGRAM' ? { programId: targetId } : targetType === 'COURSE' ? { courseId: targetId } : { batchId: targetId }
    mutation.mutate({ membershipId: text(value, 'membershipId'), ...target, ...(externalReference ? { externalReference } : {}) }, { onSuccess: () => form.reset() })
  }
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Learners</p><h3>Enrollment register</h3></div><span className="count-badge">{data.data?.totalElements ?? 0}</span></div>
    {has(me, 'ENROLLMENTS_MANAGE') && <form className="form-grid compact-form" onSubmit={submit}><label>Learner<select name="membershipId" required><option value="">Select person</option>{members.map(item => <option key={item.membershipId} value={item.membershipId}>{item.displayName}</option>)}</select></label><label>Target type<select value={targetType} onChange={event => setTargetType(event.target.value as typeof targetType)}><option value="PROGRAM">Program</option><option value="COURSE">Course</option><option value="BATCH">Batch</option></select></label><label>Target<select name="targetId" required><option value="">Select target</option>{options.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label>External reference<input name="externalReference" maxLength={160} /></label><div className="form-actions"><button className="primary-button" disabled={mutation.isPending}>Enroll learner</button></div>{mutation.isError && <p className="error-text" role="alert">{mutation.error.message}</p>}</form>}
    {data.isPending ? <p className="muted">Loading enrollment…</p> : data.isError ? <p className="error-text">{data.error.message}</p> : <div className="table-wrap"><table><thead><tr><th>Learner</th><th>Target</th><th>Status</th><th>Enrolled</th></tr></thead><tbody>{data.data.items.map(item => <tr key={item.id}><td><strong>{item.displayName ?? shortId(item.membershipId)}</strong></td><td>{targetName(item, programs, courses, batches)}</td><td>{item.status}</td><td>{new Date(item.enrolledAt).toLocaleDateString()}</td></tr>)}</tbody></table></div>}
  </section>
}

function TeachingPanel({ me, members, programs, courses, subjects, modules, batches, data }: {
  me: CurrentIdentity
  members: Array<{ membershipId: string; displayName: string }>
  programs: Array<{ id: string; displayName: string }>
  courses: Array<{ id: string; displayName: string }>
  subjects: Array<{ id: string; displayName: string }>
  modules: Array<{ id: string; displayName: string }>
  batches: Array<{ id: string; displayName: string }>
  data: UseQueryResult<PageResult<TeacherAssignmentView>, Error>
}) {
  const queryClient = useQueryClient()
  const [scopeType, setScopeType] = useState<'PROGRAM' | 'COURSE' | 'SUBJECT' | 'MODULE' | 'BATCH'>('BATCH')
  const mutation = useMutation({ mutationFn: createTeacherAssignment, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['teacher-assignments'] }) })
  const options = scopeType === 'PROGRAM' ? programs : scopeType === 'COURSE' ? courses : scopeType === 'SUBJECT' ? subjects : scopeType === 'MODULE' ? modules : batches
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const value = new FormData(form); const scopeId = text(value, 'scopeId'); const startsOn = optional(value, 'startsOn')
    const scope = scopeType === 'PROGRAM' ? { programId: scopeId } : scopeType === 'COURSE' ? { courseId: scopeId } : scopeType === 'SUBJECT' ? { subjectId: scopeId } : scopeType === 'MODULE' ? { moduleId: scopeId } : { batchId: scopeId }
    mutation.mutate({ membershipId: text(value, 'membershipId'), assignmentRole: text(value, 'assignmentRole') as TeacherAssignmentRole, ...scope, ...(startsOn ? { startsOn } : {}) }, { onSuccess: () => form.reset() })
  }
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Faculty</p><h3>Teaching assignments</h3></div><span className="count-badge">{data.data?.totalElements ?? 0}</span></div>
    {has(me, 'TEACHING_ASSIGNMENTS_MANAGE') && <form className="form-grid compact-form" onSubmit={submit}><label>Person<select name="membershipId" required><option value="">Select person</option>{members.map(item => <option key={item.membershipId} value={item.membershipId}>{item.displayName}</option>)}</select></label><label>Responsibility<select name="assignmentRole" defaultValue="TEACHER"><option value="LEAD_TEACHER">Lead teacher</option><option value="TEACHER">Teacher</option><option value="TEACHING_ASSISTANT">Teaching assistant</option><option value="MENTOR">Mentor</option><option value="EVALUATOR">Evaluator</option></select></label><label>Scope type<select value={scopeType} onChange={event => setScopeType(event.target.value as typeof scopeType)}><option value="PROGRAM">Program</option><option value="COURSE">Course</option><option value="SUBJECT">Subject</option><option value="MODULE">Module</option><option value="BATCH">Batch</option></select></label><label>Scope<select name="scopeId" required><option value="">Select scope</option>{options.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label>Starts<input name="startsOn" type="date" /></label><div className="form-actions"><button className="primary-button" disabled={mutation.isPending}>Assign</button></div>{mutation.isError && <p className="error-text" role="alert">{mutation.error.message}</p>}</form>}
    {data.isPending ? <p className="muted">Loading assignments…</p> : data.isError ? <p className="error-text">{data.error.message}</p> : <div className="table-wrap"><table><thead><tr><th>Person</th><th>Scope</th><th>Responsibility</th><th>Status</th></tr></thead><tbody>{data.data.items.map(item => <tr key={item.id}><td><strong>{item.displayName ?? shortId(item.membershipId)}</strong></td><td>{assignmentName(item, programs, courses, subjects, modules, batches)}</td><td>{humanize(item.assignmentRole)}</td><td>{item.status}</td></tr>)}</tbody></table></div>}
  </section>
}

function has(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) }
function text(data: FormData, key: string) { return String(data.get(key) ?? '').trim() }
function optional(data: FormData, key: string) { const value = text(data, key); return value || undefined }
function shortId(value: string) { return value.slice(0, 8) }
function humanize(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()) }
function targetName(item: { programId?: string; courseId?: string; batchId?: string }, programs: Array<{ id: string; displayName: string }>, courses: Array<{ id: string; displayName: string }>, batches: Array<{ id: string; displayName: string }>) { if (item.batchId) return batches.find(value => value.id === item.batchId)?.displayName ?? `Batch ${shortId(item.batchId)}`; if (item.courseId) return courses.find(value => value.id === item.courseId)?.displayName ?? `Course ${shortId(item.courseId)}`; if (item.programId) return programs.find(value => value.id === item.programId)?.displayName ?? `Program ${shortId(item.programId)}`; return '—' }
function assignmentName(item: { programId?: string; courseId?: string; subjectId?: string; moduleId?: string; batchId?: string }, programs: Array<{ id: string; displayName: string }>, courses: Array<{ id: string; displayName: string }>, subjects: Array<{ id: string; displayName: string }>, modules: Array<{ id: string; displayName: string }>, batches: Array<{ id: string; displayName: string }>) { if (item.batchId) return batches.find(value => value.id === item.batchId)?.displayName ?? `Batch ${shortId(item.batchId)}`; if (item.moduleId) return modules.find(value => value.id === item.moduleId)?.displayName ?? `Module ${shortId(item.moduleId)}`; if (item.subjectId) return subjects.find(value => value.id === item.subjectId)?.displayName ?? `Subject ${shortId(item.subjectId)}`; if (item.courseId) return courses.find(value => value.id === item.courseId)?.displayName ?? `Course ${shortId(item.courseId)}`; if (item.programId) return programs.find(value => value.id === item.programId)?.displayName ?? `Program ${shortId(item.programId)}`; return '—' }
