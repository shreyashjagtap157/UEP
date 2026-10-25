import { useState } from 'react'
import {
  administratorStats,
  clientStats,
  demoRoleLabels,
  developerStats,
  localDemoData,
  learnersForOrganization,
  organizationMetrics,
  studentStats,
  teacherStats,
} from './localDemoData'
import type { LocalRole } from './localDemoData'
import type { LocalAccount } from './LocalExperience'

type LocalSection = string
type Stat = { label: string; value: string; note: string }
type QuickAction = [id: string, label: string]

export function LocalDashboard({ account, onSignOut }: { account: LocalAccount; onSignOut: () => void }) {
  const [section, setSection] = useState<LocalSection>('today')
  const [notice, setNotice] = useState('Ready for the walkthrough.')
  const role = account.role
  const nav = navigationFor(role)

  function openSection(next: string) {
    setSection(next)
    setNotice(`${sectionTitle(role, next)} workspace opened.`)
  }

  // The shared presentation store mutates localDemoData in place; recompute metrics on every subscribed rerender.
  const stats = statsFor(role, account.name)

  return (
    <div className="app-frame">
      <aside className="sidebar">
        <div>
          <p className="product-kicker">Universal Education</p>
          <h1 className="product-title">{demoRoleLabels[role]} Workspace</h1>
          <p className="muted small">{account.name}</p>
        </div>
        <nav aria-label="Primary navigation" className="nav-list">
          {nav.map(item => <button key={item.id} className={section === item.id ? 'nav-item active' : 'nav-item'} onClick={() => openSection(item.id)}>{item.label}</button>)}
        </nav>
        <div className="sidebar-footer">
          <p className="signed-in">{account.email}</p>
          <p className="muted small">Role-scoped local session</p>
          <button className="text-button" onClick={onSignOut}>Sign out</button>
        </div>
      </aside>

      <main className="workspace">
        <header className="workspace-header">
          <div><p className="eyebrow">Local seeded dataset · {demoRoleLabels[role]}</p><h2>{sectionTitle(role, section)}</h2></div>
          <span className="version-badge">Demo data · v0.15.0</span>
        </header>

        {section === 'today' && <Today role={role} accountName={account.name} stats={stats} onOpen={openSection} />}
        {section === 'learning' && <StudentLearning accountName={account.name} />}
        {section === 'classes' && <TeacherClasses teacherName={account.name} />}
        {section === 'students' && <Students teacherName={account.name} />}
        {section === 'tasks' && <Assignments role={role} accountName={account.name} />}
        {section === 'assessments' && <Assessments role={role} accountName={account.name} />}
        {section === 'results' && <Results role={role} accountName={account.name} />}
        {section === 'schedule' && <Schedule role={role} accountName={account.name} />}
        {section === 'content' && <Products role={role} />}
        {section === 'analytics' && <Analytics role={role} accountName={account.name} />}
        {section === 'organization' && <Organizations role={role} accountName={account.name} />}
        {section === 'people' && <People role={role} accountName={account.name} />}
        {section === 'operations' && <Operations />}
        {section === 'releases' && <Releases />}
        {section === 'integrations' && <Integrations />}
        {section === 'messages' && <Messages role={role} />}
        {section === 'guardian' && <Guardian accountName={account.name} />}
        {section === 'settings' && <Settings />}

        <section className="panel" aria-live="polite"><p className="eyebrow">Walkthrough status</p><p>{notice}</p><small className="muted">Metrics are calculated from the shared local dataset and filtered to the signed-in persona where the data model supports ownership.</small></section>
      </main>
    </div>
  )
}

function navigationFor(role: LocalRole): Array<{ id: string; label: string }> {
  switch (role) {
    case 'student': return [{ id: 'today', label: 'Today' }, { id: 'learning', label: 'My Learning' }, { id: 'tasks', label: 'Tasks & Assignments' }, { id: 'assessments', label: 'Exams & Assessments' }, { id: 'results', label: 'Results' }, { id: 'schedule', label: 'Calendar & Classes' }, { id: 'content', label: 'Learning Content' }, { id: 'messages', label: 'Messages' }]
    case 'teacher': return [{ id: 'today', label: 'Today' }, { id: 'classes', label: 'My Classes' }, { id: 'students', label: 'Students' }, { id: 'content', label: 'Content' }, { id: 'tasks', label: 'Assignments' }, { id: 'assessments', label: 'Assessments' }, { id: 'results', label: 'Grading & Reviews' }, { id: 'schedule', label: 'Schedule' }, { id: 'analytics', label: 'Analytics' }, { id: 'messages', label: 'Communication' }]
    case 'client': return [{ id: 'today', label: 'Overview' }, { id: 'organization', label: 'Programs & Cohorts' }, { id: 'people', label: 'People' }, { id: 'analytics', label: 'Outcomes & Reports' }, { id: 'schedule', label: 'Delivery Schedule' }, { id: 'operations', label: 'Operations' }, { id: 'messages', label: 'Communication' }, { id: 'settings', label: 'Account' }]
    case 'developer': return [{ id: 'today', label: 'Developer Overview' }, { id: 'content', label: 'Products' }, { id: 'releases', label: 'Releases' }, { id: 'integrations', label: 'Integrations' }, { id: 'analytics', label: 'Quality & Telemetry' }, { id: 'operations', label: 'Platform Operations' }]
    case 'administrator': return [{ id: 'today', label: 'Overview' }, { id: 'organization', label: 'Organizations' }, { id: 'people', label: 'People & Roles' }, { id: 'content', label: 'Catalog' }, { id: 'assessments', label: 'Assessments' }, { id: 'analytics', label: 'Reports & Analytics' }, { id: 'operations', label: 'Operations' }, { id: 'schedule', label: 'Scheduling' }, { id: 'settings', label: 'Configuration' }]
    case 'guardian': return [{ id: 'today', label: 'Today' }, { id: 'guardian', label: 'Learner Progress' }, { id: 'schedule', label: 'Calendar' }, { id: 'results', label: 'Results & Feedback' }, { id: 'messages', label: 'Messages' }]
  }
}

function sectionTitle(role: LocalRole, section: string) {
  if (section === 'today') return role === 'developer' ? 'Platform control room' : role === 'client' ? 'Organization overview' : role === 'administrator' ? 'Platform overview' : 'Today'
  return navigationFor(role).find(item => item.id === section)?.label ?? 'Workspace'
}

function statsFor(role: LocalRole, accountName: string): Stat[] {
  if (role === 'student') { const s = studentStats(accountName); return [{ label: 'Average course progress', value: `${s.averageProgress}%`, note: `${s.activeCourses} courses in progress` }, { label: 'Assignments due', value: String(s.pendingAssignments), note: `${s.submittedAssignments} submitted and awaiting review` }, { label: 'Completed courses', value: String(s.completedCourses), note: 'Calculated from enrolled courses' }] }
  if (role === 'teacher') { const s = teacherStats(accountName); return [{ label: 'Learners', value: String(s.learners), note: `${s.activeLearners} active · ${s.atRiskLearners} at risk` }, { label: 'Pending grading', value: String(s.pendingGrading), note: 'Submissions requiring review' }, { label: 'Average attendance', value: `${s.averageAttendance}%`, note: `${s.averageCompletion}% average completion` }] }
  if (role === 'client') {
    const org = localDemoData.organizations.find(item => item.name === accountName)
    if (!org) return <PanelList eyebrow="Analytics" title="Organization scope unavailable" items={[]} />
    const s = organizationMetrics(org.name)
    return <PanelList eyebrow="Analytics" title="Calculated organization metrics" items={[{ title: 'Program completion', detail: `${s.completion}% average across ${s.learners} learner records`, status: 'Current' }, { title: 'Learner population', detail: `${s.activeLearners} active learners across ${s.courses} scoped courses`, status: 'Current' }, { title: 'Open actions', detail: `${org.actions} actions requiring follow-up`, status: org.actions ? 'Attention' : 'Clear' }]} />
  }
  const s = clientStats()
  return <PanelList eyebrow="Analytics" title="Platform outcome metrics" items={[{ title: 'Program completion', detail: `${s.completion}% average across seeded learner records`, status: 'Current' }, { title: 'Learner population', detail: `${s.learners} learner records across ${s.organizations} organizations`, status: 'Current' }, { title: 'Open actions', detail: `${s.openActions} actions requiring follow-up`, status: 'Attention' }]} />
}
function Organizations({ role, accountName }: { role: LocalRole; accountName: string }) {
  const organizations = role === 'client' ? localDemoData.organizations.filter(item => item.name === accountName) : localDemoData.organizations
  return <PanelList eyebrow="Organizations" title={role === 'client' ? 'My organization' : 'Client and institution portfolio'} items={organizations.map(item => {
    const metrics = organizationMetrics(item.name)
    return { title: item.name, detail: `${metrics.learners} learner records (${metrics.activeLearners} active) · ${metrics.courses} courses · ${metrics.completion}% average completion`, status: `${item.actions} open actions` }
  })} />
}
function People({ role, accountName }: { role: LocalRole; accountName: string }) {
  const learners = role === 'client' ? learnersForOrganization(accountName) : localDemoData.students
  return <PanelList eyebrow="People & roles" title={role === 'client' ? 'Learners in my organization' : 'Platform membership'} items={learners.map(item => ({ title: item.name, detail: `${item.cohort} · ${item.status}`, status: item.organization }))} />
}
function Operations() { return <PanelList eyebrow="Operations" title="Open operational work" items={[...localDemoData.organizations.map(item => ({ title: `${item.name} operational queue`, detail: `${item.actions} actions currently open`, status: item.actions > 5 ? 'Attention' : 'Healthy' })), { title: 'Platform build', detail: 'API release has unresolved compilation checks', status: 'Blocked' }]} /> }
function Releases() { return <PanelList eyebrow="Release engineering" title="Tracked releases" items={localDemoData.releases.map(item => ({ title: `${item.name} · ${item.version}`, detail: `${item.passed}/${item.checks} checks passed`, status: item.status }))} /> }
function Integrations() { return <PanelList eyebrow="Integrations" title="Connected platform capabilities" items={[{ title: 'Identity provider', detail: 'Keycloak integration configured for non-local mode', status: 'Configured' }, { title: 'Live learning', detail: 'LiveKit client integration present in web application', status: 'Configured' }, { title: 'Platform API', detail: 'REST API contract used by the web application', status: 'Configured' }]} /> }
function Messages({ role }: { role: LocalRole }) { return <PanelList eyebrow="Communication" title="Recent messages" items={[{ title: role === 'student' ? 'Instructor feedback received' : 'Platform announcement', detail: 'A new message is available in your workspace.', status: 'Unread' }, { title: 'Schedule update', detail: 'Tomorrow’s learning session details have been updated.', status: 'Read' }]} /> }
function Guardian({ accountName }: { accountName: string }) {
  const learner = localDemoData.students.find(item => item.guardianName === accountName)
  const items = learner ? [
    { title: 'Learning progress', detail: `${learner.completion}% completion`, status: 'Current' },
    { title: 'Attendance', detail: `${learner.attendance}% attendance`, status: 'Current' },
    { title: 'Current cohort', detail: learner.cohort, status: learner.status },
  ] : []
  return <PanelList eyebrow="Linked learner" title={learner?.name ?? 'No linked learner'} items={items} />
}
function Settings() { return <PanelList eyebrow="Configuration" title="Workspace configuration" items={[{ title: 'Organization timezone', detail: 'Asia/Kolkata', status: 'Configured' }, { title: 'Locale', detail: 'English (India)', status: 'Configured' }, { title: 'Demo environment', detail: 'Local seeded dataset; no remote identity provider', status: 'Active' }]} /> }
function PanelList({ eyebrow, title, items }: { eyebrow: string; title: string; items: Array<{ title: string; detail: string; status: string }> }) {
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">{eyebrow}</p><h3>{title}</h3></div><span className="count-badge">{items.length}</span></div>{items.length ? <div className="local-module-list">{items.map(item => <article className="local-module" key={`${item.title}-${item.detail}`}><div><strong>{item.title}</strong><p>{item.detail}</p></div><span className="chip">{item.status}</span></article>)}</div> : <p className="muted">No records are available for this role and scope.</p>}</section>
}
