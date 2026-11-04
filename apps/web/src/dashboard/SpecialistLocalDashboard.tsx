import { useEffect, useState } from 'react'
import {
  activeUserForProfile,
  activityForProfile,
  addDemoUser,
  addDemoMaterial,
  availableCourseCountForProfile,
  linkedLearnerProgressForProfile,
  advanceDemoProgress,
  courseFor,
  createDemoProduct,
  gradeDemoSubmission,
  gradeDemoExamSubmission,
  invoicesForFinanceProfile,
  attendanceForProfile,
  coursesForProfile,
  learnersForProfile,
  linkedLearnerForProfile,
  messageRecipientsForProfile,
  scheduleForProfile,
  sendDemoMessage,
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
  submitDemoExam,
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
    { id: 'today', label: 'Platform overview' },
    { id: 'integrations', label: 'Integrations & webhooks' },
    { id: 'operations', label: 'Client service operations' },
  ],
  'product-maintainer': [
    { id: 'today', label: 'Product portfolio' },
    { id: 'content', label: 'Products & publishing' },
    { id: 'activity', label: 'Product activity' },
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
  'exam-controller': [
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

export function SpecialistLocalDashboard({ profile, onSignOut }: { profile: DemoProfile; onSignOut: () => void }) {
  const [section, setSection] = useState<Section>('today')
  const [notice, setNotice] = useState('Ready for the role-scoped walkthrough.')
  const [state, setState] = useState<DemoState>(() => loadDemoState())
  const [productTitle, setProductTitle] = useState('')
  const [productDescription, setProductDescription] = useState('')
  const nav = NAVIGATION[profile.role]
  const submissions = submissionsForProfile(state, profile)
  const schedule = scheduleForProfile(state, profile)
  const attendance = attendanceForProfile(state, profile)
  const activity = activityForProfile(state, profile)
  const financeInvoices = invoicesForFinanceProfile(state, profile)
  const messages = messagesForProfile(state, profile)

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
    commitState(next, 'Grade saved to shared demo state.')
  }

  function updateSupportCase(caseId: string, status: 'In progress' | 'Resolved') {
    const fresh = loadDemoState()
    const next = updateDemoSupportCase(fresh, caseId, status, profile)
    if (next === fresh) {
      setNotice('No change was made; this case may be outside the support role’s allowed scope.')
      return
    }
    commitState(next, `Support case updated: ${caseId} · ${status}.`)
  }

  function commitState(next: DemoState, successMessage: string) {
    const persisted = saveDemoState(next)
    setState(next)
    setNotice(persisted
      ? successMessage
      : 'Change is visible in this tab only. Browser storage is unavailable, so reloading may discard it.')
  }

  function changeState(next: DemoState, successMessage: string, previousState?: DemoState) {
    if (previousState && next === previousState) {
      setNotice('No change was made; this action may be outside the signed-in role’s allowed scope.')
      return
    }
    commitState(next, successMessage)
  }

  function submitAssignment(submissionId: string) {
    const fresh = loadDemoState()
    changeState(submitDemoAssignment(fresh, submissionId, profile), 'Assignment submitted and saved.', fresh)
  }

  function submitExamAttempt(submissionId: string) {
    const fresh = loadDemoState()
    changeState(submitDemoExam(fresh, submissionId, profile), 'Exam attempt submitted and saved.', fresh)
  }

  function gradeExamAttempt(submissionId: string) {
    const fresh = loadDemoState()
    changeState(gradeDemoExamSubmission(fresh, submissionId, 80, 'Reviewed in the assigned local exam session.', profile),
      'Exam result published to the shared state.', fresh)
  }

  function advanceProgress(courseId: string) {
    const fresh = loadDemoState()
    changeState(advanceDemoProgress(fresh, courseId, profile), 'Learning progress updated and saved.', fresh)
  }

  function markAttendance(attendanceId: string) {
    const fresh = loadDemoState()
    changeState(updateDemoAttendance(fresh, attendanceId, 'Present', profile), 'Attendance update saved.', fresh)
  }

  function markMessageRead(messageId: string) {
    const fresh = loadDemoState()
    changeState(markDemoMessageRead(fresh, messageId, profile), 'Message marked as read.', fresh)
  }

  function createProduct() {
    const fresh = loadDemoState()
    const next = createDemoProduct(fresh, productTitle, productDescription, profile)
    if (next === fresh) {
      setNotice('Product was not created; check the title and role permission.')
      return
    }
    changeState(next, 'Product draft created.', fresh)
    setProductTitle('')
    setProductDescription('')
  }

  function publishProduct(productId: string) {
    const fresh = loadDemoState()
    changeState(publishDemoProduct(fresh, productId, profile), 'Product published to the shared demo catalog.', fresh)
  }

  function publishCourse(courseId: string) {
    const fresh = loadDemoState()
    changeState(publishDemoCourse(fresh, courseId, profile), 'Course published to the shared demo catalog.', fresh)
  }

  function resolveOrganizationAction(actionId: string) {
    const fresh = loadDemoState()
    changeState(resolveDemoOrganizationAction(fresh, actionId, profile), 'Organization action resolved.', fresh)
  }

  function changeUserStatus(userId: string, status: 'Active' | 'Suspended') {
    const fresh = loadDemoState()
    changeState(setDemoUserStatus(fresh, userId, status, profile), 'User status updated.', fresh)
  }

  function inviteUser(name: string, email: string, role: 'Student' | 'Teacher') {
    const fresh = loadDemoState()
    const next = addDemoUser(fresh, { name, email, role }, profile)
    if (next === fresh) {
      setNotice('User was not invited; check the name, email and role, and avoid duplicate emails.')
      return
    }
    changeState(next, 'User invited to the organization.', fresh)
  }

  function sendMessage(subject: string, body: string, recipientId: string): boolean {
    const fresh = loadDemoState()
    const next = sendDemoMessage(fresh, { subject, body, recipientIds: recipientId ? [recipientId] : [] }, profile)
    if (next === fresh) {
      setNotice('Message was not sent; choose an active, authorized recipient and enter a subject and message.')
      return false
    }
    changeState(next, 'Message sent to the selected recipient.', fresh)
    return true
  }

  function addMaterial(courseId: string, title: string, summary: string) {
    const fresh = loadDemoState()
    const next = addDemoMaterial(fresh, { courseId, title, summary }, profile)
    if (next === fresh) {
      setNotice('Material was not added; choose a course taught in your scope and enter a title.')
      return
    }
    changeState(next, 'Learning material added to the course.', fresh)
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
          {nav.map(item => (
            <button
              key={item.id}
              type="button"
              className={section === item.id ? 'nav-item active' : 'nav-item'}
              aria-current={section === item.id ? 'page' : undefined}
              onClick={() => openSection(item.id)}
            >
              {item.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-footer">
          <p className="signed-in">{profile.email}</p>
          <p className="muted small">Role-scoped local session</p>
          <p className="muted small">Use Switch user in the workspace header to sign out and choose another account.</p>
        </div>
      </aside>

      <main className="workspace">
        <header className="workspace-header">
          <div><p className="eyebrow">Local seeded dataset · {profile.label}</p><h2>{nav.find(item => item.id === section)?.label ?? 'Workspace'}</h2></div>
          <div className="workspace-header-actions">
            <span className="environment-badge environment-badge-demo">OFFLINE DEMO · SEEDED BROWSER DATA</span>
            <span className="version-badge">Demo data · v0.15.0</span>
            <div className="session-chip">
              <span className="session-avatar" aria-hidden="true">{initialsFor(profile.name)}</span>
              <span className="session-meta">
                <strong>{profile.name}</strong>
                <small>{profile.label}</small>
              </span>
              <button className="secondary-button" onClick={onSignOut} title="Sign out and return to the demo account selector">Switch user</button>
            </div>
          </div>
        </header>

        {section === 'today' && (
          <div className="content-stack">
            <section className="hero-card"><p className="eyebrow">{profile.label}</p><h3>{summaryTitle(profile.role)}</h3><p>{profile.description} This workspace is restricted to the current persona's assigned records and actions.</p></section>
            <section className="metric-grid" aria-label="Role-scoped metrics">{summaryItems.map(item => <article className="metric" key={item.title}><span>{item.title}</span><strong>{item.status}</strong><small>{item.detail}</small></article>)}</section>
            {profile.role === 'student' && <><LearningPanel state={state} profile={profile} onProgress={advanceProgress} /><SubmissionPanel state={state} profile={profile} submissions={submissions} onGrade={gradeSubmission} onSubmit={submitAssignment} onGradeExam={gradeExamAttempt} onSubmitExam={submitExamAttempt} /></>}
            {profile.role === 'teacher' && <SubmissionPanel state={state} profile={profile} submissions={submissions} onGrade={gradeSubmission} onSubmit={submitAssignment} onGradeExam={gradeExamAttempt} onSubmitExam={submitExamAttempt} />}
            {profile.role === 'client' && <OrganizationPanel state={state} profile={profile} onResolve={resolveOrganizationAction} />}
            {profile.role === 'developer' && <IntegrationsPanel state={state} profile={profile} />}
            {profile.role === 'product-maintainer' && <PanelList eyebrow="Product lifecycle" title="Catalogue & release status" items={state.products.map(product => ({ title: product.title + ' · v' + product.version, detail: product.description, status: product.status }))} />}
            {profile.role === 'admin' && <OperationsPanel state={state} profile={profile} onResolve={resolveOrganizationAction} onSupportStatus={updateSupportCase} />}
            {profile.role === 'teaching-assistant' && <SubmissionPanel state={state} profile={profile} submissions={submissions} onGrade={gradeSubmission} onSubmit={submitAssignment} onGradeExam={gradeExamAttempt} onSubmitExam={submitExamAttempt} />}
            {profile.role === 'evaluator' && <SubmissionPanel state={state} profile={profile} submissions={submissions} onGrade={gradeSubmission} onSubmit={submitAssignment} onGradeExam={gradeExamAttempt} onSubmitExam={submitExamAttempt} />}
            {(profile.role === 'mentor' || profile.role === 'guardian') && <MenteePanel state={state} profile={profile} />}
            {profile.role === 'auditor' && <PanelList eyebrow="Read-only" title="Audit coverage" items={[{ title: 'Recorded events', detail: 'Synthetic local event history; no mutations are available from this workspace.', status: String(activity.length) }, { title: 'Academic records', detail: 'Audit access is read-only.', status: 'Read-only' }]} />}
          </div>
        )}
        {section === 'learning' && <LearningPanel state={state} profile={profile} onProgress={advanceProgress} />}
        {section === 'schedule' && <PanelList eyebrow="Assigned schedule" title={profile.role === 'exam-controller' ? 'Examination sessions' : 'Assigned classes'} items={schedule.map(item => ({ title: item.title, detail: `${courseFor(state, item.courseId)?.title ?? 'Assigned course'} · ${new Date(item.startsAt).toLocaleString()} · ${item.durationMinutes} min`, status: `${item.kind} · ${item.status}` }))} />}
        {section === 'submissions' && <SubmissionPanel state={state} profile={profile} submissions={submissions} onGrade={gradeSubmission} onSubmit={submitAssignment} onGradeExam={gradeExamAttempt} onSubmitExam={submitExamAttempt} />}
        {section === 'results' && <PanelList eyebrow="Academic results" title="Graded submissions" items={submissions.filter(item => item.status === 'Graded').map(item => ({ title: item.title, detail: `${courseFor(state, item.courseId)?.title ?? 'Assigned course'} · ${state.users.find(person => person.id === item.studentId)?.name ?? 'Learner'}${item.feedback ? ' · ' + item.feedback : ''}`, status: typeof item.score === 'number' ? `${item.score}/100` : 'Graded' }))} />}
        {section === 'people' && <PeoplePanel state={state} profile={profile} onUserStatus={changeUserStatus} onInvite={inviteUser} />}
        {section === 'organization' && <OrganizationPanel state={state} profile={profile} onResolve={resolveOrganizationAction} />}
        {section === 'analytics' && <AnalyticsPanel state={state} profile={profile} submissions={submissions} attendance={attendance} schedule={schedule} />}
        {section === 'content' && <ContentPanel state={state} profile={profile} onCreateProduct={createProduct} onProductTitle={setProductTitle} onProductDescription={setProductDescription} productTitle={productTitle} productDescription={productDescription} onPublishProduct={publishProduct} onPublishCourse={publishCourse} onAddMaterial={addMaterial} />}
        {section === 'operations' && <OperationsPanel state={state} profile={profile} onResolve={resolveOrganizationAction} onSupportStatus={updateSupportCase} />}
        {section === 'integrations' && <IntegrationsPanel state={state} profile={profile} />}
        {section === 'messages' && <MessagesPanel state={state} profile={profile} messages={messages} onMarkRead={markMessageRead} onSend={sendMessage} />}
        {section === 'settings' && <SettingsPanel state={state} profile={profile} />}
        {section === 'attendance' && <AttendancePanel state={state} profile={profile} attendance={attendance} onMarkPresent={markAttendance} />}
        {section === 'mentee' && <MenteePanel state={state} profile={profile} />}
        {section === 'activity' && <PanelList eyebrow={profile.role === 'auditor' ? 'Compliance' : 'Scoped activity'} title={profile.role === 'auditor' ? 'Audit log' : 'Relevant recorded activity'} items={activity.map(item => ({ title: item.action, detail: `${item.at} · ${item.actor} · ${item.detail}`, status: 'Recorded' }))} />}
        {section === 'finance' && <FinanceScopePanel invoices={financeInvoices} users={state.users} />}
        {section === 'support' && <SupportScopePanel state={state} profile={profile} onStatus={updateSupportCase} />}
        <section className="panel" role="status" aria-live="polite" aria-atomic="true"><p>{notice}</p></section>
      </main>
    </div>
  )
}

function initialsFor(name: string) {
  return name.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]?.toUpperCase() ?? '').join('') || 'U'
}

function summaryTitle(role: DemoRole) {  switch (role) {
    case 'student': return 'Student learning workspace'
    case 'teacher': return 'Teaching and assessment workspace'
    case 'client': return 'Institution overview'
    case 'developer': return 'Platform engineering workspace'
    case 'product-maintainer': return 'Product lifecycle workspace'
    case 'admin': return 'Platform administration'
    case 'guardian': return 'Linked learner overview'
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

function summaryFor(profile: DemoProfile, state: DemoState, submissions: ReturnType<typeof submissionsForProfile>, schedule: ReturnType<typeof scheduleForProfile>, attendanceCount: number, activityCount: number): PanelItem[] {
  const currentUser = activeUserForProfile(state, profile)
  if (profile.role === 'student') {
    return [
      { title: 'Enrolled courses', status: String(availableCourseCountForProfile(state, profile)), detail: 'Published courses available to this learner' },
      { title: 'Pending work', status: String(submissions.filter(item => item.status === 'Assigned').length), detail: 'Assignments and exams awaiting completion' },
      { title: 'Graded work', status: String(submissions.filter(item => item.status === 'Graded').length), detail: 'Submissions with feedback or a score' },
    ]
  }
  if (profile.role === 'teacher') return [
    { title: 'Assigned courses', status: String(currentUser?.courseIds.length ?? 0), detail: 'Teaching assignments in the current workspace' },
    { title: 'Awaiting review', status: String(submissions.filter(item => item.status === 'Submitted').length), detail: 'Submitted assignments awaiting review' },
    { title: 'Attendance rows', status: String(attendanceCount), detail: 'Records within assigned course scope' },
  ]
  if (profile.role === 'client') {
    const learners = learnersForProfile(state, profile)
    const actions = state.organizationActions.filter(item => item.organization === currentUser?.organization && item.status === 'Open')
    return [
      { title: 'Organization learners', status: String(learners.length), detail: currentUser?.organization ?? 'No organization linked' },
      { title: 'Enrolled courses', status: String(availableCourseCountForProfile(state, profile)), detail: 'Published courses available across organization learners' },
      { title: 'Open actions', status: String(actions.length), detail: 'Organization follow-up queue' },
    ]
  }
  if (profile.role === 'developer') return [
    { title: 'Tracked integrations', status: String(state.webhooks.length), detail: 'Configured platform webhook connections' },
    { title: 'Webhook failures', status: String(state.webhooks.reduce((sum, item) => sum + item.failedDeliveries, 0)), detail: 'Failed deliveries requiring technical attention' },
    { title: 'Client service cases', status: String(state.supportCases.filter(item => item.status !== 'Resolved').length), detail: 'Open or active institution support cases' },
  ]
  if (profile.role === 'product-maintainer') return [
    { title: 'Products in catalogue', status: String(state.products.length), detail: 'Educational products managed by the platform team' },
    { title: 'Drafts to review', status: String(state.products.filter(item => item.status !== 'Published').length), detail: 'Products awaiting release approval' },
    { title: 'Published products', status: String(state.products.filter(item => item.status === 'Published').length), detail: 'Products available to institutions' },
  ]
  if (profile.role === 'admin') return [
    { title: 'Active accounts', status: String(state.users.filter(item => item.status === 'Active').length), detail: 'Platform-wide user count' },
    { title: 'Operational cases', status: String(state.supportCases.filter(item => item.status !== 'Resolved').length), detail: 'Support cases not yet resolved' },
  ]
  if (profile.role === 'guardian') {
    const learner = linkedLearnerForProfile(state, profile)
    return [
      { title: 'Linked learner', status: learner?.name ?? 'Not linked', detail: 'Explicit guardian-to-learner relationship' },
      { title: 'Assigned work', status: String(submissions.length), detail: 'Assignments visible for the linked learner' },
      { title: 'Attendance rows', status: String(attendanceCount), detail: 'Attendance history for the linked learner' },
    ]
  }
  if (profile.role === 'exam-controller') return [
    { title: 'Exam sessions', status: String(schedule.length), detail: 'Scheduled examinations assigned to this controller' },
    { title: 'Submission records', status: String(submissions.length), detail: 'Records explicitly linked to assigned exam sessions' },
    { title: 'Open sessions', status: String(schedule.filter(item => item.status === 'Scheduled').length), detail: 'Exams not yet completed or cancelled' },
  ]
  if (profile.role === 'finance-admin') {
    const invoices = invoicesForFinanceProfile(state, profile)
    const outstanding = invoices.filter(invoice => invoice.status !== 'Paid')
    const overdue = invoices.filter(invoice => invoice.status === 'Overdue')
    return [
      { title: 'Outstanding invoices', status: String(outstanding.length), detail: String(outstanding.reduce((sum, invoice) => sum + invoice.amountInr, 0).toLocaleString('en-IN', { style: 'currency', currency: 'INR' })) + ' outstanding in local ledger' },
      { title: 'Overdue invoices', status: String(overdue.length), detail: 'Synthetic ledger records needing finance review' },
      { title: 'Organization', status: currentUser?.organization ?? 'Unavailable', detail: 'Organization associated with this finance account' },
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
    const mentorProgress = linkedLearnerProgressForProfile(state, profile)
    return [
      { title: 'Linked learner', status: mentorProgress.learner?.name ?? 'Not linked', detail: 'Learner resolved through the active mentorship relationship' },
      { title: 'Enrolled courses', status: String(mentorProgress.courses.length), detail: 'Published courses visible in this mentorship scope' },
      { title: 'Completed progress', status: String(mentorProgress.completedCourses), detail: 'Visible published courses at 100% recorded progress' },
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

function SubmissionPanel({ state, profile, submissions, onGrade, onSubmit, onGradeExam, onSubmitExam }: { state: DemoState; profile: DemoProfile; submissions: ReturnType<typeof submissionsForProfile>; onGrade: (id: string) => void; onSubmit: (id: string) => void; onGradeExam: (id: string) => void; onSubmitExam: (id: string) => void }) {
  const canGrade = profile.role === 'teacher' || profile.role === 'teaching-assistant' || profile.role === 'evaluator'
  const canGradeExam = profile.role === 'exam-controller'
  const canSubmit = profile.role === 'student'
  const canSubmitExam = profile.role === 'student'
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Assigned learning evidence</p><h3>{profile.role === 'exam-controller' ? 'Exam-linked records' : profile.role === 'mentor' ? 'Mentee submissions' : 'Submission queue'}</h3></div><span className="count-badge">{submissions.length}</span></div>
    {submissions.length ? <div className="local-module-list">{submissions.map(item => {
      const student = state.users.find(person => person.id === item.studentId)
      const course = courseFor(state, item.courseId)
      return <article className="local-module" key={item.id}><div><strong>{item.title}</strong><p>{item.examScheduleId ? state.schedule.find(session => session.id === item.examScheduleId)?.title ?? 'Assigned examination' : course?.title ?? 'Assigned course'} · {student?.name ?? 'Learner'} · due {new Date(item.dueAt).toLocaleDateString()}</p>{item.feedback && <small className="muted">Feedback: {item.feedback}</small>}</div><div><span className="chip">{item.status === 'Graded' ? `${item.score}/100` : item.status}</span>{canGrade && !item.examScheduleId && item.status === 'Submitted' && <button type="button" className="text-button" onClick={() => onGrade(item.id)}>Grade {profile.role === 'evaluator' ? '90%' : '85%'}</button>}{canGradeExam && item.examScheduleId && item.status === 'Submitted' && <button type="button" className="text-button" onClick={() => onGradeExam(item.id)}>Publish exam result (80%)</button>}{canSubmit && !item.examScheduleId && item.status === 'Assigned' && <button type="button" className="text-button" onClick={() => onSubmit(item.id)}>Submit work</button>}{canSubmitExam && item.examScheduleId && item.status === 'Assigned' && <button type="button" className="text-button" onClick={() => onSubmitExam(item.id)}>Submit exam attempt</button>}</div></article>
    })}</div> : <p className="muted">No submissions are assigned to this persona.</p>}
  </section>
}

function MenteePanel({ state, profile }: { state: DemoState; profile: DemoProfile }) {
  if (profile.role !== 'mentor' && profile.role !== 'guardian') return <PanelList eyebrow="Relationship" title="Linked learner" items={[]} />
  const mentee = linkedLearnerForProfile(state, profile)
  if (!mentee) return <PanelList eyebrow="Relationship" title="No active mentee" items={[]} />
  const courses = coursesForProfile(state, profile).map(course => {
    const progress = progressFor(state, mentee.id, course.id)
    return { title: course.title, detail: `${mentee.name} · recorded progress ${progress}%`, status: progress >= 100 ? 'Completed' : 'In progress' }
  })
  return <PanelList eyebrow="Mentorship relationship" title={`${mentee.name} · learning progress`} items={courses} />
}

function LearningPanel({ state, profile, onProgress }: { state: DemoState; profile: DemoProfile; onProgress: (courseId: string) => void }) {
  const learner = profile.role === 'guardian' || profile.role === 'mentor'
    ? linkedLearnerForProfile(state, profile)
    : activeUserForProfile(state, profile)
  const learnerId = learner?.id ?? ''
  const courses = coursesForProfile(state, profile)
  const materials = state.materials.filter(material => courses.some(course => course.id === material.courseId))
  return <section className="panel">
    <div className="panel-heading"><div><p className="eyebrow">Learning progress</p><h3>{profile.role === 'guardian' || profile.role === 'mentor' ? (learner?.name ?? 'Linked learner') + ' · learning progress' : 'My enrolled courses'}</h3></div><span className="count-badge">{courses.length}</span></div>
    {courses.length ? <div className="local-module-list">{courses.map(course => {
      const percent = progressFor(state, learnerId, course.id)
      return <article className="local-module" key={course.id}><div><strong>{course.title}</strong><p>{course.category} · {course.lessonCount} lessons · {percent}% recorded progress</p></div><div><span className="chip">{course.status}</span>{profile.role === 'student' && percent < 100 && <button type="button" className="text-button" onClick={() => onProgress(course.id)}>Record progress +10%</button>}</div></article>
    })}</div> : <p className="muted">No courses are linked to this account.</p>}
    <div className="panel-heading" style={{ marginTop: '1.5rem' }}><div><p className="eyebrow">Course materials</p><h3>Resources from your teachers</h3></div><span className="count-badge">{materials.length}</span></div>
    {materials.length ? <div className="local-module-list">{materials.map(material => <article className="local-module" key={material.id}>
      <div><strong>{material.title}</strong><p>{material.summary}</p><small className="muted">{courses.find(course => course.id === material.courseId)?.title ?? 'Course'} · added by {material.addedBy} on {new Date(material.addedAt).toLocaleDateString()}</small></div>
    </article>)}</div> : <p className="muted">No learning materials are published for these courses yet.</p>}
  </section>
}

function AttendancePanel({ state, profile, attendance, onMarkPresent }: { state: DemoState; profile: DemoProfile; attendance: DemoState['attendance']; onMarkPresent: (id: string) => void }) {
  const canMark = profile.role === 'teacher' || profile.role === 'admin'
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Academic records</p><h3>Attendance register</h3></div><span className="count-badge">{attendance.length}</span></div>
    {attendance.length ? <div className="local-module-list">{attendance.map(item => {
      const student = state.users.find(person => person.id === item.studentId)
      return <article className="local-module" key={item.id}><div><strong>{student?.name ?? 'Learner'}</strong><p>{courseFor(state, item.courseId)?.title ?? 'Course'} · {item.date}</p></div><div><span className="chip">{item.status}</span>{canMark && item.status !== 'Present' && <button type="button" className="text-button" onClick={() => onMarkPresent(item.id)}>Mark present</button>}</div></article>
    })}</div> : <p className="muted">No attendance rows are visible to this account.</p>}
  </section>
}

function PeoplePanel({ state, profile, onUserStatus, onInvite }: { state: DemoState; profile: DemoProfile; onUserStatus: (userId: string, status: 'Active' | 'Suspended') => void; onInvite: (name: string, email: string, role: 'Student' | 'Teacher') => void }) {
  const currentUser = activeUserForProfile(state, profile)
  const canManage = profile.role === 'admin' || profile.role === 'client'
  const users = profile.role === 'admin'
    ? state.users
    : profile.role === 'client'
      ? state.users.filter(item => item.organization === currentUser?.organization)
      : learnersForProfile(state, profile)
  const [name, setName] = useState('')
  const [email, setEmail] = useState('')
  const [role, setRole] = useState<'Student' | 'Teacher'>('Student')
  const heading = profile.role === 'admin' ? 'Platform user directory' : profile.role === 'client' ? 'Teachers & students' : profile.role === 'teacher' ? 'Assigned learner roster' : 'Organization learners'
  function invite() {
    onInvite(name, email, role)
    setName('')
    setEmail('')
    setRole('Student')
  }
  function canChangeStatus(item: DemoState['users'][number]) {
    if (item.id === profile.userId) return false
    if (profile.role === 'admin') return item.role !== 'Platform Administrator'
    return profile.role === 'client' && (item.role === 'Student' || item.role === 'Teacher')
  }
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">People & role scope</p><h3>{heading}</h3></div><span className="count-badge">{users.length}</span></div>
    {canManage && <div className="local-form" style={{ marginBottom: '1.25rem' }}>
      <label>Full name<input value={name} onChange={event => setName(event.target.value)} placeholder="New teacher or student" /></label>
      <label>Email address<input type="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="person@institute.edu" autoComplete="off" /></label>
      <label>Role<select value={role} onChange={event => setRole(event.target.value === 'Teacher' ? 'Teacher' : 'Student')}><option value="Student">Student</option><option value="Teacher">Teacher</option></select></label>
      <button type="button" className="primary-button" onClick={invite}>Invite user</button>
    </div>}
    {users.length ? <div className="local-module-list">{users.map(item => <article className="local-module" key={item.id}><div><strong>{item.name}</strong><p>{item.role} · {item.organization}</p></div><div><span className="chip">{item.status}</span>{canChangeStatus(item) && <button type="button" className="text-button" onClick={() => onUserStatus(item.id, item.status === 'Active' ? 'Suspended' : 'Active')}>{item.status === 'Active' ? 'Suspend' : 'Activate'}</button>}</div></article>)}</div> : <p className="muted">No people are available in this role's scope.</p>}
  </section>
}

function OrganizationPanel({ state, profile, onResolve }: { state: DemoState; profile: DemoProfile; onResolve: (id: string) => void }) {
  const currentUser = state.users.find(item => item.id === profile.userId && item.status === 'Active')
  const organizations = profile.role === 'client'
    ? (currentUser ? [currentUser.organization] : [])
    : profile.role === 'admin' ? Array.from(new Set(state.users.map(item => item.organization))) : []
  const rows = organizations.map(organization => {
    const learners = state.users.filter(item => item.role === 'Student' && item.status === 'Active' && item.organization === organization)
    const courseIds = new Set(learners.flatMap(item => item.courseIds))
    const actions = state.organizationActions.filter(item => item.organization === organization)
    const tenant = state.tenants.find(item => item.name === organization)
    const activeTeachers = state.users.filter(item => item.role === 'Teacher' && item.status === 'Active' && item.organization === organization).length
    const activeStudents = state.users.filter(item => item.role === 'Student' && item.status === 'Active' && item.organization === organization).length
    return { organization, learners, courseIds, actions, tenant, activeTeachers, activeStudents }
  })
  return <div className="content-stack">{rows.map(row => <section className="panel" key={row.organization}>
    <div className="panel-heading"><div><p className="eyebrow">Organization</p><h3>{row.organization}</h3></div><span className="count-badge">{row.learners.length} learners</span></div>
    <div className="metric-grid">
      <article className="metric"><span>Active learners</span><strong>{row.learners.length}</strong><small>Active learner records</small></article>
      <article className="metric"><span>Courses</span><strong>{row.courseIds.size}</strong><small>Courses linked to learner enrollments</small></article>
      <article className="metric"><span>Open actions</span><strong>{row.actions.filter(item => item.status === 'Open').length}</strong><small>Follow-up queue</small></article>
      <article className="metric"><span>Deployment</span><strong>{row.tenant?.deploymentStatus ?? 'Live'}</strong><small>{row.tenant ? `${row.tenant.region} · updated ${new Date(row.tenant.lastDeployedAt).toLocaleDateString()}` : 'Workspace provisioned'}</small></article>
      <article className="metric"><span>Licensed seats</span><strong>{row.tenant ? row.tenant.licensedTeachers + row.tenant.licensedStudents : row.activeTeachers + row.activeStudents}</strong><small>{row.activeTeachers} teachers · {row.activeStudents} students active</small></article>
    </div>
    {row.actions.length ? <div className="local-module-list">{row.actions.map(item => <article className="local-module" key={item.id}><div><strong>{item.title}</strong><p>{item.description}</p></div><div><span className="chip">{item.status}</span>{profile.role === 'client' && item.status === 'Open' && <button type="button" className="text-button" onClick={() => onResolve(item.id)}>Resolve action</button>}</div></article>)}</div> : <p className="muted">No organization actions are recorded.</p>}
    </section>)}</div>
}

function AnalyticsPanel({ state, profile, submissions, attendance, schedule }: { state: DemoState; profile: DemoProfile; submissions: ReturnType<typeof submissionsForProfile>; attendance: DemoState['attendance']; schedule: ReturnType<typeof scheduleForProfile> }) {
  const currentUser = activeUserForProfile(state, profile)
  const learners = profile.role === 'client' || profile.role === 'teacher'
    || profile.role === 'teaching-assistant' || profile.role === 'evaluator'
    ? learnersForProfile(state, profile)
    : []
  let items: PanelItem[]
  if (profile.role === 'student' || profile.role === 'guardian') {
    items = [
      { title: 'Visible submissions', detail: 'Assignment and exam records in your role scope', status: String(submissions.length) },
      { title: 'Graded work', detail: 'Submissions with recorded scores', status: String(submissions.filter(item => item.status === 'Graded').length) },
      { title: 'Attendance records', detail: 'Visible attendance rows', status: String(attendance.length) },
    ]
  } else if (profile.role === 'teacher' || profile.role === 'teaching-assistant' || profile.role === 'evaluator') {
    items = [
      { title: 'Assigned learners', detail: 'Learners in your assigned course scope', status: String(learners.length) },
      { title: 'Awaiting review', detail: 'Submitted work in permitted grading queue', status: String(submissions.filter(item => item.status === 'Submitted').length) },
      { title: 'Attendance records', detail: 'Rows in assigned scope', status: String(attendance.length) },
    ]
  } else if (profile.role === 'client') {
    items = [
      { title: 'Learner population', detail: 'Learners associated with your organization', status: String(learners.length) },
      { title: 'Courses', detail: 'Courses linked to enrolled learners', status: String(new Set(learners.flatMap(item => item.courseIds)).size) },
      { title: 'Open actions', detail: 'Organization follow-up items', status: String(state.organizationActions.filter(item => item.organization === currentUser?.organization && item.status === 'Open').length) },
    ]
  } else if (profile.role === 'developer') {
    items = [
      { title: 'Tracked integrations', detail: 'Configured platform webhook connections', status: String(state.webhooks.length) },
      { title: 'Webhook failures', detail: 'Failed deliveries needing technical attention', status: String(state.webhooks.reduce((sum, item) => sum + item.failedDeliveries, 0)) },
      { title: 'Client service cases', detail: 'Aggregate open and active institution support cases', status: String(state.supportCases.filter(item => item.status !== 'Resolved').length) },
    ]
  } else if (profile.role === 'support') {
    items = [
      { title: 'Open support cases', detail: 'Cases not yet resolved', status: String(state.supportCases.filter(item => item.status !== 'Resolved').length) },
      { title: 'Active accounts', detail: 'Aggregate count only', status: String(state.users.filter(item => item.status === 'Active').length) },
      { title: 'Academic details', detail: 'Individual student records are not shown in this analytics view', status: 'Restricted' },
    ]
  } else {
    items = [
      { title: 'Schedule rows', detail: 'Schedule records in this role scope', status: String(schedule.length) },
      { title: 'Visible submissions', detail: 'Records permitted by this role', status: String(submissions.length) },
      { title: 'Visible activity', detail: 'Events in the current activity scope', status: String(activityForProfile(state, profile).length) },
    ]
  }
  return <PanelList eyebrow="Calculated from shared state" title="Workspace analytics" items={items} />
}

function ContentPanel({ state, profile, onCreateProduct, onProductTitle, onProductDescription, productTitle, productDescription, onPublishProduct, onPublishCourse, onAddMaterial }: { state: DemoState; profile: DemoProfile; onCreateProduct: () => void; onProductTitle: (value: string) => void; onProductDescription: (value: string) => void; productTitle: string; productDescription: string; onPublishProduct: (id: string) => void; onPublishCourse: (id: string) => void; onAddMaterial: (courseId: string, title: string, summary: string) => void }) {
  const visibleCourses = coursesForProfile(state, profile)
  const canAuthorMaterial = profile.role === 'teacher' || profile.role === 'admin'
  const [materialCourseId, setMaterialCourseId] = useState(visibleCourses[0]?.id ?? '')
  const [materialTitle, setMaterialTitle] = useState('')
  const [materialSummary, setMaterialSummary] = useState('')
  const materials = state.materials.filter(item => visibleCourses.some(course => course.id === item.courseId))
  if (profile.role === 'product-maintainer') {
    return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Product authoring</p><h3>Educational products</h3></div><span className="count-badge">{state.products.length}</span></div>
      <form className="local-login-form" onSubmit={event => { event.preventDefault(); onCreateProduct() }}><label>Product title<input value={productTitle} onChange={event => onProductTitle(event.target.value)} maxLength={200} required /></label><label>Description<textarea value={productDescription} onChange={event => onProductDescription(event.target.value)} maxLength={2000} rows={3} /></label><button type="submit" className="primary-button">Create draft</button></form>
      <div className="local-module-list">{state.products.map(product => <article className="local-module" key={product.id}><div><strong>{product.title} · v{product.version}</strong><p>{product.description} · updated {new Date(product.updatedAt).toLocaleDateString()}</p></div><div><span className="chip">{product.status}</span>{product.status !== 'Published' && <button type="button" className="text-button" onClick={() => onPublishProduct(product.id)}>Publish</button>}</div></article>)}</div>
    </section>
  }
  function submitMaterial() {
    onAddMaterial(materialCourseId, materialTitle, materialSummary)
    setMaterialTitle('')
    setMaterialSummary('')
  }
  return <section className="panel">
    <div className="panel-heading"><div><p className="eyebrow">Course catalog</p><h3>{profile.role === 'admin' ? 'Manage courses' : 'Courses available in role scope'}</h3></div><span className="count-badge">{visibleCourses.length}</span></div>
    {canAuthorMaterial && <div className="local-form" style={{ marginBottom: '1.25rem' }}>
      <p className="eyebrow" style={{ margin: 0 }}>Add learning material</p>
      <label>Course<select value={materialCourseId} onChange={event => setMaterialCourseId(event.target.value)}>{visibleCourses.map(course => <option key={course.id} value={course.id}>{course.title}</option>)}</select></label>
      <label>Material title<input value={materialTitle} onChange={event => setMaterialTitle(event.target.value)} placeholder="Week 2 · Session notes" /></label>
      <label>Summary<textarea value={materialSummary} onChange={event => setMaterialSummary(event.target.value)} rows={2} placeholder="What does this material cover?" /></label>
      <button type="button" className="primary-button" onClick={submitMaterial}>Add material</button>
    </div>}
    {visibleCourses.length ? <div className="local-module-list">{visibleCourses.map(course => <article className="local-module" key={course.id}>
      <div><strong>{course.title}</strong><p>{course.description}</p></div>
      <div><span className="chip">{course.status}</span>{profile.role === 'admin' && course.status === 'Draft' && <button type="button" className="text-button" onClick={() => onPublishCourse(course.id)}>Publish course</button>}</div>
    </article>)}</div> : <p className="muted">No courses are available in this role's scope.</p>}
    <div className="panel-heading" style={{ marginTop: '1.5rem' }}><div><p className="eyebrow">Teaching materials</p><h3>Shared resources</h3></div><span className="count-badge">{materials.length}</span></div>
    {materials.length ? <div className="local-module-list">{materials.map(material => <article className="local-module" key={material.id}>
      <div><strong>{material.title}</strong><p>{material.summary}</p><small className="muted">{visibleCourses.find(course => course.id === material.courseId)?.title ?? 'Course'} · added by {material.addedBy} on {new Date(material.addedAt).toLocaleDateString()}</small></div>
    </article>)}</div> : <p className="muted">No learning materials have been added for these courses yet.</p>}
  </section>
}

function OperationsPanel({ state, profile, onResolve, onSupportStatus }: { state: DemoState; profile: DemoProfile; onResolve: (id: string) => void; onSupportStatus: (id: string, status: 'In progress' | 'Resolved') => void }) {
  if (profile.role === 'client') return <OrganizationPanel state={state} profile={profile} onResolve={onResolve} />
  if (profile.role === 'support') return <SupportScopePanel state={state} profile={profile} onStatus={onSupportStatus} />
  const items: PanelItem[] = [
    { title: 'Support cases', detail: 'Open operational cases in synthetic local queue', status: String(state.supportCases.filter(item => item.status !== 'Resolved').length) },
    { title: 'Organization actions', detail: 'Open follow-up items across organizations', status: String(state.organizationActions.filter(item => item.status === 'Open').length) },
    { title: 'Webhook failures', detail: 'Failed integration deliveries requiring review', status: String(state.webhooks.reduce((sum, item) => sum + item.failedDeliveries, 0)) },
  ]
  return <PanelList eyebrow="Platform operations" title="Operational overview" items={items} />
}

function IntegrationsPanel({ state, profile }: { state: DemoState; profile: DemoProfile }) {
  if (profile.role !== 'developer') return <PanelList eyebrow="Integrations" title="No integration access" items={[]} />
  return <PanelList eyebrow="Integration health" title="Webhook delivery summary" items={state.webhooks.map(item => ({ title: item.name, detail: item.endpoint, status: item.status + ' · ' + item.successfulDeliveries + ' delivered · ' + item.failedDeliveries + ' failed' }))} />
}

function MessagesPanel({ state, profile, messages, onMarkRead, onSend }: { state: DemoState; profile: DemoProfile; messages: DemoState['messages']; onMarkRead: (id: string) => void; onSend: (subject: string, body: string, recipientId: string) => boolean }) {
  const unread = messages.filter(message => !message.readBy.includes(profile.userId)).length
  const activeSender = activeUserForProfile(state, profile)
  const recipients = messageRecipientsForProfile(state, profile)
  const [recipientId, setRecipientId] = useState(recipients[0]?.id ?? '')
  const selectedRecipientId = recipients.some(user => user.id === recipientId) ? recipientId : ''
  const [subject, setSubject] = useState('')
  const [body, setBody] = useState('')
  function send() {
    if (!selectedRecipientId || !subject.trim() || !body.trim()) return
    if (!onSend(subject, body, selectedRecipientId)) return
    setSubject('')
    setBody('')
  }
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Recipient-scoped communication</p><h3>Inbox</h3></div><span className="count-badge">{unread} unread</span></div>
    {!activeSender && <p className="muted" role="status">Messaging is unavailable because this account is inactive.</p>}
    {activeSender && recipients.length === 0 && <p className="muted" role="status">No active recipients are currently available for this account.</p>}
    {activeSender && recipients.length > 0 && <div className="local-form" style={{ marginBottom: '1.25rem' }}>
      <p className="eyebrow" style={{ margin: 0 }}>{profile.role === 'student' ? 'Ask a teacher' : 'Send a message'}</p>
      <label>Recipient<select value={selectedRecipientId} onChange={event => setRecipientId(event.target.value)}><option value="" disabled>Select an active recipient</option>{recipients.map(user => <option key={user.id} value={user.id}>{user.name} · {user.role}</option>)}</select></label>
      <label>Subject<input value={subject} onChange={event => setSubject(event.target.value)} placeholder="Request a meeting or ask a question" /></label>
      <label>Message<textarea value={body} onChange={event => setBody(event.target.value)} rows={3} placeholder="Describe what you need help with" /></label>
      <button type="button" className="primary-button" disabled={!selectedRecipientId || !subject.trim() || !body.trim()} onClick={send}>{profile.role === 'student' ? 'Send request' : 'Send message'}</button>
    </div>}
    {messages.length ? <div className="local-module-list">{messages.map(message => <article className="local-module" key={message.id}><div><strong>{message.subject}</strong><p>{message.body}</p><small className="muted">From {state.users.find(person => person.id === message.senderId)?.name ?? 'Platform'} · {new Date(message.sentAt).toLocaleString()}</small></div><div><span className="chip">{message.readBy.includes(profile.userId) ? 'Read' : 'Unread'}</span>{!message.readBy.includes(profile.userId) && <button type="button" className="text-button" onClick={() => onMarkRead(message.id)}>Mark as read</button>}</div></article>)}</div> : <p className="muted">No messages are addressed to this account.</p>}
  </section>
}

function SettingsPanel({ state, profile }: { state: DemoState; profile: DemoProfile }) {
  const user = state.users.find(item => item.id === profile.userId)
  return <PanelList eyebrow="Configuration" title={profile.role === 'admin' ? 'Platform configuration summary' : 'Account information'} items={[
    { title: 'Signed-in persona', detail: profile.name + ' · ' + profile.email, status: profile.role },
    { title: 'Account status', detail: user?.status ?? 'Unavailable', status: 'Current' },
    { title: 'State environment', detail: 'Shared local browser demo; not a production identity session', status: 'Demo only' },
  ]} />
}

function FinanceScopePanel({ invoices, users }: { invoices: DemoState['invoices']; users: DemoState['users'] }) {
  const totalOutstanding = invoices.filter(invoice => invoice.status !== 'Paid').reduce((sum, invoice) => sum + invoice.amountInr, 0)
  return (
    <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">Synthetic finance ledger</p><h3>Invoices and account status</h3></div><span className="count-badge">{invoices.length} invoices</span></div>
      <p className="muted">Outstanding demo balance: {totalOutstanding.toLocaleString('en-IN', { style: 'currency', currency: 'INR' })}. This is presentation data only; no payment, refund, or settlement occurs here.</p>
      {invoices.length ? <div className="local-module-list">{invoices.map(invoice => {
        const student = users.find(user => user.id === invoice.studentId)
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
