import { useEffect, useMemo, useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import {
  DEMO_PROFILES, activityForProfile, advanceDemoProgress, courseFor, createDemoProduct, gradeDemoSubmission, loadDemoState, percentage,
  publishDemoCourse, publishDemoProduct, resetDemoState, resolveDemoOrganizationAction, saveDemoState, setDemoUserStatus, submitDemoAssignment,
  subscribeDemoState, updateDemoAttendance,
} from './LocalDemoStore'
import type { DemoProfile, DemoRole, DemoState, DemoSubmission } from './LocalDemoStore'

type Section = 'overview' | 'learn' | 'classes' | 'assignments' | 'assessments' | 'results' | 'attendance' | 'calendar' | 'content' | 'learners' | 'grading' | 'reviews' | 'communication' | 'analytics' | 'programs' | 'reports' | 'billing' | 'account' | 'products' | 'editor' | 'integrations' | 'api' | 'releases' | 'academics' | 'people' | 'schedule' | 'operations' | 'finance' | 'settings' | 'security' | 'progress' | 'invoices' | 'payments' | 'submissions' | 'messages' | 'profile' | 'organization-actions'

const navigation: Record<DemoRole, Array<{ id: Section; label: string }>> = {
  student: [
    { id: 'overview', label: 'Today' }, { id: 'learn', label: 'My Learning' }, { id: 'classes', label: 'My Classes' },
    { id: 'assignments', label: 'Assignments' }, { id: 'assessments', label: 'Assessments' }, { id: 'results', label: 'Results & Feedback' },
    { id: 'attendance', label: 'Attendance' }, { id: 'calendar', label: 'Calendar' }, { id: 'messages', label: 'Messages' }, { id: 'profile', label: 'My Profile' },
  ],
  teacher: [
    { id: 'overview', label: 'Today' }, { id: 'classes', label: 'My Classes' }, { id: 'learners', label: 'My Learners' },
    { id: 'content', label: 'Learning Content' }, { id: 'assignments', label: 'Assignments' }, { id: 'grading', label: 'Grading Queue' },
    { id: 'assessments', label: 'Assessments' }, { id: 'reviews', label: 'Academic Reviews' }, { id: 'communication', label: 'Announcements' },
    { id: 'analytics', label: 'Class Analytics' }, { id: 'attendance', label: 'Attendance Register' }, { id: 'calendar', label: 'Calendar' },
  ],
  client: [
    { id: 'overview', label: 'Organization Overview' }, { id: 'programs', label: 'Programs & Products' }, { id: 'learners', label: 'Organization Members' },
    { id: 'organization-actions', label: 'Action Centre' }, { id: 'reports', label: 'Outcomes & Reports' }, { id: 'billing', label: 'Billing & Entitlements' }, { id: 'account', label: 'Organization Account' },
  ],
  developer: [
    { id: 'overview', label: 'Product Studio' }, { id: 'products', label: 'My Products' }, { id: 'editor', label: 'Create Product' },
    { id: 'releases', label: 'Publishing & Releases' }, { id: 'integrations', label: 'Integrations' }, { id: 'api', label: 'API & Delivery Status' },
  ],
  admin: [
    { id: 'overview', label: 'Platform Overview' }, { id: 'academics', label: 'Academics & Curriculum' }, { id: 'people', label: 'People & Roles' },
    { id: 'schedule', label: 'Scheduling' }, { id: 'attendance', label: 'Attendance Register' }, { id: 'assessments', label: 'Assessments' }, { id: 'operations', label: 'Operations & Audit' },
    { id: 'finance', label: 'Finance & Licensing' }, { id: 'reports', label: 'Platform Reports' }, { id: 'settings', label: 'Configuration' }, { id: 'security', label: 'Security' },
  ],
  guardian: [
    { id: 'overview', label: 'Learner Overview' }, { id: 'progress', label: 'Learning Progress' }, { id: 'schedule', label: 'Schedule' },
    { id: 'results', label: 'Results & Feedback' }, { id: 'communication', label: 'School Communication' },
  ],
  'exam-controller': [
    { id: 'overview', label: 'Exam Operations' }, { id: 'assessments', label: 'Assessments' }, { id: 'schedule', label: 'Exam Schedule' },
    { id: 'submissions', label: 'Submission Status' }, { id: 'reports', label: 'Exam Reports' },
  ],
  'finance-admin': [
    { id: 'overview', label: 'Finance Overview' }, { id: 'invoices', label: 'Invoices' }, { id: 'payments', label: 'Payments & Receipts' }, { id: 'reports', label: 'Finance Reports' },
  ],
  'teaching-assistant': [
    { id: 'overview', label: 'Teaching Today' }, { id: 'classes', label: 'Assigned Classes' }, { id: 'assignments', label: 'Submission Triage' }, { id: 'content', label: 'Learning Content' }, { id: 'calendar', label: 'Calendar' },
  ],
  evaluator: [
    { id: 'overview', label: 'Evaluation Overview' }, { id: 'grading', label: 'Assigned Grading' }, { id: 'assessments', label: 'Assessments' }, { id: 'reviews', label: 'Review History' },
  ],
  mentor: [
    { id: 'overview', label: 'Mentoring Overview' }, { id: 'learners', label: 'Assigned Learners' }, { id: 'progress', label: 'Milestones & Progress' }, { id: 'communication', label: 'Follow-ups' },
  ],
  support: [
    { id: 'overview', label: 'Support Overview' }, { id: 'people', label: 'Account Lookup' }, { id: 'operations', label: 'Service Activity' },
  ],
  auditor: [
    { id: 'overview', label: 'Audit Overview' }, { id: 'operations', label: 'Activity & Audit Trail' }, { id: 'reports', label: 'Read-only Reports' },
  ],
}
const sectionTitles: Record<Section, string> = {
  overview: 'Overview', learn: 'My Learning', classes: 'Classes', assignments: 'Assignments', assessments: 'Assessments', results: 'Results & Feedback',
  attendance: 'Attendance', calendar: 'Calendar', content: 'Learning Content', learners: 'Learners', grading: 'Grading Queue', reviews: 'Academic Reviews',
  communication: 'Communication', analytics: 'Analytics', programs: 'Programs & Products', reports: 'Reports', billing: 'Billing & Entitlements',
  account: 'Organization Account', products: 'Products', editor: 'Create Product', integrations: 'Integrations', api: 'API & Delivery Status', releases: 'Publishing & Releases',
  academics: 'Academics & Curriculum', people: 'People & Roles', schedule: 'Schedule', operations: 'Operations & Audit', finance: 'Finance & Licensing',
  settings: 'Configuration', security: 'Security', progress: 'Learning Progress', invoices: 'Invoices', payments: 'Payments & Receipts',
  submissions: 'Submission Status', messages: 'Messages', profile: 'My Profile', 'organization-actions': 'Organization Action Centre',
}

export function LocalDashboard({ initialRole = 'student', onSignOut }: { initialRole?: DemoRole; onSignOut?: () => void }) {
  const profile = DEMO_PROFILES.find(item => item.role === initialRole) ?? DEMO_PROFILES.find(item => item.role === 'student')!
  const [activeSection, setActiveSection] = useState<Section>('overview')
  const [state, setState] = useState<DemoState>(() => loadDemoState())
  useEffect(() => subscribeDemoState(() => setState(loadDemoState())), [])
  const [notice, setNotice] = useState('')
  const [productTitle, setProductTitle] = useState('')
  const [productDescription, setProductDescription] = useState('')
  const nav = navigation[profile.role]
  const visibleCourses = useMemo(() => {
    const linkedLearnerId = ['guardian', 'mentor'].includes(profile.role) ? 'student-asha' : profile.userId
    const courseIds = state.users.find(user => user.id === linkedLearnerId)?.courseIds ?? []
    const assignedCourseIds = new Set(state.users.find(user => user.id === profile.userId)?.courseIds ?? [])
    if (['student', 'guardian', 'teacher', 'teaching-assistant', 'mentor', 'evaluator'].includes(profile.role)) {
      return state.courses.filter(course => courseIds.includes(course.id)
        && (profile.role !== 'mentor' || assignedCourseIds.has(course.id))
        && course.status === 'Published')
    }
    if (profile.role === 'client') return state.courses.filter(course => course.status === 'Published')
    return state.courses
  }, [profile, state])

  function commit(next: DemoState, message: string) {
    saveDemoState(next)
    setState(next)
    setNotice(message)
  }
  function handleAssignmentSubmit(id: string) {
    const next = submitDemoAssignment(state, id, profile)
    if (next === state) setNotice('This assignment is no longer available to submit.')
    else commit(next, 'Assignment submitted and saved. The teacher grading queue has been updated.')
  }
  function handleGrade(event: FormEvent<HTMLFormElement>, submission: DemoSubmission) {
    event.preventDefault()
    const form = new FormData(event.currentTarget)
    const next = gradeDemoSubmission(state, submission.id, Number(form.get('score')), String(form.get('feedback') ?? ''), profile)
    if (next === state) setNotice('Check the score (0–100) and make sure this submission is awaiting grading.')
    else commit(next, 'Grade published to the learner record. Sign in as that learner to see the same result.')
  }
  function handleCreateProduct(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const next = createDemoProduct(state, productTitle, productDescription, profile)
    if (next === state) { setNotice('Enter a product title before saving.'); return }
    commit(next, 'Product draft saved to this browser and listed in My Products.')
    setProductTitle('')
    setProductDescription('')
    setActiveSection('products')
  }
  function handlePublishProduct(id: string) {
    const next = publishDemoProduct(state, id, profile)
    if (next !== state) commit(next, 'Product published in the local demo catalog. The client product list now reflects this change.')
  }
  function handleAdvanceProgress(courseId: string) {
    const next = advanceDemoProgress(state, courseId, profile)
    if (next === state) {
      setNotice('Progress was not changed. Only an active student can complete a lesson in an assigned published course.')
      return
    }
    commit(next, 'Lesson completion saved. Your progress and the linked guardian summary now reflect this change.')
  }
  function handlePublishCourse(courseId: string) {
    const next = publishDemoCourse(state, courseId, profile)
    if (next === state) {
      setNotice('Course was not published. Only an active platform administrator can publish a draft course.')
      return
    }
    commit(next, 'Course published to the shared demo catalog. The client catalog now reflects the published status.')
  }
  function handleResolveOrganizationAction(actionId: string) {
    const next = resolveDemoOrganizationAction(state, actionId, profile)
    if (next === state) {
      setNotice('This organization action cannot be resolved from the current profile or organization.')
      return
    }
    commit(next, 'Organization action resolved and saved for the active institution.')
  }
  function handleAttendanceUpdate(id: string, status: 'Present' | 'Late' | 'Absent') {
    const next = updateDemoAttendance(state, id, status, profile)
    if (next === state) {
      setNotice('No attendance change was made. Verify the role, learner status and assigned course.')
      return
    }
    commit(next, 'Attendance updated and saved. Learner and guardian summaries now use the updated record.')
  }
  function handleResetDemo() {
    if (!window.confirm('Reset the shared local demo dataset to its seed records? This clears workflow changes saved in this browser.')) return
    const next = resetDemoState()
    setState(next)
    setActiveSection('overview')
    setNotice('The shared local demo dataset has been reset to its seed records.')
  }

  function renderOverview(): ReactNode {
    const currentUser = state.users.find(user => user.id === profile.userId && user.status === 'Active')
    const activeLearners = state.users.filter(user => user.role === 'Student' && user.status === 'Active'
      && (profile.role !== 'client' || user.organization === currentUser?.organization))
    const assignedCourseIds = new Set(currentUser?.courseIds ?? [])
    const studentId = ['guardian', 'mentor'].includes(profile.role) ? 'student-asha' : profile.userId
    const scopedSubmissions = submissionsForProfile(state, profile)
    const ownSubmissions = scopedSubmissions.filter(item => item.studentId === profile.userId)
    const linkedSubmissions = scopedSubmissions.filter(item => item.studentId === studentId)
    const pendingSubmissions = scopedSubmissions.filter(item => item.status === 'Submitted')
    const learnersWithWork = new Set(scopedSubmissions.map(item => item.studentId))
    const scopedExams = state.schedule.filter(item => item.kind === 'Exam'
      && item.status === 'Scheduled' && item.participantIds.includes(profile.userId))
    const submittedExamWork = scopedSubmissions.filter(item => item.status !== 'Assigned')
    const publishedProducts = state.products.filter(item => item.status === 'Published')
    const studentCourses = (state.users.find(user => user.id === studentId)?.courseIds ?? [])
      .filter(id => profile.role !== 'mentor' || assignedCourseIds.has(id))
    const attendanceEntries = state.attendance.filter(item => item.studentId === studentId && studentCourses.includes(item.courseId))
    const attendanceRate = percentage(attendanceEntries.filter(item => item.status !== 'Absent').length, attendanceEntries.length)
    const averageProgress = percentage(studentCourses.reduce((sum, id) => sum + progressFor(state, studentId, id), 0), studentCourses.length)
    let metrics: Array<{ label: string; value: string; detail: string }> = []

    switch (profile.role) {
      case 'student':
        metrics = [
          { label: 'Enrolled courses', value: String(visibleCourses.length), detail: 'Your active learning catalog' },
          { label: 'Average progress', value: percentage(visibleCourses.reduce((sum, course) => sum + progressFor(state, profile.userId, course.id), 0), visibleCourses.length) + '%', detail: 'Calculated from your course records' },
          { label: 'Work awaiting completion', value: String(ownSubmissions.filter(item => item.status !== 'Graded').length), detail: 'Assignments not yet graded' },
          { label: 'Attendance', value: attendanceRate + '%', detail: 'Based on saved attendance entries' },
        ]; break
      case 'teacher':
      case 'teaching-assistant':
      case 'evaluator':
        metrics = [
          { label: 'Assigned courses', value: String(visibleCourses.length), detail: 'Courses linked to this staff profile' },
          { label: 'Active learners', value: String(learnersWithWork.size), detail: 'Learners with coursework in assigned courses' },
          { label: 'Awaiting grading', value: String(pendingSubmissions.length), detail: 'Submitted work not yet graded' },
          { label: 'Published products', value: String(publishedProducts.length), detail: 'Saved catalog records' },
        ]; break
      case 'client':
        metrics = [
          { label: 'Active learners', value: String(activeLearners.length), detail: 'Active learner memberships' },
          { label: 'Published products', value: String(publishedProducts.length), detail: 'Available catalog records' },
          { label: 'Course completion average', value: percentage(activeLearners.reduce((sum, learner) => sum + learner.courseIds.reduce((s, id) => s + progressFor(state, learner.id, id), 0), 0), activeLearners.reduce((sum, learner) => sum + learner.courseIds.length, 0)) + '%', detail: 'Calculated across enrolled learner-course records' },
          { label: 'Work awaiting grading', value: String(pendingSubmissions.length), detail: 'Institution-wide submission queue' },
        ]; break
      case 'developer':
        metrics = [
          { label: 'Total products', value: String(state.products.length), detail: 'Saved product records' },
          { label: 'Drafts', value: String(state.products.filter(item => item.status === 'Draft').length), detail: 'Products that can be edited' },
          { label: 'Published versions', value: String(publishedProducts.length), detail: 'Published catalog entries' },
          { label: 'Failed webhook deliveries', value: String(state.webhooks.reduce((sum, hook) => sum + hook.failedDeliveries, 0)), detail: 'Sample delivery history, not live endpoint health' },
        ]; break
      case 'admin':
        metrics = [
          { label: 'Platform identities', value: String(state.users.length), detail: 'Profiles in this local demo dataset' },
          { label: 'Active learners', value: String(activeLearners.length), detail: 'Active student records' },
          { label: 'Course records', value: String(state.courses.length), detail: 'Published and draft courses' },
          { label: 'Awaiting grading', value: String(pendingSubmissions.length), detail: 'Submitted work awaiting review' },
        ]; break
      case 'guardian':
        metrics = [
          { label: 'Learner courses', value: String(studentCourses.length), detail: 'Linked learner: Asha Sharma' },
          { label: 'Learner progress', value: averageProgress + '%', detail: 'Average of linked course records' },
          { label: 'Unfinished work', value: String(linkedSubmissions.filter(item => item.status !== 'Graded').length), detail: 'Linked learner assignments' },
          { label: 'Attendance', value: attendanceRate + '%', detail: 'Calculated from recorded entries' },
        ]; break
      case 'exam-controller':
        metrics = [
          { label: 'Scheduled exams', value: String(scopedExams.length), detail: 'Scheduled exams assigned to this exam controller' },
          { label: 'Submissions received', value: String(submittedExamWork.length), detail: 'Submitted or graded work in assigned exam courses' },
          { label: 'Pending grading', value: String(pendingSubmissions.length), detail: 'Awaiting a published result' },
        ]; break
      case 'finance-admin':
        metrics = [{ label: 'Finance API', value: 'Not connected', detail: 'Live finance records require the platform backend.' }, { label: 'Demo environment', value: 'Offline', detail: 'No payments are recorded or simulated as successful.' }]; break
      case 'mentor':
        metrics = [{ label: 'Linked learners', value: '1', detail: 'Seeded demo mentoring relationship' }, { label: 'Learner progress', value: averageProgress + '%', detail: 'Computed from linked learner course records' }, { label: 'Unfinished work', value: String(linkedSubmissions.filter(item => item.status !== 'Graded').length), detail: 'Linked learner assignments' }]; break
      case 'support':
        metrics = [{ label: 'Recent activity events', value: String(activityForProfile(state, profile).length), detail: 'Events visible to this support profile' }, { label: 'Platform accounts', value: String(state.users.filter(user => user.organization === 'UEP Platform').length), detail: 'Synthetic platform-only identities' }, { label: 'Support service', value: 'Not connected', detail: 'No external support system is contacted.' }]; break
      case 'auditor':
        metrics = [{ label: 'Recorded activity events', value: String(state.activity.length), detail: 'Local demonstration history' }, { label: 'Data source', value: 'Browser storage', detail: 'Not the production audit service.' }]; break
      default: metrics = [{ label: 'Workspace', value: profile.label, detail: 'Role-specific local workspace' }]
    }
    return <div className="local-content-stack">
      <section className="local-welcome"><p className="eyebrow">{profile.label} workspace</p><h2>{welcomeFor(profile.role)}</h2><p>{profile.description}</p></section>
      <div className="local-metric-grid">{metrics.map(metric => <article className="local-metric" key={metric.label}><span>{metric.label}</span><strong>{metric.value}</strong><small>{metric.detail}</small></article>)}</div>
      <section className="local-panel"><div className="local-panel-heading"><div><p className="eyebrow">Next actions</p><h3>{profile.role === 'student' ? 'Continue learning' : profile.role === 'teacher' ? 'Teaching and assessment queue' : profile.role === 'developer' ? 'Product delivery' : profile.role === 'client' ? 'Organization status' : 'Recent workspace activity'}</h3></div></div>
        {profile.role === 'student' ? <CourseList state={state} courses={visibleCourses} userId={profile.userId} onOpen={() => setActiveSection('learn')} onAdvance={handleAdvanceProgress} /> :
          ['teacher', 'evaluator', 'teaching-assistant'].includes(profile.role) ? <SubmissionList state={state} profile={profile} submissions={pendingSubmissions.slice(0, 4)} onGrade={handleGrade} onSubmit={handleAssignmentSubmit} /> :
          profile.role === 'developer' ? <ProductList products={state.products.slice(0, 4)} onPublish={handlePublishProduct} /> :
          profile.role === 'client' ? <ProductList products={publishedProducts.slice(0, 4)} onPublish={handlePublishProduct} readOnly /> : <ActivityList state={state} profile={profile} />}
      </section>
      <p className="local-data-notice">LOCAL DEMO DATA · Values are calculated from records stored in this browser. Records are synthetic sample data, not live institutional or production telemetry.</p>
    </div>
  }

  function renderSection(): ReactNode {
    switch (activeSection) {
      case 'overview': return renderOverview()
      case 'learn':
      case 'programs':
      case 'academics': return <section className="local-panel"><PanelHeading eyebrow="Catalog" title={sectionTitles[activeSection]} detail={visibleCourses.length + ' accessible course records'} /><CourseList state={state} courses={visibleCourses} userId={['client', 'admin'].includes(profile.role) ? 'student-asha' : profile.role === 'guardian' ? 'student-asha' : profile.userId} onOpen={() => setNotice('Course selected. Course and progress values come from the local records.')} onAdvance={profile.role === 'student' ? handleAdvanceProgress : undefined} onPublishCourse={profile.role === 'admin' ? handlePublishCourse : undefined} /><ProductList products={state.products.filter(item => item.status === 'Published')} onPublish={handlePublishProduct} readOnly /></section>
      case 'classes':
      case 'calendar':
      case 'schedule': return <ScheduleView state={state} profile={profile} />
      case 'assignments':
      case 'grading':
      case 'submissions': {
        const permitted = submissionsForProfile(state, profile)
        const visible = ['student', 'guardian', 'mentor'].includes(profile.role)
          ? permitted
          : permitted.filter(item => item.status === 'Submitted' || item.status === 'Graded')
        return <section className="local-panel"><PanelHeading eyebrow="Learning workflow" title={sectionTitles[activeSection]} detail="Only records inside this demo profile's learner/course scope are displayed." /><SubmissionList state={state} profile={profile} submissions={visible} onGrade={handleGrade} onSubmit={handleAssignmentSubmit} /></section>
      }
      case 'results':
      case 'progress': {
        const linkedLearnerId = ['guardian', 'mentor'].includes(profile.role) ? 'student-asha' : profile.userId
        const resultItems = submissionsForProfile(state, profile).filter(item => item.status === 'Graded')
        return <section className="local-panel"><PanelHeading eyebrow="Learning outcomes" title={sectionTitles[activeSection]} detail="Only the current learner or explicitly linked learner's authorized results are shown." /><CourseList state={state} courses={visibleCourses} userId={linkedLearnerId} onOpen={() => setNotice('Progress reflects the saved learner-course record.')} /><SubmissionList state={state} profile={profile} submissions={resultItems} onGrade={handleGrade} onSubmit={handleAssignmentSubmit} readOnly /></section>
      }
      case 'attendance': return <AttendanceView state={state} profile={profile} onUpdate={handleAttendanceUpdate} />
      case 'learners':
      case 'people': return <PeopleView state={state} profile={profile} onStatus={(id, status) => commit(setDemoUserStatus(state, id, status, profile), 'Membership status saved to the demo dataset.')} />
      case 'organization-actions': {
        const organization = state.users.find(user => user.id === profile.userId)?.organization
        const actions = state.organizationActions.filter(action => action.organization === organization)
        return <section className="local-panel"><PanelHeading eyebrow="Institution operations" title="Organization action centre" detail="Resolution state is shared across this browser's separate demo sign-ins for the same institution." />{actions.length ? <div className="local-record-list">{actions.map(action => <article className="local-record" key={action.id}><div className="local-record-main"><strong>{action.title}</strong><p>{action.description}</p><small>Updated {new Date(action.updatedAt).toLocaleString()}</small></div><div className="local-record-side"><span className={'local-status status-' + action.status.toLowerCase()}>{action.status}</span>{profile.role === 'client' && action.status === 'Open' && <button className="primary-button small-button" type="button" onClick={() => handleResolveOrganizationAction(action.id)}>Mark resolved</button>}</div></article>)}</div> : <p className="local-empty">No organization actions are available in this local demo scope.</p>}<p className="local-data-notice">These are synthetic offline demo actions, not live tasks from an institution's production system.</p></section>
      }
      case 'products':
      case 'releases': return <section className="local-panel"><PanelHeading eyebrow="Product lifecycle" title={sectionTitles[activeSection]} detail="Draft creation and publishing update the browser-persisted product catalog." /><button className="primary-button" onClick={() => setActiveSection('editor')}>Create product draft</button><ProductList products={state.products} onPublish={handlePublishProduct} /></section>
      case 'editor': return <section className="local-panel"><PanelHeading eyebrow="Product authoring" title="Create an educational product" detail="Save a draft, then publish it from My Products." /><form className="local-form" onSubmit={handleCreateProduct}><label>Product title<input required maxLength={120} value={productTitle} onChange={event => setProductTitle(event.target.value)} placeholder="e.g. Applied Data Engineering" /></label><label>Description<textarea rows={4} value={productDescription} onChange={event => setProductDescription(event.target.value)} placeholder="Outcomes, intended audience and scope" /></label><button className="primary-button" type="submit">Save draft</button></form></section>
      case 'content': return <section className="local-panel"><PanelHeading eyebrow="Teaching resources" title="Learning content" detail="Published course records and their lesson counts." /><CourseList state={state} courses={visibleCourses} userId={profile.userId} onOpen={() => setNotice('Lesson selected from the local demo course.')} /></section>
      case 'assessments': {
        const permitted = submissionsForProfile(state, profile)
        const examsOnly = profile.role === 'exam-controller'
        return <section className="local-panel"><PanelHeading eyebrow="Assessment center" title="Assessment schedule and readiness" detail="Events and submissions are restricted to this persona's assigned learner/course scope." /><ScheduleView state={state} profile={profile} examsOnly={examsOnly} /><SubmissionList state={state} profile={profile} submissions={permitted} onGrade={handleGrade} onSubmit={handleAssignmentSubmit} readOnly={profile.role === 'student' || profile.role === 'guardian' || profile.role === 'mentor'} /></section>
      }
      case 'analytics':
      case 'reports':
        if (profile.role === 'finance-admin') return <section className="local-panel"><PanelHeading eyebrow="Finance boundary" title="Finance reports" detail="Financial reports require authoritative backend records." /><div className="local-honest-state"><strong>Finance API not connected in offline demo mode</strong><p>No invoice totals, payment states or account balances are fabricated.</p></div></section>
        if (profile.role === 'exam-controller') return <ExamReportsView state={state} profile={profile} />
        return <AnalyticsView state={state} profile={profile} />
      case 'integrations':
      case 'api': return <IntegrationView state={state} />
      case 'communication':
      case 'messages': return <section className="local-panel"><PanelHeading eyebrow="Communication" title="Recent communication activity" detail="These local events do not send an external message." /><ActivityList state={state} profile={profile} /></section>
      case 'operations':
      case 'security': return <section className="local-panel"><PanelHeading eyebrow="Governance" title={sectionTitles[activeSection]} detail="Inspect local events; this is not the production audit service." /><ActivityList state={state} profile={profile} /><p className="local-data-notice">Production sessions, identity federation, authorization and immutable audit history require the connected platform server.</p></section>
      case 'billing':
      case 'finance':
      case 'invoices':
      case 'payments': return <section className="local-panel"><PanelHeading eyebrow="Finance boundary" title={sectionTitles[activeSection]} detail="Financial records must come from the authenticated platform API." /><div className="local-honest-state"><strong>Finance API not connected in offline demo mode</strong><p>No fabricated invoice, payment, receipt, balance or subscription status is shown. Start the platform backend and sign in with an authorized finance account to use the production workspace.</p></div></section>
      case 'settings': return <section className="local-panel"><PanelHeading eyebrow="Workspace configuration" title="Configuration" detail="Local-only controls for this demonstration profile." /><dl className="local-definition-list"><dt>Name</dt><dd>{profile.name}</dd><dt>Role</dt><dd>{profile.label}</dd><dt>Email</dt><dd>{profile.email}</dd><dt>Data environment</dt><dd>Browser-persisted sample data</dd><dt>Identity provider</dt><dd>Not connected</dd></dl>{profile.role === 'admin' && <div className="local-reset-zone"><h3>Reset demo data</h3><p>Restore seed records and clear saved workflow changes across browser demo sign-ins.</p><button className="text-button" type="button" onClick={handleResetDemo}>Reset to seed data</button></div>}</section>
      case 'account':
      case 'profile': return <section className="local-panel"><PanelHeading eyebrow="Workspace details" title={sectionTitles[activeSection]} detail="Current signed-in demo identity." /><dl className="local-definition-list"><dt>Name</dt><dd>{profile.name}</dd><dt>Role</dt><dd>{profile.label}</dd><dt>Email</dt><dd>{profile.email}</dd><dt>Data environment</dt><dd>Browser-persisted sample data</dd><dt>Identity provider</dt><dd>Not connected</dd></dl></section>
      case 'reviews': {
        const permitted = submissionsForProfile(state, profile).filter(item => item.status !== 'Assigned')
        return <section className="local-panel"><PanelHeading eyebrow="Review workflow" title="Academic reviews" detail="Only submitted and graded work in this persona's scope is included." /><SubmissionList state={state} profile={profile} submissions={permitted} onGrade={handleGrade} onSubmit={handleAssignmentSubmit} readOnly={profile.role !== 'teacher' && profile.role !== 'evaluator'} /></section>
      }
      default: return <section className="local-panel"><PanelHeading eyebrow={profile.label} title={sectionTitles[activeSection]} detail="Restricted to this signed-in demo persona." /><p>Select an available workspace item from your navigation.</p></section>
    }
  }

  return <main className="local-dashboard">
    <header className="local-hero"><div><p className="eyebrow">Universal Education Platform · Local Demo</p><h1>{profile.role === 'developer' ? 'Product Studio' : profile.role === 'student' ? 'Your learning space.' : profile.role === 'teacher' ? 'Your teaching workspace.' : profile.role === 'client' ? 'Your organization.' : 'Your workspace.'}</h1><p className="local-hero-copy">Signed in as {profile.name}. This workspace shows only the navigation assigned to this demo persona.</p></div><div className="local-hero-actions"><span className="local-mode-badge">Offline · Sample dataset</span><button className="local-signout" onClick={onSignOut}>Sign out</button></div></header>
    <div className="local-app-layout"><aside className="local-sidebar"><div className="local-profile"><span className="local-avatar">{profile.name.split(' ').map(part => part[0]).slice(0, 2).join('')}</span><div><strong>{profile.name}</strong><small>{profile.label}</small></div></div><nav aria-label={profile.label + ' navigation'}>{nav.map(item => <button key={item.id} className={activeSection === item.id ? 'local-nav-item active' : 'local-nav-item'} onClick={() => setActiveSection(item.id)}>{item.label}</button>)}</nav><p className="local-sidebar-foot">Local demo profile<br />Sign out to choose another profile.</p></aside>
      <section className="local-main"><header className="local-workspace-header"><div><p className="eyebrow">{profile.label} workspace</p><h2>{sectionTitles[activeSection]}</h2></div><span className="local-mode-badge">Saved in this browser</span></header>{notice && <div className="local-notice" role="status"><span>{notice}</span><button onClick={() => setNotice('')} aria-label="Dismiss message">×</button></div>}{renderSection()}</section>
    </div>
  </main>
}

function welcomeFor(role: DemoRole): string {
  switch (role) {
    case 'student': return 'Keep your learning moving.'
    case 'teacher': return 'Teach, review and support your learners.'
    case 'client': return 'See your organization’s learning activity.'
    case 'developer': return 'Build and publish educational products.'
    case 'admin': return 'Monitor and manage the platform.'
    case 'guardian': return 'Stay informed about your learner.'
    case 'exam-controller': return 'Coordinate assessment delivery.'
    case 'finance-admin': return 'Manage finance through authoritative records.'
    case 'teaching-assistant': return 'Support classes and learner submissions.'
    case 'evaluator': return 'Review assigned learner work.'
    case 'mentor': return 'Track learner milestones and follow-ups.'
    case 'support': return 'Review support-relevant account activity.'
    case 'auditor': return 'Inspect recorded activity in read-only mode.'
  }
  return 'Your role-specific workspace.'
}
function PanelHeading({ eyebrow, title, detail }: { eyebrow: string; title: string; detail?: string }) {
  return <div className="local-panel-heading"><div><p className="eyebrow">{eyebrow}</p><h3>{title}</h3>{detail && <p>{detail}</p>}</div></div>
}
function CourseList({ state, courses, userId, onOpen, onAdvance, onPublishCourse }: {
  state: DemoState
  courses: DemoState['courses']
  userId: string
  onOpen: () => void
  onAdvance?: (courseId: string) => void
  onPublishCourse?: (courseId: string) => void
}) {
  if (!courses.length) return <p className="local-empty">No courses are assigned to this profile yet.</p>
  return <div className="local-record-list">{courses.map(course => {
    const progress = progressFor(state, userId, course.id)
    return <article className="local-record" key={course.id}><div className="local-record-main"><strong>{course.title}</strong><p>{course.description}</p><small>{course.category} · {course.lessonCount} lessons · {course.status}</small></div><div className="local-record-side"><strong>{progress}%</strong><small>Progress</small><button className="text-button" onClick={onOpen}>Open course</button>{onAdvance && progress < 100 && course.status === 'Published' && <button className="primary-button small-button" type="button" onClick={() => onAdvance(course.id)}>Complete next lesson</button>}{onPublishCourse && course.status === 'Draft' && <button className="primary-button small-button" type="button" onClick={() => onPublishCourse(course.id)}>Publish course</button>}</div></article>
  })}</div>
}
function SubmissionList({ state, profile, submissions, onGrade, onSubmit, readOnly = false }: { state: DemoState; profile: DemoProfile; submissions: DemoSubmission[]; onGrade: (event: FormEvent<HTMLFormElement>, submission: DemoSubmission) => void; onSubmit: (id: string) => void; readOnly?: boolean }) {
  if (!submissions.length) return <p className="local-empty">No items match this workspace right now.</p>
  return <div className="local-record-list">{submissions.map(item => {
    const course = courseFor(state, item.courseId)
    const student = state.users.find(user => user.id === item.studentId)
    const canSubmit = profile.role === 'student' && !readOnly && item.studentId === profile.userId && item.status === 'Assigned'
    const canGrade = !readOnly && ['teacher', 'evaluator', 'teaching-assistant'].includes(profile.role) && item.status === 'Submitted'
    return <article className="local-record" key={item.id}><div className="local-record-main"><strong>{item.title}</strong><p>{course?.title ?? 'Course'}{!['student', 'guardian'].includes(profile.role) ? ' · ' + (student?.name ?? 'Learner') : ''}</p><small>Due {new Date(item.dueAt).toLocaleDateString()} · <span className={'local-status status-' + item.status.toLowerCase()}>{item.status}</span></small>{item.feedback && <p className="local-feedback">Feedback: {item.feedback}</p>}</div><div className="local-record-side">{item.score !== undefined && <><strong>{item.score}/100</strong><small>Published score</small></>}{canSubmit && <button className="primary-button small-button" onClick={() => onSubmit(item.id)}>Submit assignment</button>}{canGrade && <form className="local-grade-form" onSubmit={event => onGrade(event, item)}><label>Score / 100<input name="score" type="number" min="0" max="100" defaultValue="92" required /></label><label>Feedback<input name="feedback" defaultValue="Good work. Review the comments and continue." maxLength={500} /></label><button className="primary-button small-button" type="submit">Publish grade</button></form>}</div></article>
  })}</div>
}
function ScheduleView({ state, profile, examsOnly = false }: { state: DemoState; profile: DemoProfile; examsOnly?: boolean }) {
  const assignedCourseIds = new Set(state.users.find(user => user.id === profile.userId)?.courseIds ?? [])
  const scopedExamsOnly = examsOnly || profile.role === 'exam-controller'
  const items = state.schedule.filter(item => {
    if (scopedExamsOnly && item.kind !== 'Exam') return false
    if (profile.role === 'guardian') return item.participantIds.includes('student-asha')
    if (profile.role === 'mentor') return item.participantIds.includes('student-asha') && assignedCourseIds.has(item.courseId)
    if (['client', 'admin', 'auditor'].includes(profile.role)) return true
    return item.participantIds.includes(profile.userId)
  }).sort((a, b) => a.startsAt.localeCompare(b.startsAt))
  return <section className="local-panel"><PanelHeading eyebrow="Schedule records" title={scopedExamsOnly ? 'Assessment schedule' : 'Your upcoming schedule'} detail="Times are taken from saved local schedule records and restricted to this persona's scope." />{items.length ? <div className="local-record-list">{items.map(item => <article className="local-record" key={item.id}><div className="local-record-main"><strong>{item.title}</strong><p>{item.kind} · {courseFor(state, item.courseId)?.title ?? 'Program activity'}</p><small>{new Date(item.startsAt).toLocaleString()} · {item.durationMinutes} minutes</small></div><span className={'local-status status-' + item.status.toLowerCase()}>{item.status}</span></article>)}</div> : <p className="local-empty">No scheduled items are linked to this profile.</p>}</section>
}
function AttendanceView({ state, profile, onUpdate }: { state: DemoState; profile: DemoProfile; onUpdate: (id: string, status: 'Present' | 'Late' | 'Absent') => void }) {
  const assignedCourseIds = new Set(state.users.find(user => user.id === profile.userId)?.courseIds ?? [])
  const linkedLearnerId = ['guardian', 'mentor'].includes(profile.role) ? 'student-asha' : profile.userId
  const linkedCourses = new Set(state.users.find(user => user.id === linkedLearnerId)?.courseIds ?? [])
  const canManage = ['teacher', 'admin'].includes(profile.role)
  const items = ['student', 'guardian'].includes(profile.role)
    ? state.attendance.filter(item => item.studentId === linkedLearnerId)
    : profile.role === 'mentor'
      ? state.attendance.filter(item => item.studentId === linkedLearnerId && assignedCourseIds.has(item.courseId))
      : profile.role === 'teacher'
        ? state.attendance.filter(item => assignedCourseIds.has(item.courseId))
        : profile.role === 'admin' ? state.attendance : []
  const scopedItems = profile.role === 'guardian' ? items.filter(item => linkedCourses.has(item.courseId)) : items
  const present = scopedItems.filter(item => item.status !== 'Absent').length
  return <section className="local-panel"><PanelHeading eyebrow="Attendance register" title="Attendance summary" detail={scopedItems.length + ' saved attendance entries in this profile scope'} /><div className="local-metric-grid"><article className="local-metric"><span>Attendance rate</span><strong>{percentage(present, scopedItems.length)}%</strong><small>Present and late entries divided by recorded sessions.</small></article><article className="local-metric"><span>Absent</span><strong>{scopedItems.filter(item => item.status === 'Absent').length}</strong><small>Count from attendance records.</small></article></div><div className="local-record-list">{scopedItems.slice().sort((a, b) => b.date.localeCompare(a.date)).map(item => <article className="local-record" key={item.id}><div className="local-record-main"><strong>{state.users.find(user => user.id === item.studentId)?.name ?? 'Learner'}</strong><p>{courseFor(state, item.courseId)?.title ?? 'Course'}</p><small>{item.date}</small></div><div className="local-record-side"><span className={'local-status status-' + item.status.toLowerCase()}>{item.status}</span>{canManage && <div className="local-attendance-actions"><button type="button" className="text-button" disabled={item.status === 'Present'} onClick={() => onUpdate(item.id, 'Present')}>Present</button><button type="button" className="text-button" disabled={item.status === 'Late'} onClick={() => onUpdate(item.id, 'Late')}>Late</button><button type="button" className="text-button" disabled={item.status === 'Absent'} onClick={() => onUpdate(item.id, 'Absent')}>Absent</button></div>}</div></article>)}</div>{canManage && <p className="local-data-notice">Only attendance records for this staff member's assigned courses can be changed in this local demo.</p>}</section>
}
function PeopleView({ state, profile, onStatus }: { state: DemoState; profile: DemoProfile; onStatus: (id: string, status: 'Active' | 'Suspended') => void }) {
  const assignedCourseIds = new Set(state.users.find(user => user.id === profile.userId)?.courseIds ?? [])
  const users = ['teacher', 'teaching-assistant'].includes(profile.role)
    ? state.users.filter(user => user.role === 'Student' && user.courseIds.some(id => assignedCourseIds.has(id)))
    : profile.role === 'client'
      ? state.users.filter(user => user.organization === 'Northstar Learning Institute')
      : profile.role === 'guardian'
        ? state.users.filter(user => user.id === 'student-asha')
        : profile.role === 'mentor'
          ? state.users.filter(user => user.id === 'student-asha' && user.courseIds.some(id => assignedCourseIds.has(id)))
          : profile.role === 'support'
            ? state.users.filter(user => user.organization === 'UEP Platform')
            : profile.role === 'admin' ? state.users : []
  const canManage = profile.role === 'admin'
  const redactDetails = profile.role === 'support'
  return <section className="local-panel"><PanelHeading eyebrow="People directory" title={['guardian', 'mentor'].includes(profile.role) ? 'Linked learner' : 'Authorized directory'} detail={users.length + ' records in this profile scope'} /><div className="local-record-list">{users.map(user => <article className="local-record" key={user.id}><div className="local-record-main"><strong>{user.name}</strong><p>{user.role} · {user.organization}</p>{redactDetails ? <small>Contact and course details are redacted for support lookup.</small> : <small>{user.email} · {user.courseIds.length} course associations</small>}</div><div className="local-record-side"><span className={'local-status status-' + user.status.toLowerCase()}>{user.status}</span>{canManage && user.id !== profile.userId && <button className="text-button" onClick={() => onStatus(user.id, user.status === 'Active' ? 'Suspended' : 'Active')}>{user.status === 'Active' ? 'Suspend demo user' : 'Reactivate demo user'}</button>}</div></article>)}</div>{!canManage && <p className="local-data-notice">Contact details are synthetic demo identities. Production directory access is enforced by the backend.</p>}</section>
}
function ProductList({ products, onPublish, readOnly = false }: { products: DemoState['products']; onPublish: (id: string) => void; readOnly?: boolean }) {
  if (!products.length) return <p className="local-empty">No product records yet. Create a product draft to begin.</p>
  return <div className="local-record-list">{products.map(product => <article className="local-record" key={product.id}><div className="local-record-main"><strong>{product.title}</strong><p>{product.description}</p><small>Version {product.version} · Updated {new Date(product.updatedAt).toLocaleString()}</small></div><div className="local-record-side"><span className={'local-status status-' + product.status.toLowerCase().replace(' ', '-')}>{product.status}</span>{!readOnly && product.status !== 'Published' && <button className="primary-button small-button" onClick={() => onPublish(product.id)}>Publish</button>}</div></article>)}</div>
}
function AnalyticsView({ state, profile }: { state: DemoState; profile: DemoProfile }) {
  const actor = state.users.find(user => user.id === profile.userId)
  const assignedCourseIds = new Set(actor?.courseIds ?? [])
  const linkedLearnerId = profile.role === 'mentor' ? 'student-asha' : null
  const organization = actor?.organization
  const students = state.users.filter(user => user.role === 'Student' && user.status === 'Active'
    && (profile.role !== 'client' || user.organization === organization)
    && (!['teacher', 'teaching-assistant', 'evaluator'].includes(profile.role) || user.courseIds.some(id => assignedCourseIds.has(id)))
    && (profile.role !== 'mentor' || user.id === linkedLearnerId))
  const courseIds = new Set(students.flatMap(student => student.courseIds).filter(id =>
    !['teacher', 'teaching-assistant', 'evaluator', 'mentor'].includes(profile.role) || assignedCourseIds.has(id)))
  const courses = state.courses.filter(course => courseIds.has(course.id) && (course.status === 'Published' || profile.role === 'admin' || profile.role === 'auditor'))
  const values = students.flatMap(student => student.courseIds.filter(courseId => courseIds.has(courseId)).map(courseId => progressFor(state, student.id, courseId)))
  const studentIds = new Set(students.map(student => student.id))
  const pending = state.submissions.filter(item => item.status === 'Submitted' && studentIds.has(item.studentId) && courseIds.has(item.courseId)).length
  return <section className="local-panel"><PanelHeading eyebrow="Computed report" title="Learning outcomes and operations" detail={profile.role === 'client' ? 'Institution-scoped aggregates computed from synthetic learner-course records.' : 'Aggregates are calculated from records stored in browser storage.'} /><div className="local-metric-grid"><article className="local-metric"><span>Learner records</span><strong>{students.length}</strong><small>Counted from records in this report scope.</small></article><article className="local-metric"><span>Enrollment records</span><strong>{values.length}</strong><small>Student-course associations in scope.</small></article><article className="local-metric"><span>Average progress</span><strong>{percentage(values.reduce((sum, value) => sum + value, 0), values.length)}%</strong><small>Average of enrolled learner progress records.</small></article><article className="local-metric"><span>Pending grading</span><strong>{pending}</strong><small>Submitted items awaiting a result.</small></article></div><div className="local-record-list">{courses.map(course => { const enrolled = students.filter(student => student.courseIds.includes(course.id)); const avg = percentage(enrolled.reduce((sum, student) => sum + progressFor(state, student.id, course.id), 0), enrolled.length); return <article className="local-record" key={course.id}><div className="local-record-main"><strong>{course.title}</strong><p>{enrolled.length} learner-course records</p><small>{course.category} · {course.status}</small></div><div className="local-record-side"><strong>{avg}%</strong><small>Average progress</small></div></article> })}</div></section>
}
function ExamReportsView({ state, profile }: { state: DemoState; profile: DemoProfile }) {
  const exams = state.schedule.filter(item => item.kind === 'Exam' && item.participantIds.includes(profile.userId))
  const courseIds = new Set(exams.map(item => item.courseId))
  const submissions = state.submissions.filter(item => courseIds.has(item.courseId))
  const pending = submissions.filter(item => item.status === 'Submitted').length
  return <section className="local-panel"><PanelHeading eyebrow="Assessment operations" title="Exam readiness and submissions" detail="Only exam events linked to this exam-controller profile are included." /><div className="local-metric-grid"><article className="local-metric"><span>Assigned exams</span><strong>{exams.length}</strong><small>Scoped schedule entries.</small></article><article className="local-metric"><span>Course submissions</span><strong>{submissions.length}</strong><small>Submissions in assigned exam-course scope.</small></article><article className="local-metric"><span>Awaiting review</span><strong>{pending}</strong><small>Submitted items without a published result.</small></article></div><div className="local-record-list">{exams.map(item => <article className="local-record" key={item.id}><div className="local-record-main"><strong>{item.title}</strong><p>{courseFor(state, item.courseId)?.title ?? 'Course'}</p><small>{new Date(item.startsAt).toLocaleString()} · {item.durationMinutes} minutes</small></div><span className={'local-status status-' + item.status.toLowerCase()}>{item.status}</span></article>)}</div></section>
}

function IntegrationView({ state }: { state: DemoState }) {
  return <section className="local-panel"><PanelHeading eyebrow="Integration inventory" title="Configured demo integrations" detail="Endpoints and delivery totals are sample records. No HTTP requests are sent." /><div className="local-honest-state"><strong>Live connectivity is not tested in offline mode.</strong><p>Real API uptime and webhook delivery health are not fabricated; the figures below describe the local sample dataset only.</p></div><div className="local-record-list">{state.webhooks.map(hook => <article className="local-record" key={hook.id}><div className="local-record-main"><strong>{hook.name}</strong><p>{hook.endpoint}</p><small>{hook.successfulDeliveries} sample successes · {hook.failedDeliveries} sample failures</small></div><span className={'local-status status-' + hook.status.toLowerCase().replace(' ', '-')}>{hook.status} (sample)</span></article>)}</div></section>
}
function ActivityList({ state, profile }: { state: DemoState; profile: DemoProfile }) {
  const activity = activityForProfile(state, profile)
  if (!activity.length) return <p className="local-empty">No activity is available in this persona's local data scope.</p>
  return <div className="local-record-list">{activity.map(item => <article className="local-record" key={item.id}><div className="local-record-main"><strong>{item.action}</strong><p>{item.detail}</p><small>{item.actor} · {new Date(item.at).toLocaleString()}</small></div></article>)}</div>
}
