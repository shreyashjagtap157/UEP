import { useQuery } from '@tanstack/react-query'
import { fetchPlatformVersion } from '../platform/api'

export function App() {
  const version = useQuery({
    queryKey: ['platform-version'],
    queryFn: ({ signal }) => fetchPlatformVersion(signal),
    staleTime: 5 * 60 * 1000,
  })

  return (
    <main className="shell">
      <section className="hero" aria-labelledby="platform-title">
        <p className="eyebrow">Engineering foundation</p>
        <h1 id="platform-title">Universal Education Platform</h1>
        <p className="lede">
          The web client is the first interface to a multi-tenant, API-first education and training operations platform.
        </p>
        <dl className="status-grid">
          <div>
            <dt>Platform API</dt>
            <dd>{version.isPending ? 'Checking…' : version.isError ? 'Unavailable' : 'Connected'}</dd>
          </div>
          <div>
            <dt>Version</dt>
            <dd>{version.data?.version ?? '0.1.0.0-SNAPSHOT'}</dd>
          </div>
          <div>
            <dt>Commercial model</dt>
            <dd>Tenant entitlements</dd>
          </div>
        </dl>
      </section>
    </main>
  )
}
