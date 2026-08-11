export interface PlatformVersion {
  version: string
  releaseStatus: string
}

export async function fetchPlatformVersion(signal?: AbortSignal): Promise<PlatformVersion> {
  const response = await fetch('/api/v1/platform/version', {
    headers: { Accept: 'application/json' },
    signal,
  })
  if (!response.ok) throw new Error(`Platform version request failed: ${response.status}`)
  return response.json() as Promise<PlatformVersion>
}
