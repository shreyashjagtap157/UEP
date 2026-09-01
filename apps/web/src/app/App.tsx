import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { enrollOtp, enrollPasskey, logout, openAccountManagement } from '../auth/keycloak'
import { AcademicOverview } from '../academic/AcademicOverview'
import { AcademicWorkspace } from '../academic/AcademicWorkspace'
import { EnrollmentWorkspace } from '../academic/EnrollmentWorkspace'
import { AnnouncementWorkspace } from '../communication/AnnouncementWorkspace'
import { TodayOverview } from '../dashboard/TodayOverview'
import { NotificationWorkspace } from '../notifications/NotificationWorkspace'
import { ScheduleWorkspace } from '../scheduling/ScheduleWorkspace'
import { ContentWorkspace } from '../content/ContentWorkspace'
import { AssessmentWorkspace } from '../assessment/AssessmentWorkspace'
import { AssignmentWorkspace } from '../assignment/AssignmentWorkspace'
import {
  createBranch,
  createRole,
  fetchBranches,
  fetchCurrentIdentity,
  fetchMembers,
  fetchMySessions,
  fetchOrganizationSettings,
  fetchPlatformVersion,
  fetchRoles,
  provisionMembership,
  revokeOwnSession,
  updateOrganizationSettings,
} from '../platform/api'
import type { BranchView, CurrentIdentity, OrganizationSettings, PermissionKey, RoleView } from '../platform/api'

type Section = 'overview' | 'assessment' | 'assignments' | 'content' | 'schedule' | 'announcements' | 'notifications' | 'academics' | 'enrollment' | 'people' | 'roles' | 'organization' | 'security'

const permissionOptions: PermissionKey[] = [
  'ORGANIZATION_VIEW', 'ORGANIZATION_MANAGE', 'BRANCHES_VIEW', 'BRANCHES_MANAGE',
  'USERS_VIEW', 'USERS_MANAGE', 'ROLES_VIEW', 'ROLES_MANAGE', 'ROLES_ASSIGN',
  'SESSIONS_VIEW', 'SESSIONS_MANAGE',
  'ACADEMICS_VIEW', 'ACADEMICS_MANAGE', 'CURRICULUM_VIEW', 'CURRICULUM_MANAGE',
  'ENROLLMENTS_VIEW', 'ENROLLMENTS_MANAGE', 'TEACHING_ASSIGNMENTS_VIEW', 'TEACHING_ASSIGNMENTS_MANAGE',
  'SCHEDULE_VIEW', 'SCHEDULE_MANAGE', 'SCHEDULE_CONFLICT_OVERRIDE',
  'ANNOUNCEMENTS_VIEW', 'ANNOUNCEMENTS_MANAGE', 'NOTIFICATION_OPERATIONS_VIEW', 'CONTENT_VIEW', 'CONTENT_MANAGE',
  'ASSESSMENTS_VIEW', 'ASSESSMENTS_MANAGE', 'ASSESSMENTS_TAKE', 'GRADING_VIEW', 'GRADING_MANAGE', 'REVIEW_VIEW', 'REVIEW_SUBMIT', 'REVIEW_MANAGE',
  'ASSIGNMENTS_VIEW', 'ASSIGNMENTS_MANAGE', 'ASSIGNMENTS_TAKE', 'GRADEBOOK_VIEW', 'GRADEBOOK_MANAGE', 'AUDIT_VIEW',
]

export function App() {
  const [section, setSection] = useState<Section>('overview')
  const identity = useQuery({ queryKey: ['me'], queryFn: fetchCurrentIdentity })
  const version = useQuery({ queryKey: ['platform-version'], queryFn: ({ signal }: { signal: AbortSignal }) => fetchPlatformVersion(signal), staleTime: 300_000 })

  if (identity.isPending) return <CenteredStatus title="Loading your workspace" detail="Resolving tenant identity and permissions…" />
  if (identity.isError) return <CenteredStatus title="Access unavailable" detail={identity.error.message} />

  const me = identity.data
  const nav: Array<{ id: Section; label: string; visible: boolean }> = [
    { id: 'overview', label: 'Today', visible: true },
    { id: 'schedule', label: 'Schedule', visible: hasScoped(me, 'SCHEDULE_VIEW') },
    { id: 'announcements', label: 'Announcements', visible: hasScoped(me, 'ANNOUNCEMENTS_VIEW') },
    { id: 'notifications', label: 'Notifications', visible: true },
    { id: 'assessment', label: 'Assessments', visible: has(me, 'ASSESSMENTS_VIEW') || has(me, 'ASSESSMENTS_TAKE') },
    { id: 'assignments', label: 'Assignments', visible: has(me, 'ASSIGNMENTS_VIEW') || has(me, 'ASSIGNMENTS_TAKE') || has(me, 'GRADEBOOK_VIEW') },
    { id: 'content', label: 'Learning Content', visible: has(me, 'CONTENT_VIEW') },
    { id: 'academics', label: 'Academics', visible: has(me, 'ACADEMICS_VIEW') || has(me, 'CURRICULUM_VIEW') },
    { id: 'enrollment', label: 'Enrollment', visible: has(me, 'ENROLLMENTS_VIEW') || has(me, 'TEACHING_ASSIGNMENTS_VIEW') },
    { id: 'people', label: 'People', visible: has(me, 'USERS_VIEW') },
    { id: 'roles', label: 'Roles', visible: has(me, 'ROLES_VIEW') },
    { id: 'organization', label: 'Organization', visible: has(me, 'ORGANIZATION_VIEW') || has(me, 'BRANCHES_VIEW') },
    { id: 'security', label: 'Security', visible: true },
  ]

  return (
    <div className="app-frame">
      <aside className="sidebar">
        <div>
          <p className="product-kicker">Universal Education</p>
          <h1 className="product-title">Operations Platform</h1>
        </div>
        <nav aria-label="Primary navigation" className="nav-list">
          {nav.filter(item => item.visible).map(item => (
            <button key={item.id} className={section === item.id ? 'nav-item active' : 'nav-item'} onClick={() => setSection(item.id)}>
              {item.label}
            </button>
          ))}
        </nav>
        <div className="sidebar-footer">
          <p className="signed-in">{me.displayName}</p>
          <p className="muted small">{me.roles.join(' · ') || 'No assigned role'}</p>
          <button className="text-button" onClick={() => void logout()}>Sign out</button>
        </div>
      </aside>

      <main className="workspace">
        <header className="workspace-header">
          <div>
            <p className="eyebrow">Tenant {shortId(me.tenantId)}</p>
            <h2>{sectionTitle(section)}</h2>
          </div>
          <span className="version-badge">{version.data?.version ?? 'Platform'}</span>
        </header>

        {section === 'overview' && <Overview me={me} />}
        {section === 'schedule' && <ScheduleWorkspace me={me} />}
        {section === 'announcements' && <AnnouncementWorkspace me={me} />}
        {section === 'notifications' && <NotificationWorkspace />}
        {section === 'assessment' && <AssessmentWorkspace me={me} />}
        {section === 'assignments' && <AssignmentWorkspace me={me} />}
        {section === 'content' && <ContentWorkspace me={me} />}
        {section === 'academics' && <AcademicWorkspace me={me} />}
        {section === 'enrollment' && <EnrollmentWorkspace me={me} />}
        {section === 'people' && <People me={me} />}
        {section === 'roles' && <Roles me={me} />}
        {section === 'organization' && <Organization me={me} />}
        {section === 'security' && <Security me={me} />}
      </main>
    </div>
  )
}

function Overview({ me }: { me: CurrentIdentity }) {
  return (
    <div className="content-stack">
      <section className="hero-card">
        <p className="eyebrow">Today</p>
        <h3>Welcome, {me.displayName}</h3>
        <p>Your classes, examinations, institutional operations, announcements, and unread work are brought together here.</p>
      </section>
      <TodayOverview />
      <section className="metric-grid" aria-label="Identity overview">
        <Metric label="Membership" value={me.membershipStatus} />
        <Metric label="Roles" value={String(me.roles.length)} />
        <Metric label="Permissions" value={String(me.permissions.length)} />
        <Metric label="Authentication" value={me.authenticationAssurance.multiFactorEvidence ? 'MFA evidence' : 'Standard'} />
      </section>
      <section className="panel">
        <div className="panel-heading"><div><p className="eyebrow">Assigned roles</p><h3>Current responsibilities</h3></div></div>
        <div className="chip-row">
          {me.roles.length ? me.roles.map(role => <span className="chip" key={role}>{role}</span>) : <span className="muted">No roles assigned.</span>}
        </div>
      </section>
      <AcademicOverview me={me} />
    </div>
  )
}

function People({ me }: { me: CurrentIdentity }) {
  const queryClient = useQueryClient()
  const members = useQuery({ queryKey: ['members'], queryFn: fetchMembers })
  const roles = useQuery({ queryKey: ['roles'], queryFn: fetchRoles, enabled: has(me, 'ROLES_VIEW') })
  const mutation = useMutation({
    mutationFn: provisionMembership,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['members'] }),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const roleIds = Array.from((form.elements.namedItem('roleIds') as HTMLSelectElement | null)?.selectedOptions ?? []).map(option => option.value)
    mutation.mutate({
      subject: String(data.get('subject') ?? ''),
      email: String(data.get('email') ?? '') || undefined,
      displayName: String(data.get('displayName') ?? ''),
      roleIds,
    }, { onSuccess: () => form.reset() })
  }

  return (
    <div className="content-stack">
      {has(me, 'USERS_MANAGE') && (
        <section className="panel">
          <div className="panel-heading"><div><p className="eyebrow">Provision identity</p><h3>Add tenant membership</h3></div></div>
          <form className="form-grid" onSubmit={submit}>
            <label>OIDC subject<input name="subject" required maxLength={160} placeholder="Identity-provider subject" /></label>
            <label>Display name<input name="displayName" required maxLength={200} /></label>
            <label>Email<input name="email" type="email" maxLength={320} /></label>
            <label>Initial roles<select name="roleIds" multiple size={4}>{roles.data?.items.map(role => <option key={role.id} value={role.id}>{role.name}</option>)}</select></label>
            <div className="form-actions"><button className="primary-button" disabled={mutation.isPending}>Create membership</button></div>
            {mutation.isError && <p className="error-text" role="alert">{mutation.error.message}</p>}
          </form>
        </section>
      )}
      <section className="panel">
        <div className="panel-heading"><div><p className="eyebrow">Directory</p><h3>Tenant people</h3></div><span className="count-badge">{members.data?.totalElements ?? 0}</span></div>
        {members.isPending ? <LoadingLine /> : members.isError ? <ErrorLine error={members.error} /> : (
          <div className="table-wrap"><table><thead><tr><th>Name</th><th>Status</th><th>Roles</th><th>Identity subject</th></tr></thead><tbody>
            {members.data.items.map(member => <tr key={member.membershipId}><td><strong>{member.displayName}</strong><span>{member.email ?? 'No email'}</span></td><td>{member.membershipStatus}</td><td>{member.roles.map(role => role.name).join(', ') || '—'}</td><td><code>{member.oidcSubject}</code></td></tr>)}
          </tbody></table></div>
        )}
      </section>
    </div>
  )
}

function Roles({ me }: { me: CurrentIdentity }) {
  const queryClient = useQueryClient()
  const roles = useQuery({ queryKey: ['roles'], queryFn: fetchRoles })
  const mutation = useMutation({
    mutationFn: createRole,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['roles'] }),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const permissions = permissionOptions.filter(permission => data.get(permission) === 'on')
    mutation.mutate({ name: String(data.get('name') ?? ''), description: String(data.get('description') ?? '') || undefined, permissions }, { onSuccess: () => form.reset() })
  }

  return (
    <div className="content-stack">
      {has(me, 'ROLES_MANAGE') && (
        <section className="panel">
          <div className="panel-heading"><div><p className="eyebrow">Custom RBAC</p><h3>Create role</h3></div></div>
          <form className="form-grid" onSubmit={submit}>
            <label>Role name<input name="name" required maxLength={120} /></label>
            <label className="wide">Description<textarea name="description" maxLength={500} rows={2} /></label>
            <fieldset className="wide permission-grid"><legend>Permissions</legend>{permissionOptions.map(permission => <label className="check-row" key={permission}><input type="checkbox" name={permission} />{humanize(permission)}</label>)}</fieldset>
            <div className="form-actions"><button className="primary-button" disabled={mutation.isPending}>Create custom role</button></div>
            {mutation.isError && <p className="error-text" role="alert">{mutation.error.message}</p>}
          </form>
        </section>
      )}
      <section className="panel">
        <div className="panel-heading"><div><p className="eyebrow">Authorization catalog</p><h3>Roles</h3></div><span className="count-badge">{roles.data?.totalElements ?? 0}</span></div>
        {roles.isPending ? <LoadingLine /> : roles.isError ? <ErrorLine error={roles.error} /> : <RoleCards roles={roles.data.items} />}
      </section>
    </div>
  )
}

function RoleCards({ roles }: { roles: RoleView[] }) {
  return <div className="card-grid">{roles.map(role => <article className="mini-card" key={role.id}><div className="mini-card-title"><strong>{role.name}</strong><span>{role.systemManaged ? 'System' : 'Custom'}</span></div><p>{role.description ?? 'No description'}</p><div className="chip-row compact">{role.permissions.map(permission => <span className="chip" key={permission}>{humanize(permission)}</span>)}</div></article>)}</div>
}

function Organization({ me }: { me: CurrentIdentity }) {
  const queryClient = useQueryClient()
  const branches = useQuery({ queryKey: ['branches'], queryFn: fetchBranches, enabled: has(me, 'BRANCHES_VIEW') })
  const settings = useQuery({ queryKey: ['organization-settings'], queryFn: fetchOrganizationSettings, enabled: has(me, 'ORGANIZATION_VIEW') })
  const create = useMutation({ mutationFn: createBranch, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['branches'] }) })
  const saveSettings = useMutation({ mutationFn: updateOrganizationSettings, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['organization-settings'] }) })

  function submitBranch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    create.mutate({ code: String(data.get('code') ?? ''), displayName: String(data.get('displayName') ?? ''), timezone: String(data.get('timezone') ?? '') }, { onSuccess: () => form.reset() })
  }

  function submitSettings(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (!settings.data) return
    const data = new FormData(event.currentTarget)
    saveSettings.mutate({
      ...settings.data,
      defaultTimezone: String(data.get('defaultTimezone') ?? 'UTC'),
      defaultLocale: String(data.get('defaultLocale') ?? 'en'),
      weekStartsOn: Number(data.get('weekStartsOn') ?? 1),
      supportEmail: String(data.get('supportEmail') ?? '') || undefined,
      supportUrl: String(data.get('supportUrl') ?? '') || undefined,
    })
  }

  return (
    <div className="content-stack">
      {has(me, 'BRANCHES_MANAGE') && <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Structure</p><h3>Add branch</h3></div></div><form className="form-grid" onSubmit={submitBranch}><label>Code<input name="code" required maxLength={64} placeholder="MAIN" /></label><label>Display name<input name="displayName" required maxLength={200} /></label><label>Timezone<input name="timezone" required maxLength={80} defaultValue="Asia/Kolkata" /></label><div className="form-actions"><button className="primary-button" disabled={create.isPending}>Create branch</button></div>{create.isError && <p className="error-text" role="alert">{create.error.message}</p>}</form></section>}
      {has(me, 'BRANCHES_VIEW') && <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Locations</p><h3>Branches</h3></div></div>{branches.isPending ? <LoadingLine /> : branches.isError ? <ErrorLine error={branches.error} /> : <BranchCards branches={branches.data.items} />}</section>}
      {has(me, 'ORGANIZATION_VIEW') && <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Defaults</p><h3>Organization settings</h3></div></div>{settings.isPending ? <LoadingLine /> : settings.isError ? <ErrorLine error={settings.error} /> : <SettingsForm settings={settings.data} editable={has(me, 'ORGANIZATION_MANAGE')} onSubmit={submitSettings} pending={saveSettings.isPending} error={saveSettings.error} />}</section>}
    </div>
  )
}

function BranchCards({ branches }: { branches: BranchView[] }) {
  if (!branches.length) return <p className="muted">No branches have been created.</p>
  return <div className="card-grid">{branches.map(branch => <article className="mini-card" key={branch.id}><div className="mini-card-title"><strong>{branch.displayName}</strong><span>{branch.status}</span></div><p><code>{branch.code}</code> · {branch.timezone}</p></article>)}</div>
}

function SettingsForm({ settings, editable, onSubmit, pending, error }: { settings: OrganizationSettings; editable: boolean; onSubmit: (event: FormEvent<HTMLFormElement>) => void; pending: boolean; error: Error | null }) {
  return <form className="form-grid" onSubmit={onSubmit} key={settings.version}><label>Default timezone<input name="defaultTimezone" defaultValue={settings.defaultTimezone} disabled={!editable} /></label><label>Locale<input name="defaultLocale" defaultValue={settings.defaultLocale} disabled={!editable} /></label><label>Week starts on<select name="weekStartsOn" defaultValue={settings.weekStartsOn} disabled={!editable}>{[1,2,3,4,5,6,7].map(day => <option key={day} value={day}>{day}</option>)}</select></label><label>Support email<input name="supportEmail" type="email" defaultValue={settings.supportEmail} disabled={!editable} /></label><label className="wide">Support URL<input name="supportUrl" type="url" defaultValue={settings.supportUrl} disabled={!editable} /></label>{editable && <div className="form-actions"><button className="primary-button" disabled={pending}>Save settings</button></div>}{error && <p className="error-text" role="alert">{error.message}</p>}</form>
}

function Security({ me }: { me: CurrentIdentity }) {
  const queryClient = useQueryClient()
  const sessions = useQuery({ queryKey: ['my-sessions'], queryFn: fetchMySessions })
  const revoke = useMutation({ mutationFn: revokeOwnSession, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['my-sessions'] }) })
  const assurance = me.authenticationAssurance
  return <div className="content-stack"><section className="panel"><div className="panel-heading"><div><p className="eyebrow">Authentication</p><h3>Strong sign-in</h3></div></div><div className="metric-grid"><Metric label="ACR" value={assurance.acr || 'Not asserted'} /><Metric label="OTP evidence" value={assurance.otpEvidence ? 'Present' : 'Not present'} /><Metric label="WebAuthn evidence" value={assurance.webAuthnEvidence ? 'Present' : 'Not present'} /></div><div className="button-row"><button className="primary-button" onClick={() => void enrollPasskey()}>Register passkey</button><button className="secondary-button" onClick={() => void enrollOtp()}>Configure OTP</button><button className="secondary-button" onClick={() => void openAccountManagement()}>Identity account</button></div></section><section className="panel"><div className="panel-heading"><div><p className="eyebrow">Session control</p><h3>Platform sessions</h3></div></div>{sessions.isPending ? <LoadingLine /> : sessions.isError ? <ErrorLine error={sessions.error} /> : <div className="session-list">{sessions.data.items.map(session => <div className="session-row" key={session.id}><div><strong>{session.current ? 'Current session' : 'Authenticated session'}</strong><span>Last seen {new Date(session.lastSeenAt).toLocaleString()}</span></div><div>{session.revokedAt ? <span className="danger-chip">Revoked</span> : <button className="text-button" disabled={revoke.isPending} onClick={() => revoke.mutate(session.id)}>Revoke</button>}</div></div>)}</div>}</section></div>
}

function Metric({ label, value }: { label: string; value: string }) { return <div className="metric"><span>{label}</span><strong>{value}</strong></div> }
function LoadingLine() { return <p className="muted" aria-live="polite">Loading…</p> }
function ErrorLine({ error }: { error: Error }) { return <p className="error-text" role="alert">{error.message}</p> }
function CenteredStatus({ title, detail }: { title: string; detail: string }) { return <main className="centered-status"><section className="hero-card"><p className="eyebrow">Universal Education Platform</p><h1>{title}</h1><p>{detail}</p></section></main> }
function has(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) }
function hasScoped(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) || me.primaryBranchPermissions.includes(permission) }
function shortId(value: string) { return value.slice(0, 8) }
function humanize(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()) }
function sectionTitle(section: Section) { return ({ overview: 'Today', content: 'Learning Content', schedule: 'Schedule', announcements: 'Announcements', notifications: 'Notifications', academics: 'Academic core', enrollment: 'Enrollment & teaching', people: 'People & membership', roles: 'Roles & permissions', organization: 'Organization', security: 'Security & sessions' })[section] }
