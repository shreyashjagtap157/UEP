import { useState } from 'react'

export type PersonaRole = 'DEV_ADMIN' | 'CLIENT_ADMIN' | 'TEACHER' | 'STUDENT'

export interface AuthPersona {
  id: PersonaRole
  title: string
  subtitle: string
  badge: string
  badgeClass: string
  description: string
  organization: string
  permissionsCount: number
  dashboardFocus: string
}

export const personas: AuthPersona[] = [
  {
    id: 'DEV_ADMIN',
    title: 'Developer Platform Administrator',
    subtitle: 'System Control Plane & Multi-Tenant Authority',
    badge: 'Developer Admin',
    badgeClass: 'chip-danger',
    description: 'Full authorization over all tenants, commercial licensing, system health, platform federation, global operations, and organization provisioning.',
    organization: 'Universal Platform Infrastructure',
    permissionsCount: 56,
    dashboardFocus: 'Global Operations, System Health, Commercial Licensing, Audit Trail & Federation',
  },
  {
    id: 'CLIENT_ADMIN',
    title: 'Client Organization Administrator',
    subtitle: 'Authorized Tenant Administrator',
    badge: 'Client Admin',
    badgeClass: 'chip-primary',
    description: 'Manage organization branches, custom RBAC roles, member provisioning, academic programs, course offerings, announcements, and financial invoices.',
    organization: 'Apex Academy of Excellence (Tenant Org)',
    permissionsCount: 38,
    dashboardFocus: 'Branch Operations, Member Provisioning, Curriculum, Finance & Announcements',
  },
  {
    id: 'TEACHER',
    title: 'Faculty / Evaluator',
    subtitle: 'Academic Instruction & Assessment Evaluator',
    badge: 'Teacher',
    badgeClass: 'chip-warning',
    description: 'Manage class sessions, take attendance, publish learning content, author question banks, evaluate student submissions, and manage gradebooks.',
    organization: 'Apex Academy of Excellence (Tenant Org)',
    permissionsCount: 24,
    dashboardFocus: 'Teaching Schedule, Classroom Attendance, Grading, Question Banks & Reviews',
  },
  {
    id: 'STUDENT',
    title: 'Enrolled Student / Learner',
    subtitle: 'Student connected to Client Organization',
    badge: 'Student / Learner',
    badgeClass: 'chip-success',
    description: 'Access personal today schedule, enrolled courses, learning content, take assessments, submit assignments, and join live classrooms.',
    organization: 'Apex Academy of Excellence (Tenant Org)',
    permissionsCount: 10,
    dashboardFocus: 'Personal Today Overview, Course Content, Assessment Attempts & Live Classes',
  },
]

export function AuthPage({ onAuthenticate }: { onAuthenticate: (persona: PersonaRole) => void }) {
  const [selected, setSelected] = useState<PersonaRole>('DEV_ADMIN')
  const [authMode, setAuthMode] = useState<'SELECT' | 'KEYCLOAK'>('SELECT')

  return (
    <main className="auth-container">
      <div className="auth-card">
        <header className="auth-header">
          <p className="eyebrow">Universal Education Platform</p>
          <h1>Platform Authentication</h1>
          <p className="auth-subtitle">Select an authorized identity persona or authenticate via Keycloak SSO to enter your workspace.</p>
        </header>

        <div className="auth-tabs">
          <button className={authMode === 'SELECT' ? 'tab-btn active' : 'tab-btn'} onClick={() => setAuthMode('SELECT')}>
            Role-Based Persona Access
          </button>
          <button className={authMode === 'KEYCLOAK' ? 'tab-btn active' : 'tab-btn'} onClick={() => setAuthMode('KEYCLOAK')}>
            Keycloak OIDC Single Sign-On
          </button>
        </div>

        {authMode === 'SELECT' && (
          <div className="persona-grid">
            {personas.map(p => (
              <article
                key={p.id}
                className={selected === p.id ? 'persona-card selected' : 'persona-card'}
                onClick={() => setSelected(p.id)}
              >
                <div className="persona-card-header">
                  <div>
                    <span className={`chip ${p.badgeClass}`}>{p.badge}</span>
                    <h3>{p.title}</h3>
                    <p className="muted small">{p.subtitle}</p>
                  </div>
                </div>
                <p className="persona-desc">{p.description}</p>
                <div className="persona-meta">
                  <span><strong>Organization:</strong> {p.organization}</span>
                  <span><strong>Focus:</strong> {p.dashboardFocus}</span>
                </div>
              </article>
            ))}

            <div className="auth-actions">
              <button className="primary-button large" onClick={() => onAuthenticate(selected)}>
                Enter Workspace as {personas.find(p => p.id === selected)?.badge}
              </button>
            </div>
          </div>
        )}

        {authMode === 'KEYCLOAK' && (
          <div className="keycloak-pane">
            <p>Connect to the Keycloak Single Sign-On server configured for your tenant realm.</p>
            <div className="chip-row">
              <span className="chip">Default Realm: <code>education</code></span>
              <span className="chip">Client ID: <code>platform-web</code></span>
              <span className="chip">Service Port: <code>8081</code></span>
            </div>
            <p className="muted small">If Keycloak server is offline, authentication fallback uses role-based persona access.</p>
            <div className="auth-actions">
              <button className="primary-button" onClick={() => onAuthenticate('DEV_ADMIN')}>
                Authenticate via Keycloak OIDC
              </button>
            </div>
          </div>
        )}
      </div>
    </main>
  )
}
