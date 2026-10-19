import Keycloak from 'keycloak-js'

// The only supported sign-in path is the configured Keycloak OIDC realm.
// All three variables are required so the client never guesses identity
// endpoints (previously it probed localhost ports, including the backend's).
let instance: Keycloak | null = null

function ensureKeycloak(): Keycloak {
  if (instance) return instance
  const url = import.meta.env.VITE_KEYCLOAK_URL
  const realm = import.meta.env.VITE_KEYCLOAK_REALM
  const clientId = import.meta.env.VITE_KEYCLOAK_CLIENT_ID
  if (!url || !realm || !clientId) {
    throw new Error(
      'Keycloak is not configured. Set VITE_KEYCLOAK_URL, VITE_KEYCLOAK_REALM and VITE_KEYCLOAK_CLIENT_ID at build time.',
    )
  }
  instance = new Keycloak({ url, realm, clientId })
  return instance
}

let initialized = false

export async function initializeAuthentication(): Promise<void> {
  const keycloak = ensureKeycloak()
  if (!initialized) {
    const authenticated = await keycloak.init({
      onLoad: 'login-required',
      flow: 'standard',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    })
    initialized = true
    if (!authenticated) await keycloak.login()
  }
}

export function isAuthenticated(): boolean {
  return instance?.authenticated === true
}

/** Subscribes to OIDC session transitions; returns an unsubscribe function. */
export function onOidcStatus(callback: (authenticated: boolean) => void): () => void {
  const keycloak = ensureKeycloak()
  const notify = () => callback(keycloak.authenticated === true)
  const previousSuccess = keycloak.onAuthSuccess
  const previousLogout = keycloak.onAuthLogout
  const previousError = keycloak.onAuthError
  const previousRefreshError = keycloak.onAuthRefreshError
  keycloak.onAuthSuccess = notify
  keycloak.onAuthLogout = notify
  keycloak.onAuthError = notify
  keycloak.onAuthRefreshError = notify
  return () => {
    keycloak.onAuthSuccess = previousSuccess
    keycloak.onAuthLogout = previousLogout
    keycloak.onAuthError = previousError
    keycloak.onAuthRefreshError = previousRefreshError
  }
}

export function signIn(): Promise<void> {
  return ensureKeycloak().login()
}

export async function logout(): Promise<void> {
  await ensureKeycloak().logout({ redirectUri: window.location.origin })
}

export async function openAccountManagement(): Promise<void> {
  await ensureKeycloak().accountManagement()
}

export function enrollOtp(): Promise<void> {
  return ensureKeycloak().login({ action: 'CONFIGURE_TOTP' })
}

export function enrollPasskey(): Promise<void> {
  return ensureKeycloak().login({ action: 'webauthn-register-passwordless:skip_if_exists' })
}

export async function accessToken(): Promise<string> {
  const keycloak = ensureKeycloak()
  await keycloak.updateToken(30)
  if (!keycloak.token) throw new Error('Authenticated access token is unavailable')
  return keycloak.token
}
