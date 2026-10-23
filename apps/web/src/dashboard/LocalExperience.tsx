import { useState } from 'react'
import type { FormEvent } from 'react'
import { DEMO_PROFILES } from './LocalDemoStore'
import type { DemoRole } from './LocalDemoStore'
import { LocalDashboard } from './LocalDashboard'

const SESSION_KEY = 'uep.local-demo-role.v1'

export function LocalExperience() {
  const [role, setRole] = useState<DemoRole | null>(() => readSessionRole())

  function handleLogin(nextRole: DemoRole) {
    try { window.sessionStorage.setItem(SESSION_KEY, nextRole) } catch { /* session can still run in memory */ }
    setRole(nextRole)
  }
  function handleLogout() {
    try { window.sessionStorage.removeItem(SESSION_KEY) } catch { /* no persistent session to clear */ }
    setRole(null)
  }

  return role ? <LocalDashboard initialRole={role} onSignOut={handleLogout} /> : <LocalDemoLogin onLogin={handleLogin} />
}

function readSessionRole(): DemoRole | null {
  try {
    const value = window.sessionStorage.getItem(SESSION_KEY)
    return DEMO_PROFILES.find(profile => profile.role === value)?.role ?? null
  } catch {
    return null
  }
}

function LocalDemoLogin({ onLogin }: { onLogin: (role: DemoRole) => void }) {
  const [role, setRole] = useState<DemoRole>('student')
  const [error, setError] = useState('')

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!DEMO_PROFILES.some(profile => profile.role === role)) {
      setError('Choose one of the available demo profiles.')
      return
    }
    setError('')
    onLogin(role)
  }

  return <main className="local-login">
    <section className="local-login-intro">
      <p className="eyebrow">Universal Education Platform</p>
      <h1>One place for every learning relationship.</h1>
      <p>Enter a local demonstration workspace. Each persona has its own navigation and data scope. Saved workflow changes remain in this browser so you can demonstrate them across separate sign-ins.</p>
      <div className="local-login-note"><span>LOCAL DEMO ONLY</span><strong>This does not authenticate against production.</strong></div>
    </section>
    <section className="local-login-panel" aria-labelledby="local-login-title">
      <p className="eyebrow">Client demonstration</p>
      <h2 id="local-login-title">Choose a demo account</h2>
      <p className="local-login-help">Select one persona to open its dedicated workspace. To change personas, sign out first.</p>
      <form className="local-login-form" onSubmit={submit}>
        <label>Demo account
          <select value={role} onChange={event => setRole(event.target.value as DemoRole)}>
            {DEMO_PROFILES.map(profile => <option key={profile.role} value={profile.role}>{profile.label} — {profile.name}</option>)}
          </select>
        </label>
        <p className="local-demo-account-email">{DEMO_PROFILES.find(profile => profile.role === role)?.email}</p>
        <p className="local-demo-account-description">{DEMO_PROFILES.find(profile => profile.role === role)?.description}</p>
        {error && <p className="error-text" role="alert">{error}</p>}
        <button className="primary-button local-login-submit" type="submit">Open {DEMO_PROFILES.find(profile => profile.role === role)?.label ?? 'demo'} workspace</button>
      </form>
      <p className="local-login-footnote">All sample records are synthetic. Metrics are calculated from local stored records; production API, identity, payments and service-health data require the connected platform backend.</p>
    </section>
  </main>
}
