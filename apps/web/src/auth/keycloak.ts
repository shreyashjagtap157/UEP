import Keycloak from 'keycloak-js'

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'education',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'platform-web',
})

let isStandaloneDevMode = false

async function isKeycloakServerAvailable(url: string, realm: string): Promise<boolean> {
  try {
    const controller = new AbortController()
    const timer = setTimeout(() => controller.abort(), 2000)
    await fetch(`${url}/realms/${realm}`, { method: 'HEAD', signal: controller.signal, mode: 'no-cors' })
    clearTimeout(timer)
    return true
  } catch {
    return false
  }
}

export async function initializeAuthentication(): Promise<void> {
  const url = import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081'
  const realm = import.meta.env.VITE_KEYCLOAK_REALM ?? 'education'
  const reachable = await isKeycloakServerAvailable(url, realm)

  if (!reachable) {
    console.warn(`[Keycloak Auth] Identity service at ${url} is unreachable. Initializing in local development fallback mode.`)
    isStandaloneDevMode = true
    return
  }

  try {
    const authenticated = await keycloak.init({
      onLoad: 'login-required',
      flow: 'standard',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    })
    if (!authenticated) await keycloak.login()
  } catch (error) {
    console.warn('[Keycloak Auth] Keycloak initialization failed, switching to local development fallback mode:', error)
    isStandaloneDevMode = true
  }
}

export async function accessToken(): Promise<string> {
  if (isStandaloneDevMode) {
    return 'mock-dev-access-token'
  }
  await keycloak.updateToken(30)
  if (!keycloak.token) throw new Error('Authenticated access token is unavailable')
  return keycloak.token
}

export function logout(): Promise<void> {
  if (isStandaloneDevMode) {
    window.location.reload()
    return Promise.resolve()
  }
  return keycloak.logout({ redirectUri: window.location.origin })
}

export async function openAccountManagement(): Promise<void> {
  if (isStandaloneDevMode) {
    alert('Keycloak identity server is currently offline. Start Keycloak via docker compose to manage identity accounts.')
    return
  }
  await keycloak.accountManagement()
}

export function enrollOtp(): Promise<void> {
  if (isStandaloneDevMode) {
    alert('Keycloak identity server is currently offline. Start Keycloak via docker compose to configure OTP.')
    return Promise.resolve()
  }
  return keycloak.login({ action: 'CONFIGURE_TOTP' })
}

export function enrollPasskey(): Promise<void> {
  if (isStandaloneDevMode) {
    alert('Keycloak identity server is currently offline. Start Keycloak via docker compose to register passkeys.')
    return Promise.resolve()
  }
  return keycloak.login({ action: 'webauthn-register-passwordless:skip_if_exists' })
}

export { keycloak }

