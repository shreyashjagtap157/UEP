import Keycloak from 'keycloak-js'

let activeKeycloakUrl = import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081'
let keycloak = new Keycloak({
  url: activeKeycloakUrl,
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'education',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'platform-web',
})

let isStandaloneDevMode = false

async function findReachableKeycloakUrl(realm: string): Promise<string | null> {
  const candidates = Array.from(new Set([
    import.meta.env.VITE_KEYCLOAK_URL,
    'http://localhost:8081',
    'http://localhost:8080',
    'http://localhost:8082',
  ])).filter(Boolean) as string[]

  for (const url of candidates) {
    try {
      const controller = new AbortController()
      const timer = setTimeout(() => controller.abort(), 1200)
      await fetch(`${url}/realms/${realm}`, { method: 'HEAD', signal: controller.signal, mode: 'no-cors' })
      clearTimeout(timer)
      return url
    } catch {
      // Continue checking next candidate
    }
  }
  return null
}

export async function initializeAuthentication(): Promise<void> {
  const realm = import.meta.env.VITE_KEYCLOAK_REALM ?? 'education'
  const reachableUrl = await findReachableKeycloakUrl(realm)

  if (!reachableUrl) {
    console.warn(`[Keycloak Auth] No active Keycloak identity service discovered on 8081, 8080, or 8082. Initializing in local development fallback mode.`)
    isStandaloneDevMode = true
    return
  }

  activeKeycloakUrl = reachableUrl
  keycloak = new Keycloak({
    url: activeKeycloakUrl,
    realm,
    clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'platform-web',
  })

  try {
    const authenticated = await keycloak.init({
      onLoad: 'login-required',
      flow: 'standard',
      pkceMethod: 'S256',
      checkLoginIframe: false,
    })
    if (!authenticated) await keycloak.login()
  } catch (error) {
    console.warn(`[Keycloak Auth] Keycloak initialization at ${activeKeycloakUrl} failed, switching to local development fallback mode:`, error)
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

