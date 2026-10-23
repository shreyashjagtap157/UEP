import { useState } from 'react'

type LocalRole = 'student' | 'client' | 'developer'

type RoleConfig = {
  label: string
  eyebrow: string
  title: string
  description: string
  accent: string
  stats: Array<{ label: string; value: string; note: string }>
  modules: Array<{ title: string; detail: string; status: string }>
}

const roleConfigs: Record<LocalRole, RoleConfig> = {
  student: {
    label: 'Student',
    eyebrow: 'Learner workspace',
    title: 'Keep your semester moving.',
    description: 'A focused view of classes, assessments, progress, and the next useful action in your learning plan.',
    accent: 'coral',
    stats: [
      { label: 'Course progress', value: '68%', note: '+12% this month' },
      { label: 'Due this week', value: '04', note: '2 are ready to submit' },
      { label: 'Attendance', value: '94%', note: 'Excellent standing' },
    ],
    modules: [
      { title: 'Web Application Architecture', detail: 'Module 5 · API integration', status: 'Continue' },
      { title: 'Assessment checkpoint', detail: 'Design systems reflection', status: 'Due Friday' },
      { title: 'Next live class', detail: 'Thursday · 10:30 · Studio 2', status: 'View details' },
    ],
  },
  client: {
    label: 'Client',
    eyebrow: 'Institution workspace',
    title: 'See the learning operation clearly.',
    description: 'A decision-ready snapshot for client and institution teams managing cohorts, delivery, and outcomes.',
    accent: 'mint',
    stats: [
      { label: 'Active learners', value: '248', note: '+18 this intake' },
      { label: 'Completion rate', value: '82%', note: 'Across 12 courses' },
      { label: 'Open actions', value: '07', note: '3 need attention today' },
    ],
    modules: [
      { title: 'Cohort performance', detail: 'North campus · Spring intake', status: 'Open report' },
      { title: 'Teaching coverage', detail: '18 of 20 sessions staffed', status: 'Review roster' },
      { title: 'Billing & entitlement', detail: 'Renewal window in 21 days', status: 'View account' },
    ],
  },
  developer: {
    label: 'Developer',
    eyebrow: 'Platform workspace',
    title: 'Build with confidence.',
    description: 'A local control room for integrations, API health, releases, and the contracts that keep the platform dependable.',
    accent: 'blue',
    stats: [
      { label: 'API health', value: '99.98%', note: 'No incidents today' },
      { label: 'Webhooks', value: '03', note: 'All deliveries healthy' },
      { label: 'Release', value: '0.15', note: 'Ready for review' },
    ],
    modules: [
      { title: 'Contract parity', detail: 'OpenAPI and client types aligned', status: 'Inspect' },
      { title: 'Integration credentials', detail: '2 sandbox keys · 1 production key', status: 'Manage' },
      { title: 'Recent deployment', detail: 'Platform server · 14 minutes ago', status: 'View logs' },
    ],
  },
}

function readRole(): LocalRole {
  const value = new URLSearchParams(window.location.search).get('role')
  return value === 'client' || value === 'developer' ? value : 'student'
}

export function LocalDashboard({ initialRole, onSignOut }: { initialRole?: LocalRole; onSignOut?: () => void }) {
  const [role, setRole] = useState<LocalRole>(initialRole ?? readRole)
  const [lastAction, setLastAction] = useState('Select a profile to preview its workspace.')
  const config = roleConfigs[role]

  function switchRole(nextRole: LocalRole) {
    setRole(nextRole)
    window.history.replaceState({}, '', `/?role=${nextRole}`)
    setLastAction(`${roleConfigs[nextRole].label} dashboard loaded locally.`)
  }

  return (
    <main className={`local-dashboard local-dashboard-${config.accent}`}>
      <header className="local-hero">
        <div>
          <p className="eyebrow">Offline presentation mode</p>
          <h1>Universal Education Platform</h1>
          <p className="local-hero-copy">A complete local walkthrough of the platform experience, with remote services intentionally paused.</p>
        </div>
        <div className="local-hero-actions"><span className="local-mode-badge">No network required</span>{onSignOut && <button className="local-signout" onClick={onSignOut}>Sign out</button>}</div>
      </header>

      <section className="local-role-switcher" aria-label="Local dashboard profiles">
        <div>
          <p className="eyebrow">Preview as</p>
          <h2>{config.label} dashboard</h2>
        </div>
        <div className="local-role-options" role="tablist" aria-label="Choose a local role">
          {(Object.keys(roleConfigs) as LocalRole[]).map(option => (
            <button
              key={option}
              className={role === option ? 'local-role-option active' : 'local-role-option'}
              onClick={() => switchRole(option)}
              role="tab"
              aria-selected={role === option}
            >
              {roleConfigs[option].label}
            </button>
          ))}
        </div>
      </section>

      <section className="local-intro">
        <p className="eyebrow">{config.eyebrow}</p>
        <h2>{config.title}</h2>
        <p>{config.description}</p>
      </section>

      <section className="local-stat-grid" aria-label={`${config.label} summary`}>
        {config.stats.map(stat => (
          <article className="local-stat" key={stat.label}>
            <span>{stat.label}</span>
            <strong>{stat.value}</strong>
            <small>{stat.note}</small>
          </article>
        ))}
      </section>

      <section className="local-workspace-grid">
        <div className="local-module-panel">
          <div className="panel-heading"><div><p className="eyebrow">Workspace modules</p><h3>What needs your attention</h3></div><span className="count-badge">{config.modules.length}</span></div>
          <div className="local-module-list">
            {config.modules.map(module => (
              <article className="local-module" key={module.title}>
                <div><strong>{module.title}</strong><p>{module.detail}</p></div>
                <button className="text-button" onClick={() => setLastAction(`${module.title}: ${module.status} selected.`)}>{module.status}</button>
              </article>
            ))}
          </div>
        </div>
        <aside className="local-action-panel">
          <p className="eyebrow">Local walkthrough</p>
          <h3>Presentation checkpoint</h3>
          <p>{lastAction}</p>
          <button className="primary-button" onClick={() => setLastAction(`${config.label} dashboard is ready for a live walkthrough.`)}>Run dashboard check</button>
          <small>All values on this screen are local demo data.</small>
        </aside>
      </section>
    </main>
  )
}
