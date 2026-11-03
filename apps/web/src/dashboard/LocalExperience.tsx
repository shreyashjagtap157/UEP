import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { activeUserForProfile, loadDemoState, subscribeDemoState } from './LocalDemoStore'
import type { DemoRole } from './LocalDemoStore'
import { demoAccounts, type LocalAccount } from './localDemoAccounts'
import { SpecialistLocalDashboard } from './SpecialistLocalDashboard'

const ACCOUNT_GROUPS: Array<{ title: string; description: string; roles: DemoRole[] }> = [
  { title: 'Platform team', description: 'Builds and operates UEP, maintains products, and supports client institutions.', roles: ['developer', 'product-maintainer', 'support', 'admin', 'auditor'] },
  { title: 'Client & institution management', description: 'Leads each institution and manages its finance and examination operations.', roles: ['client', 'finance-admin', 'exam-controller'] },
  { title: 'Institution teaching & learning', description: 'The people who teach, support, evaluate and take part in learning.', roles: ['teacher', 'teaching-assistant', 'evaluator', 'mentor', 'student', 'guardian'] },
]

export function LocalExperience() {
  const [account, setAccount] = useState<LocalAccount | null>(() => readSessionAccount())
  useEffect(() => subscribeDemoState(() => {
    const raw = sessionStorage.getItem('uep-local-account')
    if (!raw) return
    try {
      const parsed = JSON.parse(raw) as Partial<LocalAccount>
      const current = demoAccounts.find(item => item.email === parsed.email && item.role === parsed.role)
      if (!current || !activeUserForProfile(loadDemoState(), current.profile)) {
        sessionStorage.removeItem('uep-local-account')
        setAccount(null)
      }
    } catch {
      sessionStorage.removeItem('uep-local-account')
      setAccount(null)
    }
  }), [])
  function handleLogin(nextAccount: LocalAccount) {
    sessionStorage.setItem('uep-local-account', JSON.stringify(nextAccount))
    setAccount(nextAccount)
  }
  function handleLogout() {
    sessionStorage.removeItem('uep-local-account')
    setAccount(null)
  }

  if (!account) return <LocalLogin onLogin={handleLogin} />
  return <SpecialistLocalDashboard profile={account.profile} onSignOut={handleLogout} />
}

function readSessionAccount(): LocalAccount | null {
  // Restore a stored demo session only after validating the account is still active.
  // Inactive or invalid accounts are rejected, cleared, and return to login.
  try {
    const raw = sessionStorage.getItem('uep-local-account')
    if (!raw) return null
    const parsed = JSON.parse(raw) as Partial<LocalAccount>
    const account = demoAccounts.find(item => item.email === parsed.email && item.role === parsed.role)
    if (!account || !activeUserForProfile(loadDemoState(), account.profile)) {
      sessionStorage.removeItem('uep-local-account')
      return null
    }
    return account
  } catch {
    // ignore storage/parse failures - treat as no session
    try { sessionStorage.removeItem('uep-local-account') } catch { /* ignore */ }
    return null
  }
}

function LocalLogin({ onLogin }: { onLogin: (account: LocalAccount) => void }) {
  const [email, setEmail] = useState('student@demo.uep')
  const [password, setPassword] = useState('demo')
  const [error, setError] = useState('')

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const account = demoAccounts.find(item => item.email === email.trim().toLowerCase())
    if (!account || password !== 'demo') {
      setError('Use a supplied demo account and the password demo.')
      return
    }
    if (!activeUserForProfile(loadDemoState(), account.profile)) {
      setError('This demo account is inactive. Ask a platform administrator to reactivate it.')
      return
    }
    setError('')
    onLogin(account)
  }

  return (
    <main className="local-login">
      <section className="local-login-intro">
        <p className="eyebrow">Universal Education Platform</p>
        <h1>One platform, one workspace for each relationship.</h1>
        <p>Explore UEP through three connected perspectives: the platform team, client institutions, and the teachers and learners in each institution. Every account opens a separate, role-scoped workspace.</p>
        <div className="local-login-note local-mode-badge"><span>OFFLINE DEMO</span><strong>Seeded presentation environment</strong></div>
      </section>
      <section className="local-login-panel" aria-labelledby="local-login-title">
        <p className="eyebrow">Demo sign in</p>
        <h2 id="local-login-title">Enter your workspace</h2>
        <p className="local-login-help">Use a supplied account below. The password for every account is <strong>demo</strong>.</p>
        <form className="local-login-form" onSubmit={submit}>
          <label>Email<input type="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="student@demo.uep" autoComplete="username" /></label>
          <label>Password<input type="password" value={password} onChange={event => setPassword(event.target.value)} autoComplete="current-password" /></label>
          {error && <p className="error-text" role="alert">{error}</p>}
          <button className="primary-button local-login-submit" type="submit">Sign in</button>
        </form>
        <div className="persona-groups" aria-label="Demo accounts by organizational group">
          {ACCOUNT_GROUPS.map(group => {
            const groupId = group.title.replace(/[^a-z0-9]+/gi, '-').toLowerCase()
            const groupAccounts = demoAccounts.filter(account => group.roles.includes(account.role))
            return (
              <section className="persona-group" key={group.title} aria-labelledby={groupId}>
                <div className="persona-group-heading">
                  <div>
                    <h3 id={groupId}>{group.title}</h3>
                    <p>{group.description}</p>
                  </div>
                  <span>{groupAccounts.length} roles</span>
                </div>
                <div className="persona-grid">
                  {groupAccounts.map(account => (
                    <button
                      key={account.email}
                      className={email === account.email ? 'persona-option selected' : 'persona-option'}
                      type="button"
                      aria-pressed={email === account.email}
                      onClick={() => setEmail(account.email)}
                    >
                      <strong>{account.profile.label}</strong>
                      <span>{account.profile.description}</span>
                      <small>{account.email}</small>
                    </button>
                  ))}
                </div>
              </section>
            )
          })}
        </div>
        <p className="local-login-footnote">Password for every seeded demo account: <strong>demo</strong>. Use <strong>Switch user</strong> inside a workspace to sign out and return here.</p>
      </section>
    </main>
  )
}
