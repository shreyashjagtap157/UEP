import { useEffect, useState } from 'react'
import {
  activityForProfile,
  advanceDemoProgress,
  courseFor,
  createDemoProduct,
  gradeDemoSubmission,
  invoicesForFinanceProfile,
  loadDemoState,
  markDemoMessageRead,
  messagesForProfile,
  progressFor,
  publishDemoCourse,
  publishDemoProduct,
  resolveDemoOrganizationAction,
  saveDemoState,
  setDemoUserStatus,
  submissionsForProfile,
  submitDemoAssignment,
  subscribeDemoState,
  updateDemoAttendance,
  updateDemoSupportCase,
} from './LocalDemoStore'
import type { DemoProfile, DemoRole, DemoState } from './LocalDemoStore'

type Section = 'today' | 'learning' | 'schedule' | 'submissions' | 'results' | 'people' | 'organization' | 'analytics' | 'content' | 'operations' | 'integrations' | 'messages' | 'attendance' | 'mentee' | 'activity' | 'finance' | 'support' | 'settings'
type PanelItem = { title: string; detail: string; status: string }
type NavigationItem = { id: Section; label: string }

const NAVIGATION: Record<DemoRole, NavigationItem[]> = {
  student: [
    { id: 'today', label: 'Today' },
    { id: 'learning', label: 'My learning' },
    { id: 'submissions', label: 'Assignments' },
    { id: 'results', label: 'Results & feedback' },
    { id: 'schedule', label: 'Calendar & classes' },
    { id: 'attendance', label: 'Attendance' },
    { id: 'messages', label: 'Inbox' },
  ],
  teacher: [
    { id: 'today', label: 'Teaching overview' },
    { id: 'schedule', label: 'My classes' },
    { id: 'people', label: 'Learner roster' },
    { id: 'submissions', label: 'Submission queue' },
    { id: 'attendance', label: 'Attendance register' },
    { id: 'content', label: 'Course catalog' },
    { id: 'analytics', label: 'Class analytics' },
    { id: 'messages', label: 'Inbox' },
  ],
  client: [
    { id: 'today', label: 'Organization overview' },
    { id: 'organization', label: 'Programs & actions' },
    { id: 'people', label: 'People' },
    { id: 'analytics', label: 'Outcomes & reports' },
    { id: 'schedule', label: 'Delivery schedule' },
    { id: 'operations', label: 'Operations' },
    { id: 'messages', label: 'Inbox' },
  ],
  developer: [
    { id: 'today', label: 'Product overview' },
    { id: 'content', label: 'Products & publishing' },
    { id: 'integrations', label: 'Integrations & webhooks' },
    { id: 'activity', label: 'Product activity' },
    { id: 'operations', label: 'Platform operations' },
  ],
  admin: [
    { id: 'today', label: 'Platform overview' },
    { id: 'people', label: 'People & roles' },
    { id: 'organization', label: 'Organizations' },
    { id: 'content', label: 'Course catalog' },
    { id: 'submissions', label: 'Academic records' },
    { id: 'schedule', label: 'Scheduling' },
    { id: 'operations', label: 'Operations' },
    { id: 'activity', label: 'Audit activity' },
    { id: 'settings', label: 'Configuration' },
  ],
  guardian: [
    { id: 'today', label: 'Learner overview' },
    { id: 'mentee', label: 'Learner progress' },
    { id: 'submissions', label: 'Assignments & results' },
    { id: 'schedule', label: 'Calendar' },
    { id: 'attendance', label: 'Attendance' },
    { id: 'messages', label: 'Inbox' },
  ],
  'exam-controller': [  'exam-controller': [
    { id: 'today', label: 'Exam overview' },
    { id: 'schedule', label: 'Exam schedule' },
    { id: 'submissions', label: 'Exam submissions' },
    { id: 'activity', label: 'Exam activity' },
  ],
  'finance-admin': [
    { id: 'today', label: 'Finance overview' },
    { id: 'finance', label: 'Invoices & payments' },
  ],
  'teaching-assistant': [
    { id: 'today', label: 'Teaching overview' },
    { id: 'schedule', label: 'Assigned classes' },
    { id: 'submissions', label: 'Submission triage' },
    { id: 'attendance', label: 'Attendance' },
    { id: 'activity', label: 'My activity' },
  ],
  evaluator: [
    { id: 'today', label: 'Evaluation overview' },
    { id: 'submissions', label: 'Grading queue' },
    { id: 'activity', label: 'Evaluation activity' },
  ],
  mentor: [
    { id: 'today', label: 'Mentor overview' },
    { id: 'mentee', label: 'Mentee progress' },
    { id: 'submissions', label: 'Learning evidence' },
    { id: 'activity', label: 'Mentoring activity' },
  ],
  support: [
    { id: 'today', label: 'Support overview' },
    { id: 'support', label: 'Support queue' },
  ],
  auditor: [
    { id: 'today', label: 'Audit overview' },
    { id: 'activity', label: 'Audit log' },
  ],
}

function visibleSchedule(state: DemoState, profile: DemoProfile) {
  if (profile.role === 'exam-controller') {
    return state.schedule.filter(item => item.kind === 'Exam' && item.participantIds.includes(profile.userId))
  }
  const user = state.users.find(item => item.id === profile.userId && item.status === 'Active')
  if (!user) return []
  if (profile.role === 'student') return state.schedule.filter(item => item.participantIds.includes(profile.userId))
  if (profile.role === 'guardian') return state.schedule.filter(item => item.participantIds.includes('student-asha'))
  if (profile.role === 'teacher') {
    const courseIds = new Set(user.courseIds)
    return state.schedule.filter(item => item.participantIds.includes(profile.userId) && courseIds.has(item.courseId))
  }
  if (profile.role === 'admin') return state.schedule
  if (profile.role === 'client') {
    const learnerIds = new Set(state.users.filter(learner => learner.role === 'Student' && learner.status === 'Active' && learner.organization === user.organization).map(learner => learner.id))
    return state.schedule.filter(item => item.participantIds.some(id => learnerIds.has(id)))
  }
  if (profile.role === 'teaching-assistant' || profile.role === 'evaluator' || profile.role === 'mentor') {
    const courseIds = new Set(user.courseIds)
    return state.schedule.filter(item => courseIds.has(item.courseId))
  }
  return []
}

export function SpecialistLocalDashboard({ profile, onSignOut }: { profile: DemoProfile; onSignOut: () => void }) {
  const [section, setSection] = useState<Section>('today')
  const [notice, setNotice] = useState('Ready for the role-scoped walkthrough.')
  const [state, setState] = useState<DemoState>(() => loadDemoState())
  const nav = NAVIGATION[profile.role] ?? NAVIGATION.auditor!
  const user = state.users.find(item => item.id === profile.userId && item.status === 'Active')
  const submissions = submissionsForProfile(state, profile)
  const schedule = visibleSchedule(state, profile)
  const attendance = profile.role === 'teaching-assistant'
    ? state.attendance.filter(item => (user?.courseIds ?? []).includes(item.courseId))
    : []
  const activity = activityForProfile(state, profile)
  const financeInvoices = invoicesForFinanceProfile(state, profile)

  useEffect(() => {
    setState(loadDemoState())
    return subscribeDemoState(() => setState(loadDemoState()))
  }, [])

  function openSection(next: Section) {
    setSection(next)
    setNotice(`${nav.find(item => item.id === next)?.label ?? 'Workspace'} opened.`)
  }

  function gradeSubmission(submissionId: string) {
    const fresh = loadDemoState()
    const score = profile.role === 'evaluator' ? 90 : 85
    const next = gradeDemoSubmission(fresh, submissionId, score, 'Reviewed in the local demonstration workspace.', profile)
    if (next === fresh) {
      setNotice('No change was made; the record may be outside this role’s allowed scope.')
      return
    }
    saveDemoState(next)
    setState(next)
    setNotice('Grade saved to shared demo state.')
  }

  function updateSupportCase(caseId: string, status: 'In progress' | 'Resolved') {
    const fresh = loadDemoState()
    const next = updateDemoSupportCase(fresh, caseId, status, profile)
    if (next === fresh) {
      setNotice('No change was made; this case may be outside the support role’s allowed scope.')
      return
    }
    saveDemoState(next)
    setState(next)
    setNotice(`Support case updated: ${caseId} · ${status}.`)
  }

  const summaryItems = summaryFor(profile, state, submissions, schedule, attendance.length, activity.length)

  return (
    <div className="app-frame">
      <aside className="sidebar">
        <div>
          <p className="product-kicker">Universal Education</p>
          <h1 className="product-title">{profile.label} Workspace</h1>
          <p className="muted small">{profile.name}</p>
        </div>
        <nav aria-label="Primary navigation" className="nav-list">
          {nav.map(item => <button key={item.id} className={section === item.id ? 'nav-item active' : 'nav-item'} onClick={() => openSection(item.id)}>{item.label}</button>)}
        </nav>
        <div className="sidebar-footer">
          <p className="signed-in">{profile.email}</p>
          <p className="muted small">Role-scoped local session</p>
          <button className="text-button" onClick={onSignOut}>Sign out</button>
        </div>
      </aside>

      <main className="workspace">
        <header className="workspace-header">
          <div><p className="eyebrow">Local seeded dataset · {profile.label}</p><h2>{nav.find(item => item.id === section)?.label ?? 'Workspace'}</h2></div>
          <span className="version-badge">Demo data · v0.15.0</span>
        </header>

        {section === 'today' && (
          <div className="content-stack">
            <section className="hero-card"><p className="eyebrow">{profile.label}</p><h3>{summaryTitle(profile.role)}</h3><p>{profile.description} This workspace is restricted to the current persona's assigned records and actions.</p></section>
            <section className="metric-grid" aria-label="Role-scoped metrics">{summaryItems.map(item => <article className="metric" key={item.title}><span>{item.title}</span><strong>{item.status}</strong><small>{item.detail}</small></article>)}</section>
            {profile.role === 'teaching-assistant' && <SubmissionPanel state={state} profile={profile} submissions={submissions} onGrade={gradeSubmission} />}
            {profile.role === 'evaluator' && <SubmissionPanel state={state} profile={profile} submissions={submissions} onGrade={gradeSubmission} />}
            {profile.role === 'mentor' && <MenteePanel state={state} profile={profile} />}
            {profile.role === 'auditor' && <PanelList eyebrow="Read-only" title="Audit coverage" items={[{ title: 'Recorded events', detail: 'Synthetic local event history; no mutations are available from this workspace.', status: String(activity.length) }, { title: 'Academic records', detail: 'Audit access is read-only.', status: 'Read-only' }]} />}
          </div>
        )}
        {section === 'schedule' && <PanelList eyebrow="Assigned schedule" title={profile.role === 'exam-controller' ? 'Examination sessions' : 'Assigned classes'} items={schedule.map(item => ({ title: item.title, detail: `${courseFor(state, item.courseId)?.title ?? 'Assigned course'} · ${new Date(item.startsAt).toLocaleString()} · ${item.durationMinutes} min`, status: `${item.kind} · ${item.status}` }))} />}
        {section === 'submissions' && <SubmissionPanel state={state} profile={profile} submissions={submissions} onGrade={gradeSubmission} />}
        {section === 'attendance' && <PanelList eyebrow="Assigned class records" title="Attendance register" items={attendance.map(item => ({ title: state.users.find(person => person.id === item.studentId)?.name ?? 'Learner', detail: `${courseFor(state, item.courseId)?.title ?? 'Assigned course'} · ${item.date}`, status: item.status }))} />}
        {section === 'mentee' && <MenteePanel state={state} profile={profile} />}
        {section === 'activity' && <PanelList eyebrow={profile.role === 'auditor' ? 'Compliance' : 'Scoped activity'} title={profile.role === 'auditor' ? 'Audit log' : 'Relevant recorded activity'} items={activity.map(item => ({ title: item.action, detail: `${item.at} · ${item.actor} · ${item.detail}`, status: 'Recorded' }))} />}
        {section === 'finance' && <FinanceScopePanel invoices={financeInvoices} />}
        {section === 'support' && <SupportScopePanel state={state} profile={profile} onStatus={updateSupportCase} />}
        <section className="panel" aria-live="polite"><p className="eyebrow">Walkthrough status</p><p>{notice}</p><small className="muted">Data shown here is seeded browser state. This mode does not call the production API or settle real payments.</small></section>
      </main>
    </div>
  )
}

function summaryTitle(role: DemoRole) {
  switch (role) {
    case 'exam-controller': return 'Examination operations'
    case 'finance-admin': return 'Finance and account context'
    case 'teaching-assistant': return 'Assigned teaching support'
    case 'evaluator': return 'Assessment review'
    case 'mentor': return 'Learner mentoring'
    case 'support': return 'Privacy-preserving support view'
    case 'auditor': return 'Read-only compliance view'
    default: return 'Role workspace'
  }
}

function summaryFor(profile: DemoProfile, state: DemoState, submissions: ReturnType<typeof submissionsForProfile>, schedule: ReturnType<typeof visibleSchedule>, attendanceCount: number, activityCount: number): PanelItem[] {
  const currentUser = state.users.find(item => item.id === profile.userId && item.status === 'Active')
  if (profile.role === 'exam-controller') return [
    { title: 'Exam sessions', status: String(schedule.length), detail: 'Scheduled examinations assigned to this controller' },
    { title: 'Submission records', status: String(submissions.length), detail: 'Records attached to assigned exam courses' },
    { title: 'Open sessions', status: String(schedule.filter(item => item.status === 'Scheduled').length), detail: 'Exams not yet completed or cancelled' },
  ]
  if (profile.role === 'finance-admin') {
    const invoices = invoicesForFinanceProfile(state, profile)
    const outstanding = invoices.filter(invoice => invoice.status !== 'Paid')
    const overdue = invoices.filter(invoice => invoice.status === 'Overdue')
    return [
      { title: 'Outstanding invoices', status: String(outstanding.length), detail: `${outstanding.reduce((sum, invoice) => sum + invoice.amountInr, 0).toLocaleString('en-IN', { style: 'currency', currency: 'INR' })} outstanding in the local ledger` },
      { title: 'Overdue invoices', status: String(overdue.length), detail: 'Synthetic ledger records requiring finance review' },
      { title: 'Tenant context', status: currentUser?.organization ?? 'Unavailable', detail: 'Organization associated with this local persona' },
    ]
  }
  if (profile.role === 'teaching-assistant') return [
    { title: 'Assigned courses', status: String(currentUser?.courseIds.length ?? 0), detail: 'Course scope for submission triage and attendance' },
    { title: 'Needs grading', status: String(submissions.filter(item => item.status === 'Submitted').length), detail: 'Submitted work in assigned courses' },
    { title: 'Attendance records', status: String(attendanceCount), detail: 'Attendance records for assigned courses' },
  ]
  if (profile.role === 'evaluator') return [
    { title: 'Evaluation courses', status: String(currentUser?.courseIds.length ?? 0), detail: 'Courses assigned to this evaluator' },
    { title: 'Awaiting grade', status: String(submissions.filter(item => item.status === 'Submitted').length), detail: 'Submitted work eligible for evaluation' },
    { title: 'Graded records', status: String(submissions.filter(item => item.status === 'Graded').length), detail: 'Submitted work already graded' },
  ]
  if (profile.role === 'mentor') {
    const mentee = state.users.find(item => item.id === 'student-asha' && item.status === 'Active')
    const completedProgress = mentee ? mentee.courseIds.filter(courseId => progressFor(state, mentee.id, courseId) >= 100).length : 0
    return [
      { title: 'Linked learner', status: mentee?.name ?? 'Not linked', detail: 'Explicit seeded mentorship relationship' },
      { title: 'Enrolled courses', status: String(mentee?.courseIds.length ?? 0), detail: 'Courses on the linked learner record' },
      { title: 'Completed progress', status: String(completedProgress), detail: 'Courses at 100% recorded progress' },
    ]
  }
  if (profile.role === 'support') return [
    { title: 'Open support cases', status: String(state.supportCases.filter(item => item.status === 'Open').length), detail: 'Cases ready for triage' },
    { title: 'In progress', status: String(state.supportCases.filter(item => item.status === 'In progress').length), detail: 'Cases currently being handled' },
    { title: 'Active accounts', status: String(state.users.filter(item => item.status === 'Active').length), detail: 'Aggregate count only; individual academic records are not exposed' },
  ]
  return [
    { title: 'Audit events', status: String(activityCount), detail: 'Events visible to the read-only auditor persona' },
    { title: 'User records', status: String(state.users.length), detail: 'Synthetic local records' },
    { title: 'Course records', status: String(state.courses.length), detail: 'Synthetic local records' },
  ]
}

function SubmissionPanel({ state, profile, submissions, onGrade }: { state: DemoState; profile: DemoProfile; submissions: ReturnType<typeof submissionsForProfile>; onGrade: (id: string) => void }) {
  const canGrade = profile.role === 'teaching-assistant' || profile.role === 'evaluator'
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Assigned learning evidence</p><h3>{profile.role === 'exam-controller' ? 'Exam-linked records' : profile.role === 'mentor' ? 'Mentee submissions' : 'Submission queue'}</h3></div><span className="count-badge">{submissions.length}</span></div>
    {submissions.length ? <div className="local-module-list">{submissions.map(item => {
      const student = state.users.find(person => person.id === item.studentId)
      const course = courseFor(state, item.courseId)
      return <article className="local-module" key={item.id}><div><strong>{item.title}</strong><p>{item.examScheduleId ? state.schedule.find(session => session.id === item.examScheduleId)?.title ?? 'Assigned examination' : course?.title ?? 'Assigned course'} · {student?.name ?? 'Learner'} · due {new Date(item.dueAt).toLocaleDateString()}</p>{item.feedback && <small className="muted">Feedback: {item.feedback}</small>}</div><div><span className="chip">{item.status === 'Graded' ? `${item.score}/100` : item.status}</span>{canGrade && item.status === 'Submitted' && <button type="button" className="text-button" onClick={() => onGrade(item.id)}>Grade {profile.role === 'evaluator' ? '90%' : '85%'}</button>}</div></article>
    })}</div> : <p className="muted">No submissions are assigned to this persona.</p>}
  </section>
}

function MenteePanel({ state, profile }: { state: DemoState; profile: DemoProfile }) {
  if (profile.role !== 'mentor') return <PanelList eyebrow="Relationship" title="Linked learner" items={[]} />
  const mentee = state.users.find(item => item.id === 'student-asha' && item.status === 'Active')
  if (!mentee) return <PanelList eyebrow="Relationship" title="No active mentee" items={[]} />
  const courses = mentee.courseIds.map(courseId => {
    const course = courseFor(state, courseId)
    const progress = progressFor(state, mentee.id, courseId)
    return { title: course?.title ?? courseId, detail: `${mentee.name} · recorded progress ${progress}%`, status: progress >= 100 ? 'Completed' : 'In progress' }
  })
  return <PanelList eyebrow="Mentorship relationship" title={`${mentee.name} · learning progress`} items={courses} />
}

function FinanceScopePanel({ invoices }: { invoices: DemoState['invoices'] }) {
  const totalOutstanding = invoices.filter(invoice => invoice.status !== 'Paid').reduce((sum, invoice) => sum + invoice.amountInr, 0)
  return (
    <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">Synthetic finance ledger</p><h3>Invoices and account status</h3></div><span className="count-badge">{invoices.length} invoices</span></div>
      <p className="muted">Outstanding demo balance: {totalOutstanding.toLocaleString('en-IN', { style: 'currency', currency: 'INR' })}. This is presentation data only; no payment, refund, or settlement occurs here.</p>
      {invoices.length ? <div className="local-module-list">{invoices.map(invoice => {
        const student = state.users.find(user => user.id === invoice.studentId)
        return <article className="local-module" key={invoice.id}>
          <div><strong>{invoice.invoiceNumber} · {student?.name ?? 'Learner'}</strong><p>{invoice.description} · due {new Date(invoice.dueAt).toLocaleDateString()}</p><small className="muted">{invoice.organization}</small></div>
          <div><strong>{invoice.amountInr.toLocaleString('en-IN', { style: 'currency', currency: 'INR' })}</strong><span className="chip">{invoice.status}</span></div>
        </article>
      })}</div> : <p className="muted">No invoices are seeded.</p>}
    </section>
  )
}

function SupportScopePanel({ state, profile, onStatus }: { state: DemoState; profile: DemoProfile; onStatus: (id: string, status: 'In progress' | 'Resolved') => void }) {
  const canManage = profile.role === 'support'
  return (
    <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">Privacy-scoped service desk</p><h3>Support cases</h3></div><span className="count-badge">{state.supportCases.length} cases</span></div>
      <p className="muted">Cases contain synthetic operational summaries only, not learner submissions or private contact details.</p>
      {state.supportCases.length ? <div className="local-module-list">{state.supportCases.map(item => (
        <article className="local-module" key={item.id}>
          <div><strong>{item.reference} · {item.category}</strong><p>{item.summary}</p><small className="muted">{item.organization} · opened {new Date(item.createdAt).toLocaleDateString()}</small></div>
          <div><span className="chip">{item.priority} · {item.status}</span>{canManage && item.status === 'Open' && <button type="button" className="text-button" onClick={() => onStatus(item.id, 'In progress')}>Start triage</button>}{canManage && item.status !== 'Resolved' && <button type="button" className="text-button" onClick={() => onStatus(item.id, 'Resolved')}>Resolve case</button>}</div>
        </article>
      ))}</div> : <p className="muted">No support cases are available.</p>}
    </section>
  )
}

function PanelList({ eyebrow, title, items }: { eyebrow: string; title: string; items: PanelItem[] }) {
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">{eyebrow}</p><h3>{title}</h3></div><span className="count-badge">{items.length}</span></div>{items.length ? <div className="local-module-list">{items.map(item => <article className="local-module" key={`${item.title}-${item.detail}`}><div><strong>{item.title}</strong><p>{item.detail}</p></div><span className="chip">{item.status}</span></article>)}</div> : <p className="muted">No records are available for this role and scope.</p>}</section>
}
