import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { activeUserForProfile, loadDemoState, subscribeDemoState } from './LocalDemoStore'
import { demoAccounts, type LocalAccount } from './localDemoAccounts'
import { SpecialistLocalDashboard } from './SpecialistLocalDashboard'


export function LocalExperience() {
  const [account, setAccount] = useState<LocalAccount | null>(() => readSessionAccount())

  useEffect(() => {
    if (!account) return

    const validateSession = () => {
      if (activeUserForProfile(loadDemoState(), account.profile)) return
      clearStoredDemoAccount()
      setSessionNotice('Your demo session ended because this account is inactive. Sign in with an active account or ask an administrator to reactivate it.')
      setAccount(null)
    }

    const unsubscribe = subscribeDemoState(validateSession)
    // Close the gap between the initial account read and effect subscription.
    validateSession()
    return unsubscribe
  }, [account])

  function handleLogin(nextAccount: LocalAccount) {
    try {
      sessionStorage.setItem('uep-local-account', JSON.stringify(nextAccount))
    } catch {
      setSessionNotice('Browser session storage is unavailable. Enable it and try signing in again.')
      return
    }
    setAccount(nextAccount)
  }
  function handleLogout() {
    clearStoredDemoAccount()
    setAccount(null)
  }
}

function clearStoredDemoAccount() {
  try {
    sessionStorage.removeItem('uep-local-account')
  } catch {
    // The in-memory account is still revoked; a reload will fail closed in readSessionAccount.
  }
}

if (!account) return <LocalLogin onLogin={handleLogin} />
}
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
        <p>Local presentation mode uses a seeded, shared dataset. Each demo account receives its own role, navigation and permissions; roles cannot be switched from inside another user's workspace.</p>
        <div className="local-login-note"><span>LOCAL MODE</span><strong>Shared presentation environment</strong></div>
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
        <div className="panel">
          <div className="panel-heading"><div><p className="eyebrow">Presentation accounts</p><h3>Choose a persona</h3></div></div>
          <div className="chip-row">
            {demoAccounts.map(account => (
              <button key={account.email} className="text-button" type="button" onClick={() => setEmail(account.email)}>
                {account.name}
              </button>
            ))}
          </div>
        </div>
      </section>
    </main>
  )
}
