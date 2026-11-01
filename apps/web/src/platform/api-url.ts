export function resolveBackendBaseUrl(
  configuredUrl: string | undefined,
  localMode: boolean,
  localBackendUrl: string | undefined,
): string {
  const configured = configuredUrl?.trim()
  if (configured) return configured.replace(/\/+$/, '')

  if (!localMode) return ''

  const local = localBackendUrl?.trim()
  return (local || 'http://localhost:8080').replace(/\/+$/, '')
}

export function joinBackendUrl(baseUrl: string, path: string): string {
  if (!path.startsWith("/")) {
    throw new Error('Backend API paths must start with "/"')
  }
  return baseUrl + path
}
