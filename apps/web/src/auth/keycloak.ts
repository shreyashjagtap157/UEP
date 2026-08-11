import Keycloak from 'keycloak-js'

const keycloak = new Keycloak({
  url: import.meta.env.VITE_KEYCLOAK_URL ?? 'http://localhost:8081',
  realm: import.meta.env.VITE_KEYCLOAK_REALM ?? 'education',
  clientId: import.meta.env.VITE_KEYCLOAK_CLIENT_ID ?? 'platform-web',
})

export async function initializeAuthentication(): Promise<void> {
  const authenticated = await keycloak.init({
    onLoad: 'login-required',
    flow: 'standard',
    pkceMethod: 'S256',
    checkLoginIframe: true,
  })
  if (!authenticated) await keycloak.login()
}

export async function accessToken(): Promise<string> {
  await keycloak.updateToken(30)
  if (!keycloak.token) throw new Error('Authenticated access token is unavailable')
  return keycloak.token
}

export function logout(): Promise<void> {
  return keycloak.logout({ redirectUri: window.location.origin })
}

export async function openAccountManagement(): Promise<void> {
  await keycloak.accountManagement()
}

export function enrollOtp(): Promise<void> {
  return keycloak.login({ action: 'CONFIGURE_TOTP' })
}

export function enrollPasskey(): Promise<void> {
  return keycloak.login({ action: 'webauthn-register-passwordless:skip_if_exists' })
}

export { keycloak }
