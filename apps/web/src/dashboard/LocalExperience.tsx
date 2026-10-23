import { useState } from 'react'
import type { FormEvent } from 'react'
import { LocalDashboard } from './LocalDashboard'

type LocalRole = 'student' | 'client' | 'developer'

export function LocalExperience() {
  const [role, setRole] = useState<LocalRole | null>(() => readSessionRole())

  function handleLogin(nextRole: LocalRole) {
    sessionStorage.setItem('uep-local-role', nextRole)
    setRole(nextRole)
  }

  function handleLogout() {
    sessionStorage.removeItem('uep-local-role')
    setRole(null)
  }

  return role ? <LocalDashboard initialRole={role} onSignOut={handleLogout} /> : <LocalLogin onLogin={handleLogin} />
}

function readSessionRole(): LocalRole | null {
  const role = sessionStorage.getItem('uep-local-role')
  return role === 'student' || role === 'client' || role === 'developer' ? role : null
}

function LocalLogin({ onLogin }: { onLogin: (role: LocalRole) => void }) {
  const [role, setRole] = useState<LocalRole>('student')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState('')

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!email.trim() || !password.trim()) {
      setError('Enter an email and password to continue.')
      return
    }
    setError('')
    onLogin(role)
  }

  return (
    <main className="local-login">
      <section className="local-login-intro">
        <p className="eyebrow">Universal Education Platform</p>
        <h1>One place for every learning relationship.</h1>
        <p>Sign in to continue to your workspace. This local presentation login uses demo data and does not contact a remote identity provider.</p>
        <div className="local-login-note"><span>LOCAL MODE</span><strong>Offline presentation environment</strong></div>
      </section>
      <section className="local-login-panel" aria-labelledby="local-login-title">
        <p className="eyebrow">Welcome back</p>
        <h2 id="local-login-title">Sign in to the platform</h2>
        <p className="local-login-help">Choose a demo role to preview its dashboard after authentication.</p>
        <form className="local-login-form" onSubmit={submit}>
          <label>Email<input type="email" value={email} onChange={event => setEmail(event.target.value)} placeholder="you@example.com" autoComplete="username" /></label>
          <label>Password<input type="password" value={password} onChange={event => setPassword(event.target.value)} placeholder="Enter any demo password" autoComplete="current-password" /></label>
          <label>Demo role<select value={role} onChange={event => setRole(event.target.value as LocalRole)}><option value="student">Student</option><option value="client">Client</option><option value="developer">Developer</option></select></label>
          {error && <p className="error-text" role="alert">{error}</p>}
          <button className="primary-button local-login-submit" type="submit">Continue to dashboard</button>
        </form>
        <p className="local-login-footnote">Demo credentials are local-only. Any non-empty email and password are accepted.</p>
      </section>
    </main>
  )
}
