import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { UseQueryResult } from '@tanstack/react-query'
import {
  createAcademicPeriod,
  createBatch,
  createCourse,
  createCurriculumModule,
  createProgram,
  createSubject,
  fetchAcademicPeriods,
  fetchBatches,
  fetchBranches,
  fetchCourses,
  fetchCurriculumModules,
  fetchPrograms,
  fetchSubjects,
} from '../platform/api'
import type { AcademicPeriodView, BatchView, CourseView, CurrentIdentity, CurriculumModuleView, PageResult, PermissionKey, ProgramView, SubjectView } from '../platform/api'

type CatalogTab = 'periods' | 'programs' | 'courses' | 'subjects' | 'modules' | 'batches'

export function AcademicWorkspace({ me }: { me: CurrentIdentity }) {
  const [tab, setTab] = useState<CatalogTab>('periods')
  const periods = useQuery({ queryKey: ['academic-periods'], queryFn: fetchAcademicPeriods, enabled: has(me, 'ACADEMICS_VIEW') })
  const programs = useQuery({ queryKey: ['programs'], queryFn: fetchPrograms, enabled: has(me, 'ACADEMICS_VIEW') })
  const courses = useQuery({ queryKey: ['courses'], queryFn: fetchCourses, enabled: has(me, 'CURRICULUM_VIEW') })
  const subjects = useQuery({ queryKey: ['subjects'], queryFn: fetchSubjects, enabled: has(me, 'CURRICULUM_VIEW') })
  const modules = useQuery({ queryKey: ['curriculum-modules'], queryFn: fetchCurriculumModules, enabled: has(me, 'CURRICULUM_VIEW') })
  const batches = useQuery({ queryKey: ['batches'], queryFn: fetchBatches, enabled: has(me, 'ENROLLMENTS_VIEW') })
  const branches = useQuery({ queryKey: ['branches'], queryFn: fetchBranches, enabled: has(me, 'BRANCHES_VIEW') })

  const tabs: Array<{ id: CatalogTab; label: string; visible: boolean; count?: number }> = [
    { id: 'periods', label: 'Periods', visible: has(me, 'ACADEMICS_VIEW'), count: periods.data?.totalElements },
    { id: 'programs', label: 'Programs', visible: has(me, 'ACADEMICS_VIEW'), count: programs.data?.totalElements },
    { id: 'courses', label: 'Courses', visible: has(me, 'CURRICULUM_VIEW'), count: courses.data?.totalElements },
    { id: 'subjects', label: 'Subjects', visible: has(me, 'CURRICULUM_VIEW'), count: subjects.data?.totalElements },
    { id: 'modules', label: 'Modules', visible: has(me, 'CURRICULUM_VIEW'), count: modules.data?.totalElements },
    { id: 'batches', label: 'Batches', visible: has(me, 'ENROLLMENTS_VIEW'), count: batches.data?.totalElements },
  ]

  return (
    <div className="content-stack">
      <section className="panel">
        <div className="panel-heading"><div><p className="eyebrow">Academic model</p><h3>Structure & curriculum</h3></div></div>
        <div className="tab-row" role="tablist" aria-label="Academic catalog sections">
          {tabs.filter(item => item.visible).map(item => (
            <button key={item.id} role="tab" aria-selected={tab === item.id} className={tab === item.id ? 'tab-button active' : 'tab-button'} onClick={() => setTab(item.id)}>
              {item.label}<span>{item.count ?? '—'}</span>
            </button>
          ))}
        </div>
      </section>

      {tab === 'periods' && <Periods me={me} data={periods} />}
      {tab === 'programs' && <Programs me={me} data={programs} periods={periods.data?.items ?? []} />}
      {tab === 'courses' && <Courses me={me} data={courses} programs={programs.data?.items ?? []} />}
      {tab === 'subjects' && <Subjects me={me} data={subjects} courses={courses.data?.items ?? []} />}
      {tab === 'modules' && <Modules me={me} data={modules} programs={programs.data?.items ?? []} courses={courses.data?.items ?? []} subjects={subjects.data?.items ?? []} />}
      {tab === 'batches' && <Batches me={me} data={batches} periods={periods.data?.items ?? []} programs={programs.data?.items ?? []} courses={courses.data?.items ?? []} branches={branches.data?.items ?? []} />}
    </div>
  )
}

function Periods({ me, data }: { me: CurrentIdentity; data: UseQueryResult<PageResult<AcademicPeriodView>, Error> }) {
  const queryClient = useQueryClient()
  const mutation = useMutation({ mutationFn: createAcademicPeriod, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['academic-periods'] }) })
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const value = new FormData(form)
    mutation.mutate({ code: text(value, 'code'), displayName: text(value, 'displayName'), startsOn: text(value, 'startsOn'), endsOn: text(value, 'endsOn') }, { onSuccess: () => form.reset() })
  }
  return <CatalogPanel kicker="Time" title="Academic periods" count={data.data?.totalElements} loading={data.isPending} error={data.error}>
    {has(me, 'ACADEMICS_MANAGE') && <form className="form-grid compact-form" onSubmit={submit}><label>Code<input name="code" required maxLength={64} /></label><label>Name<input name="displayName" required maxLength={200} /></label><label>Starts<input name="startsOn" required type="date" /></label><label>Ends<input name="endsOn" required type="date" /></label><div className="form-actions"><button className="primary-button" disabled={mutation.isPending}>Create period</button></div>{mutation.isError && <MutationError error={mutation.error} />}</form>}
    <CatalogCards<AcademicPeriodView> items={data.data?.items ?? []} render={item => <><div className="mini-card-title"><strong>{item.displayName}</strong><span>{item.status}</span></div><p><code>{item.code}</code> · {item.startsOn} → {item.endsOn}</p></>} />
  </CatalogPanel>
}

function Programs({ me, data, periods }: { me: CurrentIdentity; data: UseQueryResult<PageResult<ProgramView>, Error>; periods: Array<{ id: string; displayName: string }> }) {
  const queryClient = useQueryClient()
  const mutation = useMutation({ mutationFn: createProgram, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['programs'] }) })
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); const form = event.currentTarget; const value = new FormData(form); const academicPeriodId = optional(value, 'academicPeriodId'); const description = optional(value, 'description')
    mutation.mutate({ code: text(value, 'code'), displayName: text(value, 'displayName'), ...(academicPeriodId ? { academicPeriodId } : {}), ...(description ? { description } : {}) }, { onSuccess: () => form.reset() })
  }
  return <CatalogPanel kicker="Hierarchy" title="Programs" count={data.data?.totalElements} loading={data.isPending} error={data.error}>
    {has(me, 'ACADEMICS_MANAGE') && <form className="form-grid compact-form" onSubmit={submit}><label>Period<select name="academicPeriodId"><option value="">No fixed period</option>{periods.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label>Code<input name="code" required maxLength={64} /></label><label>Name<input name="displayName" required maxLength={200} /></label><label className="wide">Description<textarea name="description" maxLength={2000} rows={2} /></label><div className="form-actions"><button className="primary-button" disabled={mutation.isPending}>Create program</button></div>{mutation.isError && <MutationError error={mutation.error} />}</form>}
    <CatalogCards<ProgramView> items={data.data?.items ?? []} render={item => <><div className="mini-card-title"><strong>{item.displayName}</strong><span>{item.status}</span></div><p><code>{item.code}</code>{item.academicPeriodId ? ` · ${periods.find(period => period.id === item.academicPeriodId)?.displayName ?? 'Period'}` : ' · reusable'}</p></>} />
  </CatalogPanel>
}

function Courses({ me, data, programs }: { me: CurrentIdentity; data: UseQueryResult<PageResult<CourseView>, Error>; programs: Array<{ id: string; displayName: string }> }) {
  const queryClient = useQueryClient(); const mutation = useMutation({ mutationFn: createCourse, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['courses'] }) })
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = event.currentTarget; const value = new FormData(form); const programId = optional(value, 'programId'); const description = optional(value, 'description'); mutation.mutate({ code: text(value, 'code'), displayName: text(value, 'displayName'), ...(programId ? { programId } : {}), ...(description ? { description } : {}) }, { onSuccess: () => form.reset() }) }
  return <CatalogPanel kicker="Curriculum" title="Courses" count={data.data?.totalElements} loading={data.isPending} error={data.error}>{has(me, 'CURRICULUM_MANAGE') && <form className="form-grid compact-form" onSubmit={submit}><label>Program<select name="programId"><option value="">Standalone course</option>{programs.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label>Code<input name="code" required /></label><label>Name<input name="displayName" required /></label><label className="wide">Description<textarea name="description" rows={2} /></label><div className="form-actions"><button className="primary-button" disabled={mutation.isPending}>Create course</button></div>{mutation.isError && <MutationError error={mutation.error} />}</form>}<CatalogCards<CourseView> items={data.data?.items ?? []} render={item => <><div className="mini-card-title"><strong>{item.displayName}</strong><span>{item.status}</span></div><p><code>{item.code}</code>{item.programId ? ` · ${programs.find(program => program.id === item.programId)?.displayName ?? 'Program'}` : ' · standalone'}</p></>} /></CatalogPanel>
}

function Subjects({ me, data, courses }: { me: CurrentIdentity; data: UseQueryResult<PageResult<SubjectView>, Error>; courses: Array<{ id: string; displayName: string }> }) {
  const queryClient = useQueryClient(); const mutation = useMutation({ mutationFn: createSubject, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['subjects'] }) })
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = event.currentTarget; const value = new FormData(form); const courseId = optional(value, 'courseId'); const description = optional(value, 'description'); mutation.mutate({ code: text(value, 'code'), displayName: text(value, 'displayName'), ...(courseId ? { courseId } : {}), ...(description ? { description } : {}) }, { onSuccess: () => form.reset() }) }
  return <CatalogPanel kicker="Curriculum" title="Subjects" count={data.data?.totalElements} loading={data.isPending} error={data.error}>{has(me, 'CURRICULUM_MANAGE') && <form className="form-grid compact-form" onSubmit={submit}><label>Course<select name="courseId"><option value="">Standalone subject</option>{courses.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label>Code<input name="code" required /></label><label>Name<input name="displayName" required /></label><label className="wide">Description<textarea name="description" rows={2} /></label><div className="form-actions"><button className="primary-button" disabled={mutation.isPending}>Create subject</button></div>{mutation.isError && <MutationError error={mutation.error} />}</form>}<CatalogCards<SubjectView> items={data.data?.items ?? []} render={item => <><div className="mini-card-title"><strong>{item.displayName}</strong><span>{item.status}</span></div><p><code>{item.code}</code>{item.courseId ? ` · ${courses.find(course => course.id === item.courseId)?.displayName ?? 'Course'}` : ' · standalone'}</p></>} /></CatalogPanel>
}

function Modules({ me, data, programs, courses, subjects }: { me: CurrentIdentity; data: UseQueryResult<PageResult<CurriculumModuleView>, Error>; programs: Array<{ id: string; displayName: string }>; courses: Array<{ id: string; displayName: string }>; subjects: Array<{ id: string; displayName: string }> }) {
  const queryClient = useQueryClient(); const mutation = useMutation({ mutationFn: createCurriculumModule, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['curriculum-modules'] }) })
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = event.currentTarget; const value = new FormData(form); const parentType = text(value, 'parentType'); const parentId = optional(value, 'parentId'); const description = optional(value, 'description'); const sequence = optional(value, 'sequenceNumber'); const parent = parentId && parentType === 'PROGRAM' ? { programId: parentId } : parentId && parentType === 'COURSE' ? { courseId: parentId } : parentId && parentType === 'SUBJECT' ? { subjectId: parentId } : {}; mutation.mutate({ code: text(value, 'code'), displayName: text(value, 'displayName'), ...parent, ...(description ? { description } : {}), ...(sequence ? { sequenceNumber: Number(sequence) } : {}) }, { onSuccess: () => form.reset() }) }
  const [parentType, setParentType] = useState('NONE'); const parentOptions = parentType === 'PROGRAM' ? programs : parentType === 'COURSE' ? courses : parentType === 'SUBJECT' ? subjects : []
  return <CatalogPanel kicker="Curriculum" title="Modules" count={data.data?.totalElements} loading={data.isPending} error={data.error}>{has(me, 'CURRICULUM_MANAGE') && <form className="form-grid compact-form" onSubmit={submit}><label>Parent type<select name="parentType" value={parentType} onChange={event => setParentType(event.target.value)}><option value="NONE">Standalone</option><option value="PROGRAM">Program</option><option value="COURSE">Course</option><option value="SUBJECT">Subject</option></select></label><label>Parent<select name="parentId" disabled={parentType === 'NONE'}><option value="">Select parent</option>{parentOptions.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label>Code<input name="code" required /></label><label>Name<input name="displayName" required /></label><label>Sequence<input name="sequenceNumber" type="number" min={1} /></label><label className="wide">Description<textarea name="description" rows={2} /></label><div className="form-actions"><button className="primary-button" disabled={mutation.isPending}>Create module</button></div>{mutation.isError && <MutationError error={mutation.error} />}</form>}<CatalogCards<CurriculumModuleView> items={data.data?.items ?? []} render={item => <><div className="mini-card-title"><strong>{item.displayName}</strong><span>{item.status}</span></div><p><code>{item.code}</code>{item.sequenceNumber ? ` · #${item.sequenceNumber}` : ''} · {moduleParent(item, programs, courses, subjects)}</p></>} /></CatalogPanel>
}

function Batches({ me, data, periods, programs, courses, branches }: { me: CurrentIdentity; data: UseQueryResult<PageResult<BatchView>, Error>; periods: Array<{ id: string; displayName: string }>; programs: Array<{ id: string; displayName: string }>; courses: Array<{ id: string; displayName: string }>; branches: Array<{ id: string; displayName: string }> }) {
  const queryClient = useQueryClient(); const mutation = useMutation({ mutationFn: createBatch, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['batches'] }) })
  function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); const form = event.currentTarget; const value = new FormData(form); const periodId = optional(value, 'academicPeriodId'); const programId = optional(value, 'programId'); const courseId = optional(value, 'courseId'); const branchId = optional(value, 'branchId'); const sectionCode = optional(value, 'sectionCode'); const startsOn = optional(value, 'startsOn'); const endsOn = optional(value, 'endsOn'); const capacity = optional(value, 'capacity'); mutation.mutate({ code: text(value, 'code'), displayName: text(value, 'displayName'), ...(periodId ? { academicPeriodId: periodId } : {}), ...(programId ? { programId } : {}), ...(courseId ? { courseId } : {}), ...(branchId ? { branchId } : {}), ...(sectionCode ? { sectionCode } : {}), ...(startsOn ? { startsOn } : {}), ...(endsOn ? { endsOn } : {}), ...(capacity ? { capacity: Number(capacity) } : {}) }, { onSuccess: () => form.reset() }) }
  return <CatalogPanel kicker="Cohorts" title="Batches & sections" count={data.data?.totalElements} loading={data.isPending} error={data.error}>{has(me, 'ENROLLMENTS_MANAGE') && <form className="form-grid compact-form" onSubmit={submit}><label>Period<select name="academicPeriodId"><option value="">Any period</option>{periods.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label>Program<select name="programId"><option value="">No program</option>{programs.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label>Course<select name="courseId"><option value="">No course</option>{courses.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label>Branch<select name="branchId"><option value="">No branch</option>{branches.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label><label>Code<input name="code" required /></label><label>Name<input name="displayName" required /></label><label>Section<input name="sectionCode" /></label><label>Starts<input name="startsOn" type="date" /></label><label>Ends<input name="endsOn" type="date" /></label><label>Capacity<input name="capacity" type="number" min={1} /></label><div className="form-actions"><button className="primary-button" disabled={mutation.isPending}>Create batch</button></div>{mutation.isError && <MutationError error={mutation.error} />}</form>}<CatalogCards<BatchView> items={data.data?.items ?? []} render={item => <><div className="mini-card-title"><strong>{item.displayName}</strong><span>{item.status}</span></div><p><code>{item.code}</code>{item.sectionCode ? ` · ${item.sectionCode}` : ''}{item.capacity ? ` · capacity ${item.capacity}` : ''}</p></>} /></CatalogPanel>
}

function CatalogPanel({ kicker, title, count, loading, error, children }: { kicker: string; title: string; count: number | undefined; loading: boolean; error: Error | null; children: ReactNode }) {
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">{kicker}</p><h3>{title}</h3></div><span className="count-badge">{count ?? 0}</span></div>{loading ? <p className="muted">Loading…</p> : error ? <p className="error-text">{error.message}</p> : children}</section>
}

function CatalogCards<T extends { id: string }>({ items, render }: { items: T[]; render: (item: T) => ReactNode }) {
  if (!items.length) return <p className="muted">Nothing has been created here yet.</p>
  return <div className="card-grid catalog-grid">{items.map(item => <article className="mini-card" key={item.id}>{render(item)}</article>)}</div>
}

function MutationError({ error }: { error: Error }) { return <p className="error-text" role="alert">{error.message}</p> }
function text(data: FormData, key: string) { return String(data.get(key) ?? '').trim() }
function optional(data: FormData, key: string) { const value = text(data, key); return value || undefined }
function has(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) }
function moduleParent(item: { programId?: string; courseId?: string; subjectId?: string }, programs: Array<{ id: string; displayName: string }>, courses: Array<{ id: string; displayName: string }>, subjects: Array<{ id: string; displayName: string }>) { if (item.subjectId) return subjects.find(value => value.id === item.subjectId)?.displayName ?? 'Subject'; if (item.courseId) return courses.find(value => value.id === item.courseId)?.displayName ?? 'Course'; if (item.programId) return programs.find(value => value.id === item.programId)?.displayName ?? 'Program'; return 'Standalone' }
