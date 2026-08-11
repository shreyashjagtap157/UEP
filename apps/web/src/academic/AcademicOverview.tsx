import { useQuery } from '@tanstack/react-query'
import {
  fetchAcademicPeriods,
  fetchBatches,
  fetchCourses,
  fetchCurriculumModules,
  fetchMyEnrollments,
  fetchMyTeacherAssignments,
  fetchPrograms,
  fetchSubjects,
} from '../platform/api'
import type { CurrentIdentity, PermissionKey } from '../platform/api'

export function AcademicOverview({ me }: { me: CurrentIdentity }) {
  const canViewAcademics = has(me, 'ACADEMICS_VIEW')
  const canViewCurriculum = has(me, 'CURRICULUM_VIEW')
  const periods = useQuery({ queryKey: ['academic-periods'], queryFn: fetchAcademicPeriods, enabled: canViewAcademics })
  const programs = useQuery({ queryKey: ['programs'], queryFn: fetchPrograms, enabled: canViewAcademics })
  const courses = useQuery({ queryKey: ['courses'], queryFn: fetchCourses, enabled: canViewCurriculum })
  const subjects = useQuery({ queryKey: ['subjects'], queryFn: fetchSubjects, enabled: canViewCurriculum })
  const modules = useQuery({ queryKey: ['curriculum-modules'], queryFn: fetchCurriculumModules, enabled: canViewCurriculum })
  const batches = useQuery({ queryKey: ['batches'], queryFn: fetchBatches, enabled: has(me, 'ENROLLMENTS_VIEW') })
  const myEnrollments = useQuery({ queryKey: ['my-enrollments'], queryFn: fetchMyEnrollments })
  const myTeaching = useQuery({ queryKey: ['my-teacher-assignments'], queryFn: fetchMyTeacherAssignments })

  const admin = has(me, 'ACADEMICS_MANAGE') || has(me, 'CURRICULUM_MANAGE') || has(me, 'ENROLLMENTS_MANAGE')
  const learner = me.roles.includes('Student') || (myEnrollments.data?.totalElements ?? 0) > 0
  const teacher = me.roles.some(role => ['Teacher', 'Teaching Assistant', 'Mentor', 'Evaluator'].includes(role)) || (myTeaching.data?.totalElements ?? 0) > 0

  return (
    <div className="content-stack">
      {admin && (
        <section className="panel">
          <div className="panel-heading"><div><p className="eyebrow">Academic administration</p><h3>Academic core</h3></div></div>
          <div className="metric-grid">
            <Metric label="Periods" value={periods.data?.totalElements} />
            <Metric label="Programs" value={programs.data?.totalElements} />
            <Metric label="Courses" value={courses.data?.totalElements} />
            <Metric label="Subjects" value={subjects.data?.totalElements} />
            <Metric label="Modules" value={modules.data?.totalElements} />
            <Metric label="Batches" value={batches.data?.totalElements} />
          </div>
        </section>
      )}

      {learner && (
        <section className="panel">
          <div className="panel-heading"><div><p className="eyebrow">Learner dashboard</p><h3>My learning</h3></div><span className="count-badge">{myEnrollments.data?.totalElements ?? 0}</span></div>
          {myEnrollments.isPending ? <p className="muted">Loading enrollment…</p> : myEnrollments.isError ? <p className="error-text">{myEnrollments.error.message}</p> : (
            <div className="card-grid">
              {myEnrollments.data.items.length === 0 ? <p className="muted">No active or historical enrollment yet.</p> : myEnrollments.data.items.map(item => (
                <article className="mini-card" key={item.id}>
                  <div className="mini-card-title"><strong>{targetName(item.programId, item.courseId, item.batchId, programs.data?.items, courses.data?.items, batches.data?.items)}</strong><span>{item.status}</span></div>
                  <p>Enrolled {new Date(item.enrolledAt).toLocaleDateString()}</p>
                </article>
              ))}
            </div>
          )}
        </section>
      )}

      {teacher && (
        <section className="panel">
          <div className="panel-heading"><div><p className="eyebrow">Teacher dashboard</p><h3>My teaching</h3></div><span className="count-badge">{myTeaching.data?.totalElements ?? 0}</span></div>
          {myTeaching.isPending ? <p className="muted">Loading teaching assignments…</p> : myTeaching.isError ? <p className="error-text">{myTeaching.error.message}</p> : (
            <div className="card-grid">
              {myTeaching.data.items.length === 0 ? <p className="muted">No teaching assignments yet.</p> : myTeaching.data.items.map(item => (
                <article className="mini-card" key={item.id}>
                  <div className="mini-card-title"><strong>{assignmentTarget(item, programs.data?.items, courses.data?.items, subjects.data?.items, modules.data?.items, batches.data?.items)}</strong><span>{humanize(item.assignmentRole)}</span></div>
                  <p>{item.status}{item.startsOn ? ` · from ${item.startsOn}` : ''}</p>
                </article>
              ))}
            </div>
          )}
        </section>
      )}
    </div>
  )
}

function Metric({ label, value }: { label: string; value: number | undefined }) {
  return <div className="metric"><span>{label}</span><strong>{value ?? '—'}</strong></div>
}

function targetName(
  programId?: string,
  courseId?: string,
  batchId?: string,
  programs?: Array<{ id: string; displayName: string }>,
  courses?: Array<{ id: string; displayName: string }>,
  batches?: Array<{ id: string; displayName: string }>,
) {
  if (batchId) return batches?.find(item => item.id === batchId)?.displayName ?? `Batch ${shortId(batchId)}`
  if (courseId) return courses?.find(item => item.id === courseId)?.displayName ?? `Course ${shortId(courseId)}`
  if (programId) return programs?.find(item => item.id === programId)?.displayName ?? `Program ${shortId(programId)}`
  return 'Academic enrollment'
}

function assignmentTarget(
  item: { programId?: string; courseId?: string; subjectId?: string; moduleId?: string; batchId?: string },
  programs?: Array<{ id: string; displayName: string }>,
  courses?: Array<{ id: string; displayName: string }>,
  subjects?: Array<{ id: string; displayName: string }>,
  modules?: Array<{ id: string; displayName: string }>,
  batches?: Array<{ id: string; displayName: string }>,
) {
  if (item.batchId) return batches?.find(value => value.id === item.batchId)?.displayName ?? `Batch ${shortId(item.batchId)}`
  if (item.moduleId) return modules?.find(value => value.id === item.moduleId)?.displayName ?? `Module ${shortId(item.moduleId)}`
  if (item.subjectId) return subjects?.find(value => value.id === item.subjectId)?.displayName ?? `Subject ${shortId(item.subjectId)}`
  if (item.courseId) return courses?.find(value => value.id === item.courseId)?.displayName ?? `Course ${shortId(item.courseId)}`
  if (item.programId) return programs?.find(value => value.id === item.programId)?.displayName ?? `Program ${shortId(item.programId)}`
  return 'Teaching assignment'
}

function has(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) }
function shortId(value: string) { return value.slice(0, 8) }
function humanize(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()) }
