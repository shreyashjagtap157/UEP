import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import type { CurrentIdentity, PermissionKey } from '../platform/api'
import { configureIdentityProvider, configureNotificationProvider, configureStorageBinding, createApiCredential, createWebhook, fetchApiCredentials, fetchIdentityProviders, fetchNotificationProviders, fetchStorageBinding, fetchWebhooks, revokeApiCredential, revokeWebhook, testNotificationProvider, testWebhook } from '../platform/api'

export function IntegrationWorkspace({ me }: { me: CurrentIdentity }) {
  const client = useQueryClient()
  const webhooks = useQuery({ queryKey: ['integration-webhooks'], queryFn: fetchWebhooks, enabled: me.permissions.includes('WEBHOOKS_VIEW') })
  const idps = useQuery({ queryKey: ['integration-idps'], queryFn: fetchIdentityProviders, enabled: me.permissions.includes('FEDERATION_VIEW') })
  const notificationProviders = useQuery({ queryKey: ['integration-notification-providers'], queryFn: fetchNotificationProviders, enabled: me.permissions.includes('INTEROPERABILITY_VIEW') })
  const storage = useQuery({ queryKey: ['integration-storage'], queryFn: fetchStorageBinding, enabled: me.permissions.includes('EXTERNAL_STORAGE_MANAGE') })
  const webhookMutation = useMutation({ mutationFn: createWebhook, onSuccess: () => void client.invalidateQueries({ queryKey: ['integration-webhooks'] }) })
  const webhookRevoke = useMutation({ mutationFn: ({ id, version }: { id: string; version: number }) => revokeWebhook(id, version), onSuccess: () => void client.invalidateQueries({ queryKey: ['integration-webhooks'] }) })
  const webhookTest = useMutation({ mutationFn: testWebhook })
  const storageMutation = useMutation({ mutationFn: configureStorageBinding, onSuccess: () => void client.invalidateQueries({ queryKey: ['integration-storage'] }) })
  const idpMutation = useMutation({ mutationFn: configureIdentityProvider, onSuccess: () => void client.invalidateQueries({ queryKey: ['integration-idps'] }) })
  const notificationMutation = useMutation({ mutationFn: configureNotificationProvider, onSuccess: () => void client.invalidateQueries({ queryKey: ['integration-notification-providers'] }) })
  const notificationTest = useMutation({ mutationFn: ({ key }: { key: string }) => testNotificationProvider(key, { title: 'UEP integration test', body: 'External notification provider connectivity test.' }) })
  const [webhookResult, setWebhookResult] = useState('')
  const [credentialResult, setCredentialResult] = useState('')
  const [scopeError, setScopeError] = useState('')
  const credentials = useQuery({ queryKey: ['integration-api-credentials', me.membershipId], queryFn: () => fetchApiCredentials(me.membershipId), enabled: me.permissions.includes('API_VIEW') })
  const credentialMutation = useMutation({ mutationFn: createApiCredential, onSuccess: r => { setCredentialResult(r.secret ? `API key (shown once): ${r.secret}` : 'Credential created.'); void client.invalidateQueries({ queryKey: ['integration-api-credentials', me.membershipId] }) } })
  const credentialRevoke = useMutation({ mutationFn: ({ id, version }: { id: string; version: number }) => revokeApiCredential(id, version), onSuccess: () => void client.invalidateQueries({ queryKey: ['integration-api-credentials', me.membershipId] }) })

  return <div className="content-stack">
    {(me.permissions.includes('API_VIEW') || me.permissions.includes('API_MANAGE')) && <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">Public API</p><h3>Scoped service credentials</h3></div></div>
      {me.permissions.includes('API_MANAGE') && !me.permissions.includes('API_VIEW') && <p className="muted small">API Manage can create credentials. API View is also required to inspect and revoke existing credentials.</p>}
      {me.permissions.includes('API_MANAGE') && (
        <form className="form-grid" onSubmit={event => {
          event.preventDefault()
          const f = new FormData(event.currentTarget)
          const raw = String(f.get('scopes') || '').split(',').map(x => x.trim()).filter(Boolean) as PermissionKey[]
          if (raw.length === 0) { setScopeError('Select at least one explicit permission scope.'); return }
          setScopeError('')
          credentialMutation.mutate({ membershipId: me.membershipId, name: String(f.get('name') || ''), scopes: raw })
        }}>
          <label>Name<input name="name" required maxLength={120} placeholder="Reporting integration" /></label>
          <label>Permission scopes<input name="scopes" required defaultValue="ANALYTICS_VIEW,INTEROPERABILITY_VIEW" /></label>
          <p className="muted">Choose at least one explicit permission. Empty-scope credentials are disabled rather than inheriting all membership permissions.</p>
          <div className="form-actions"><button className="primary-button" disabled={credentialMutation.isPending}>Create credential</button></div>
          {scopeError && <p className="error-text" role="alert">{scopeError}</p>}
          {credentialResult && <p className="muted"><code>{credentialResult}</code></p>}
          {credentialMutation.isError && <p className="error-text" role="alert">{credentialMutation.error.message}</p>}
        </form>
      )}
      {me.permissions.includes('API_VIEW') && (
        <div className="table-wrap"><table>
          <thead><tr><th>Name</th><th>Scopes</th><th>Expires</th><th>Status</th>{me.permissions.includes('API_MANAGE') && <th>Actions</th>}</tr></thead>
          <tbody>{credentials.isPending ? <tr><td colSpan={me.permissions.includes('API_MANAGE') ? 5 : 4}>Loading credentials…</td></tr> : credentials.isError ? <tr><td colSpan={me.permissions.includes('API_MANAGE') ? 5 : 4}><span className="error-text">{credentials.error.message}</span></td></tr> : credentials.data?.map(c => (
            <tr key={c.id}>
              <td>{c.name}</td>
              <td>{c.scopes.join(', ') || 'Legacy unscoped credential (disabled)'}</td>
              <td>{c.expiresAt ? new Date(c.expiresAt).toLocaleString() : 'Never'}</td>
              <td>{c.revokedAt ? <span className="danger-chip">Revoked</span> : <span>Active</span>}</td>
              {me.permissions.includes('API_MANAGE') && <td>{c.revokedAt
                ? '—'
                : <button className="text-button" disabled={credentialRevoke.isPending} onClick={() => {
                    if (window.confirm('Revoke this API credential? Clients using it will stop authenticating.')) {
                      credentialRevoke.mutate({ id: c.id, version: c.version })
                    }
                  }}>Revoke credential</button>}</td>}
            </tr>
          ))}</tbody>
        </table></div>
      )}
      {credentialRevoke.isError && <p className="error-text" role="alert">{credentialRevoke.error.message}</p>}
    </section>}
    {(me.permissions.includes('WEBHOOKS_VIEW') || me.permissions.includes('WEBHOOKS_MANAGE')) && <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">Webhooks</p><h3>Durable event delivery</h3></div></div>
      {me.permissions.includes('WEBHOOKS_MANAGE') && !me.permissions.includes('WEBHOOKS_VIEW') && <p className="muted small">Webhooks Manage can create and queue test events. Webhooks View is also required to inspect and revoke existing subscriptions.</p>}
      {me.permissions.includes('WEBHOOKS_MANAGE') && (
        <>
          <form className="form-grid" onSubmit={event => {
            event.preventDefault()
            const form = event.currentTarget
            const f = new FormData(form)
            webhookMutation.mutate({ callbackUrl: String(f.get('url') || ''), eventFilter: String(f.get('event') || '*') }, {
              onSuccess: r => { setWebhookResult(r.secret ? 'Signing secret (shown once): ' + r.secret : 'Webhook created. The signing secret is only returned once.'); form.reset() },
            })
          }}>
            <label>Callback URL<input name="url" required type="url" placeholder="https://example.com/uep/webhook" /></label>
            <label>Event filter<input name="event" defaultValue="*" maxLength={64} /></label>
            <div className="form-actions"><button className="primary-button" disabled={webhookMutation.isPending}>Create webhook</button></div>
            {webhookResult && <p className="muted"><code>{webhookResult}</code></p>}
            {webhookMutation.isError && <p className="error-text" role="alert">{webhookMutation.error.message}</p>}
          </form>
          <form className="inline-form" onSubmit={event => {
            event.preventDefault()
            const f = new FormData(event.currentTarget)
            webhookTest.mutate({ eventType: String(f.get('eventType') || ''), message: String(f.get('message') || '') })
          }}>
            <label>Test event type<input name="eventType" required maxLength={64} defaultValue="UEP_INTEGRATION_TEST" /></label>
            <label>Test message<input name="message" maxLength={500} defaultValue="Administrator-requested webhook test" /></label>
            <button className="secondary-button" disabled={webhookTest.isPending}>Queue test event</button>
          </form>
          <p className="muted small">A test event is queued for every enabled webhook whose event filter matches. Queueing does not confirm remote delivery.</p>
          {webhookTest.isSuccess && <p className="muted" role="status">Test event queued for matching webhook subscriptions.</p>}
          {webhookTest.isError && <p className="error-text" role="alert">{webhookTest.error.message}</p>}
        </>
      )}
      {me.permissions.includes('WEBHOOKS_VIEW') && (
        <div className="table-wrap"><table>
          <thead><tr><th>Endpoint</th><th>Filter</th><th>Version</th>{me.permissions.includes('WEBHOOKS_MANAGE') && <th>Actions</th>}</tr></thead>
          <tbody>{webhooks.isPending ? <tr><td colSpan={me.permissions.includes('WEBHOOKS_MANAGE') ? 4 : 3}>Loading webhooks…</td></tr> : webhooks.isError ? <tr><td colSpan={me.permissions.includes('WEBHOOKS_MANAGE') ? 4 : 3}><span className="error-text">{webhooks.error.message}</span></td></tr> : webhooks.data?.map(w => (
            <tr key={w.id}>
              <td><code>{w.callbackUrl}</code></td><td>{w.eventFilter}</td><td>{w.version}</td>
              {me.permissions.includes('WEBHOOKS_MANAGE') && <td>
                <button className="text-button" disabled={webhookRevoke.isPending} onClick={() => {
                  if (window.confirm('Disable this webhook subscription? Pending retries for it will no longer be delivered.')) {
                    webhookRevoke.mutate({ id: w.id, version: w.version })
                  }
                }}>Revoke webhook</button>
              </td>}
            </tr>
          ))}</tbody>
        </table></div>
      )}
      {webhookRevoke.isError && <p className="error-text" role="alert">{webhookRevoke.error.message}</p>}
    </section>}
    {me.permissions.includes('EXTERNAL_STORAGE_MANAGE') && <section className="panel"><div className="panel-heading"><div><p className="eyebrow">External storage</p><h3>Tenant storage binding</h3></div></div><form className="inline-form" onSubmit={event=>{event.preventDefault();const f=new FormData(event.currentTarget);storageMutation.mutate({providerType:String(f.get('providerType')) as 'LOCAL'|'S3_COMPATIBLE'|'GOOGLE_DRIVE'|'GOOGLE_SHARED_DRIVE',objectPrefix:String(f.get('prefix')||''),expectedVersion:storage.data?.version??0})}}><select key={storage.data?.version ?? 'new'} name="providerType" defaultValue={storage.data?.providerType ?? 'S3_COMPATIBLE'}><option>S3_COMPATIBLE</option><option>GOOGLE_DRIVE</option><option>GOOGLE_SHARED_DRIVE</option><option>LOCAL</option></select><input name="prefix" defaultValue={storage.data?.objectPrefix ?? ''} maxLength={200} placeholder="tenant/object-prefix"/><button className="primary-button" disabled={storageMutation.isPending}>Save storage binding</button></form><p className="muted">Configured: {storage.data?.providerType ?? 'not configured'}. Upload destinations are resolved by the server from this tenant binding; the browser cannot select a provider.</p>{storageMutation.isError && <p className="error-text" role="alert">{storageMutation.error.message}</p>}</section>}
    {(me.permissions.includes('FEDERATION_VIEW') || me.permissions.includes('FEDERATION_MANAGE')) && <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">SSO / enterprise federation</p><h3>OpenID Connect relying party</h3></div></div>
      {me.permissions.includes('FEDERATION_MANAGE') && (
        <form className="form-grid" onSubmit={event => {
          event.preventDefault()
          const form = event.currentTarget
          const values = new FormData(form)
          const providerKey = String(values.get('key') || '').trim()
          const currentProvider = idps.data?.find(provider => provider.providerKey === providerKey)
          idpMutation.mutate({
            providerKey,
            issuer: String(values.get('issuer') || ''),
            authorizationEndpoint: String(values.get('auth') || ''),
            clientId: String(values.get('client') || ''),
            clientSecret: String(values.get('secret') || ''),
            redirectUri: String(values.get('redirect') || ''),
            scopes: String(values.get('scopes') || 'openid profile email'),
            expectedVersion: currentProvider?.version ?? 0,
          }, { onSuccess: () => form.reset() })
        }}>
          <label>Provider key<input name="key" required maxLength={64}/></label>
          <label>Issuer<input name="issuer" required type="url"/></label>
          <label>Authorization endpoint<input name="auth" required type="url"/></label>
          <label>Client ID<input name="client" required/></label>
          <label>Client secret<input name="secret" type="password"/></label>
          <label>Redirect URI<input name="redirect" required type="url"/></label>
          <label>Scopes<input name="scopes" defaultValue="openid profile email"/></label>
          <div className="form-actions"><button className="primary-button" disabled={idpMutation.isPending}>Save provider</button></div>
          {idpMutation.isError && <p className="error-text" role="alert">{idpMutation.error.message}</p>}
        </form>
      )}
      {me.permissions.includes('FEDERATION_MANAGE') && !me.permissions.includes('FEDERATION_VIEW') && <p className="muted small">You can create providers. FEDERATION_VIEW is also required to inspect an existing provider and submit its current version when updating it.</p>}
      {me.permissions.includes('FEDERATION_VIEW') && (
        <div className="table-wrap"><table>
          <thead><tr><th>Provider</th><th>Issuer</th><th>Client</th><th>Version</th></tr></thead>
          <tbody>{idps.isPending ? <tr><td colSpan={4}>Loading identity providers…</td></tr> : idps.isError ? <tr><td colSpan={4}><span className="error-text">{idps.error.message}</span></td></tr> : idps.data?.map(provider => (
            <tr key={provider.id}><td>{provider.providerKey}</td><td><code>{provider.issuer}</code></td><td>{provider.clientId}</td><td>{provider.version}</td></tr>
          ))}</tbody>
        </table></div>
      )}
    </section>}

    {(me.permissions.includes('INTEROPERABILITY_VIEW') || me.permissions.includes('EXTERNAL_NOTIFICATIONS_MANAGE')) && <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">External notifications</p><h3>HTTP / Slack / Teams</h3></div></div>
      {me.permissions.includes('EXTERNAL_NOTIFICATIONS_MANAGE') && (
        <form className="form-grid" onSubmit={event => {
          event.preventDefault()
          const form = event.currentTarget
          const values = new FormData(form)
          const providerKey = String(values.get('key') || '').trim()
          const currentProvider = notificationProviders.data?.find(provider => provider.providerKey === providerKey)
          notificationMutation.mutate({
            providerKey,
            providerType: String(values.get('type') || ''),
            endpoint: String(values.get('endpoint') || ''),
            credential: String(values.get('credential') || ''),
            expectedVersion: currentProvider?.version ?? 0,
          }, { onSuccess: () => form.reset() })
        }}>
          <label>Provider key<input name="key" required maxLength={64}/></label>
          <label>Provider type<select name="type"><option>HTTP_JSON</option><option>SLACK_WEBHOOK</option><option>TEAMS_WEBHOOK</option></select></label>
          <label>Endpoint<input name="endpoint" required type="url"/></label>
          <label>Bearer credential<input name="credential" type="password"/></label>
          <div className="form-actions"><button className="primary-button" disabled={notificationMutation.isPending}>Save provider</button></div>
          {notificationMutation.isError && <p className="error-text" role="alert">{notificationMutation.error.message}</p>}
        </form>
      )}
      {me.permissions.includes('EXTERNAL_NOTIFICATIONS_MANAGE') && !me.permissions.includes('INTEROPERABILITY_VIEW') && <p className="muted small">You can create providers. INTEROPERABILITY_VIEW is also required to inspect an existing provider and submit its current version when updating it.</p>}
      {me.permissions.includes('INTEROPERABILITY_VIEW') && (
        <div className="table-wrap"><table>
          <thead><tr><th>Provider</th><th>Type</th><th>Endpoint</th><th>Version</th>{me.permissions.includes('EXTERNAL_NOTIFICATIONS_MANAGE') && <th>Actions</th>}</tr></thead>
          <tbody>{notificationProviders.isPending ? <tr><td colSpan={me.permissions.includes('EXTERNAL_NOTIFICATIONS_MANAGE') ? 5 : 4}>Loading notification providers…</td></tr> : notificationProviders.isError ? <tr><td colSpan={me.permissions.includes('EXTERNAL_NOTIFICATIONS_MANAGE') ? 5 : 4}><span className="error-text">{notificationProviders.error.message}</span></td></tr> : notificationProviders.data?.map(provider => (
            <tr key={provider.id}>
              <td>{provider.providerKey}</td><td>{provider.providerType}</td><td><code>{provider.endpoint}</code></td><td>{provider.version}</td>
              {me.permissions.includes('EXTERNAL_NOTIFICATIONS_MANAGE') && <td><button className="text-button" disabled={notificationTest.isPending} onClick={() => notificationTest.mutate({ key: provider.providerKey })}>Send test</button></td>}
            </tr>
          ))}</tbody>
        </table></div>
      )}
      {notificationTest.isSuccess && <p className="muted" role="status">The provider accepted the test delivery request.</p>}
      {notificationTest.isError && <p className="error-text" role="alert">{notificationTest.error.message}</p>}
    </section>}
  </div>
}
