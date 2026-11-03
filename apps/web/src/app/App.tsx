import { useEffect, useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  enrollOtp,
  enrollPasskey,
  isAuthenticated as isOidcAuthenticated,
  logout,
  onOidcStatus,
  openAccountManagement,
  signIn,
  localMode,
  localApiMode,
} from '../auth/keycloak'
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
import { LiveLearningWorkspace } from '../live/LiveLearningWorkspace'
import { RecordingWorkspace } from '../recording/RecordingWorkspace'
import { FinanceWorkspace } from '../finance/FinanceWorkspace'
import { AnalyticsWorkspace } from '../analytics/AnalyticsWorkspace'
import { AdvancedAcademicWorkspace } from '../advanced/AdvancedAcademicWorkspace'

import { IntegrationWorkspace } from '../integration/IntegrationWorkspace'
import { LocalExperience } from '../dashboard/LocalExperience'
import {
  assignRole,
  createBranch,
  createRole,
  deleteRole,
  fetchBranches,
  fetchCurrentIdentity,
  fetchMembers,
  fetchMemberSessions,
  fetchMySessions,
  fetchOrganizationSettings,
  fetchPlatformVersion,
  fetchRoles,
  fetchRoleAssignments,
  provisionMembership,
  revokeOwnSession,
  revokeMemberSession,
  updateOrganizationSettings,
  updateMembership,
  updateRole,
  unassignRole,
} from '../platform/api'
import type { BranchView, CurrentIdentity, MemberView, OrganizationSettings, PermissionKey, RoleAssignmentView, RoleView } from '../platform/api'

type Section = 'overview' | 'advanced' | 'integrations' | 'analytics' | 'live' | 'recordings' | 'finance' | 'assessment' | 'assignments' | 'content' | 'schedule' | 'announcements' | 'notifications' | 'academics' | 'enrollment' | 'people' | 'roles' | 'organization' | 'security'

const permissionOptions: PermissionKey[] = [
  'ORGANIZATION_VIEW', 'ORGANIZATION_MANAGE', 'BRANCHES_VIEW', 'BRANCHES_MANAGE',
  'USERS_VIEW', 'USERS_MANAGE', 'ROLES_VIEW', 'ROLES_MANAGE', 'ROLES_ASSIGN',
  'SESSIONS_VIEW', 'SESSIONS_MANAGE',
  'ACADEMICS_VIEW', 'ACADEMICS_MANAGE', 'CURRICULUM_VIEW', 'CURRICULUM_MANAGE',
  'ENROLLMENTS_VIEW', 'ENROLLMENTS_MANAGE', 'TEACHING_ASSIGNMENTS_VIEW', 'TEACHING_ASSIGNMENTS_MANAGE',
  'SCHEDULE_VIEW', 'SCHEDULE_MANAGE', 'SCHEDULE_CONFLICT_OVERRIDE',
  'ANNOUNCEMENTS_VIEW', 'ANNOUNCEMENTS_MANAGE', 'NOTIFICATION_OPERATIONS_VIEW', 'CONTENT_VIEW', 'CONTENT_MANAGE',
  'ASSESSMENTS_VIEW', 'ASSESSMENTS_MANAGE', 'ASSESSMENTS_TAKE', 'GRADING_VIEW', 'GRADING_MANAGE', 'REVIEW_VIEW', 'REVIEW_SUBMIT', 'REVIEW_MANAGE',
  'ASSIGNMENTS_VIEW', 'ASSIGNMENTS_MANAGE', 'ASSIGNMENTS_TAKE', 'GRADEBOOK_VIEW', 'GRADEBOOK_MANAGE',
  'LIVE_CLASS_VIEW', 'LIVE_CLASS_MANAGE', 'LIVE_CLASS_MODERATE', 'LIVE_CLASS_CHAT',
  'PRESENCE_VIEW', 'PRESENCE_MANAGE', 'ATTENDANCE_VIEW', 'ATTENDANCE_MANAGE',
  'RECORDINGS_VIEW', 'RECORDINGS_MANAGE', 'LEARNING_OUTCOMES_VIEW', 'LEARNING_OUTCOMES_MANAGE',
  'CREDENTIALS_VIEW', 'CREDENTIALS_MANAGE', 'CREDENTIALS_VERIFY', 'MENTORING_VIEW', 'MENTORING_MANAGE',
  'SURVEYS_VIEW', 'SURVEYS_MANAGE', 'FEEDBACK_VIEW', 'FEEDBACK_MANAGE', 'MESSAGING_MODERATE', 'API_VIEW', 'API_MANAGE', 'WEBHOOKS_VIEW', 'WEBHOOKS_MANAGE', 'FEDERATION_VIEW', 'FEDERATION_MANAGE', 'INTEROPERABILITY_VIEW', 'INTEROPERABILITY_MANAGE', 'EXTERNAL_STORAGE_MANAGE', 'EXTERNAL_NOTIFICATIONS_MANAGE', 'FINANCE_VIEW', 'FINANCE_MANAGE', 'COMMERCIAL_VIEW', 'COMMERCIAL_MANAGE', 'PAYMENTS_MANAGE', 'ANALYTICS_VIEW', 'ANALYTICS_MANAGE', 'REPORTS_EXPORT', 'OPERATIONS_VIEW', 'OPERATIONS_MANAGE', 'AUDIT_VIEW',
]

export function App() {
  const [section, setSection] = useState<Section>('overview')
  const [oidcAuthenticated, setOidcAuthenticated] = useState<boolean>(() => isOidcAuthenticated())
  const [signInPending, setSignInPending] = useState(false)
  const [signInError, setSignInError] = useState<string | null>(null)
  const queryClient = useQueryClient()
  const presentationLabel = import.meta.env.VITE_UEP_PRESENTATION_LABEL?.trim()
  const identity = useQuery({ queryKey: ['me'], queryFn: fetchCurrentIdentity, enabled: oidcAuthenticated && !localMode })
  const version = useQuery({ queryKey: ['platform-version'], queryFn: ({ signal }: { signal: AbortSignal }) => fetchPlatformVersion(signal), staleTime: 300_000, enabled: oidcAuthenticated && !localMode })

  useEffect(() => onOidcStatus(setOidcAuthenticated), [])

  // Determine initial dashboard section based on user role after authentication
  useEffect(() => {
    if (oidcAuthenticated && identity.data) {
      setSection(determineInitialSection(identity.data))
    }
  }, [oidcAuthenticated, identity.data])

  function determineInitialSection(me: CurrentIdentity): Section {
    // Check for Student role - primary role for learners
    const isStudent = me.roles.includes('Student')
    const isTeacher = me.roles.includes('Teacher')
    const isAdmin = me.roles.some(r => 
      ['Organization Owner', 'Organization Administrator', 'Branch Administrator', 'Academic Administrator'].includes(r)
    )

    // Route based on user type
    if (isAdmin) {
      return 'overview' // Administrators see overview first with full navigation
    }
    if (isTeacher) {
      return 'academics' // Teachers see academic content first
    }
    if (isStudent) {
      return 'overview' // Students start with overview
    }
    return 'overview' // Default fallback
  }

  function handleSignIn() {
    setSignInPending(true)
    setSignInError(null)
    void signIn()
      .catch(error => {
        setSignInError(error instanceof Error ? error.message : 'Sign-in could not be started')
        setSignInPending(false)
      })
      .finally(() => setSignInPending(false))
  }

  function handleSignOut() {
    queryClient.clear()
    // Clear the Offline Demo tab session and return to its own account selector.
    if (localMode) {
      sessionStorage.removeItem('uep-local-account')
      setOidcAuthenticated(false)
      setSignInPending(false)
      return
    }
    // Local-native credentials are held by the launcher/Vite process, not the browser.
    // They cannot be revoked by this button. Do not route into a no-op Keycloak login.
    if (localApiMode) return
    // Perform Keycloak logout for production OIDC sessions.
    void logout()
      .then(() => {
        setOidcAuthenticated(false)
        setSignInPending(false)
      })
      .catch(() => {
        // Ensure state is cleared even if logout fails
        setOidcAuthenticated(false)
        setSignInPending(false)
      })
  }

  if (!oidcAuthenticated) {
    return (
      <main className="centered-status">
        <section className="hero-card">
          <p className="eyebrow">Universal Education Platform</p>
          <h1>Sign in required</h1>
          <p>Your single sign-on session is not active. Continue to the identity provider to authenticate.</p>
          <div className="button-row">
            <button className="primary-button" disabled={signInPending} onClick={handleSignIn}>
              Sign in with Keycloak
            </button>
          </div>
          {signInError && <p className="error-text" role="alert">{signInError}</p>}
        </section>
      </main>
    )
  }

  if (localMode) return <LocalExperience />

  if (identity.isPending) return <CenteredStatus title="Loading your workspace" detail="Resolving tenant identity and permissions…" />
    if (identity.isError) return <CenteredStatus title="Access unavailable" detail={identity.error.message} />

  const me = identity.data
  const initials = me.displayName.split(/\s+/).filter(Boolean).slice(0, 2).map(part => part[0]?.toUpperCase() ?? '').join('') || 'U'
  const nav: Array<{ id: Section; label: string; visible: boolean }> = [
    { id: 'overview', label: 'Today', visible: true },
    { id: 'analytics', label: 'Analytics & Operations', visible: has(me, 'ANALYTICS_VIEW') },
    { id: 'integrations', label: 'Integrations', visible: has(me, 'API_VIEW') || has(me, 'WEBHOOKS_VIEW') || has(me, 'FEDERATION_VIEW') || has(me, 'INTEROPERABILITY_VIEW') },
    { id: 'live', label: 'Live Learning', visible: has(me, 'LIVE_CLASS_VIEW') || has(me, 'LIVE_CLASS_MANAGE') },
    { id: 'recordings', label: 'Recordings', visible: has(me, 'RECORDINGS_VIEW') || has(me, 'RECORDINGS_MANAGE') },
    { id: 'finance', label: 'Finance', visible: has(me, 'FINANCE_VIEW') || has(me, 'FINANCE_MANAGE') },
    { id: 'schedule', label: 'Schedule', visible: hasScoped(me, 'SCHEDULE_VIEW') },
    { id: 'announcements', label: 'Announcements', visible: hasScoped(me, 'ANNOUNCEMENTS_VIEW') },
    { id: 'notifications', label: 'Notifications', visible: true },
    { id: 'assessment', label: 'Assessments', visible: has(me, 'ASSESSMENTS_VIEW') || has(me, 'ASSESSMENTS_TAKE') },
    { id: 'assignments', label: 'Assignments', visible: has(me, 'ASSIGNMENTS_VIEW') || has(me, 'ASSIGNMENTS_TAKE') || has(me, 'GRADEBOOK_VIEW') },
    { id: 'content', label: 'Learning Content', visible: has(me, 'CONTENT_VIEW') },
    { id: 'academics', label: 'Academics', visible: has(me, 'ACADEMICS_VIEW') || has(me, 'CURRICULUM_VIEW') },
    { id: 'advanced', label: 'Advanced Academics', visible: has(me, 'LEARNING_OUTCOMES_MANAGE') || has(me, 'CREDENTIALS_MANAGE') || has(me, 'MENTORING_MANAGE') || has(me, 'SURVEYS_MANAGE') || has(me, 'LEARNING_OUTCOMES_VIEW') || has(me, 'CREDENTIALS_VIEW') || has(me, 'MENTORING_VIEW') || has(me, 'SURVEYS_VIEW') || has(me, 'FEEDBACK_MANAGE') },
    { id: 'enrollment', label: 'Enrollment', visible: has(me, 'ENROLLMENTS_VIEW') || has(me, 'TEACHING_ASSIGNMENTS_VIEW') },
    { id: 'people', label: 'People', visible: has(me, 'USERS_VIEW') },
    { id: 'roles', label: 'Roles', visible: has(me, 'ROLES_VIEW') },
    { id: 'organization', label: 'Organization', visible: has(me, 'ORGANIZATION_VIEW') || has(me, 'BRANCHES_VIEW') },
    { id: 'security', label: 'Security', visible: has(me, 'SESSIONS_VIEW') || has(me, 'SESSIONS_MANAGE') },
    
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
          {localMode && (
            <div className="footer-actions">
              <button className="secondary-button" onClick={() => setSection('overview')}>
                Cancel
              </button>
            </div>
          )}
        </div>
      </aside>

      <main className="workspace">
        <header className="workspace-header">
          <div>
            <p className="eyebrow">Tenant {shortId(me.tenantId)}</p>
            <h2>{sectionTitle(section)}</h2>
          </div>
          <div className="workspace-header-actions">
            {presentationLabel
              ? <span className="environment-badge environment-badge-real">{presentationLabel}</span>
              : <span className="version-badge">{version.data?.version ?? 'Platform'}</span>}
            <div className="session-chip">
              <span className="session-avatar" aria-hidden="true">{initials}</span>
              <span className="session-meta">
                <strong>{me.displayName}</strong>
                <small>{me.roles[0] ?? 'Signed in'}</small>
              </span>
              {localApiMode
                ? (<>
                    <span className="chip" title="The local owner session is managed by the PowerShell launcher">Launcher-managed session</span>
                    <a className="secondary-button" href="http://127.0.0.1:5174/" target="_blank" rel="noopener noreferrer" title="Open the separate Offline Demo account selector">Open demo accounts</a>
                  </>)
                : <button className="secondary-button" onClick={handleSignOut} title="Sign out of UEP">Sign out</button>}
            </div>
          </div>
        </header>

        {section === 'overview' && <Overview me={me} />}
        {section === 'advanced' && <AdvancedAcademicWorkspace me={me} />}
        {section === 'integrations' && <IntegrationWorkspace me={me} />}
        {section === 'analytics' && <AnalyticsWorkspace me={me} />}
        {section === 'schedule' && <ScheduleWorkspace me={me} />}
        {section === 'live' && <LiveLearningWorkspace me={me} />}
        {section === 'recordings' && <RecordingWorkspace me={me} />}
        {section === 'finance' && <FinanceWorkspace me={me} />}
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
  const branches = useQuery({ queryKey: ['branches'], queryFn: fetchBranches, enabled: has(me, 'BRANCHES_VIEW') })
  const mutation = useMutation({
    mutationFn: provisionMembership,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['members'] }),
  })
  const updateMutation = useMutation({
    mutationFn: ({ membershipId, input }: {
      membershipId: string
      input: { status: 'ACTIVE' | 'SUSPENDED' | 'ENDED'; primaryBranchId?: string | null; externalReference?: string | null; expectedVersion: number }
    }) => updateMembership(membershipId, input),
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
          <div className="table-wrap"><table aria-label="Tenant member directory"><thead><tr><th>Name</th><th>Status</th><th>Roles</th><th>Identity subject</th>{has(me, 'USERS_MANAGE') && <th>Manage membership</th>}{(has(me, 'ROLES_VIEW') || has(me, 'ROLES_ASSIGN')) && <th>Role assignments</th>}</tr></thead><tbody>
            {members.data.items.map(member => <tr key={member.membershipId}>
              <td><strong>{member.displayName}</strong><span>{member.email ?? 'No email'}</span></td>
              <td>{member.membershipStatus}</td>
              <td>{member.roles.map(role => role.name).join(', ') || '—'}</td>
              <td><code>{member.oidcSubject}</code></td>
              {has(me, 'USERS_MANAGE') && <td>
                <form key={`${member.membershipId}:${member.version}`} className="form-grid" onSubmit={event => {
                  event.preventDefault()
                  const data = new FormData(event.currentTarget)
                  const primaryBranchId = has(me, 'BRANCHES_VIEW')
                    ? (String(data.get('primaryBranchId') ?? '') || null)
                    : (member.primaryBranchId ?? null)
                  updateMutation.mutate({
                    membershipId: member.membershipId,
                    input: {
                      status: String(data.get('status')) as 'ACTIVE' | 'SUSPENDED' | 'ENDED',
                      primaryBranchId,
                      externalReference: String(data.get('externalReference') ?? '') || null,
                      expectedVersion: member.version,
                    },
                  })
                }}>
                  <label>Status<select name="status" defaultValue={member.membershipStatus}>
                    <option value="ACTIVE">Active</option><option value="SUSPENDED">Suspended</option><option value="ENDED">Ended</option>
                  </select></label>
                  {has(me, 'BRANCHES_VIEW') && branches.data
                    ? <label>Primary branch<select name="primaryBranchId" defaultValue={member.primaryBranchId ?? ''}>
                        <option value="">No primary branch</option>
                        {member.primaryBranchId && !branches.data.items.some(branch => branch.id === member.primaryBranchId)
                          && <option value={member.primaryBranchId}>Current branch ({shortId(member.primaryBranchId)})</option>}
                        {branches.data.items.map(branch => <option key={branch.id} value={branch.id}>{branch.displayName}</option>)}
                      </select></label>
                    : <input type="hidden" name="primaryBranchId" defaultValue={member.primaryBranchId ?? ''} />}
                  <label>External reference<input name="externalReference" maxLength={160} defaultValue={member.externalReference ?? ''} /></label>
                  <button className="primary-button" disabled={updateMutation.isPending}>Save membership</button>
                  {updateMutation.isError && updateMutation.variables?.membershipId === member.membershipId && <p className="error-text" role="alert">{updateMutation.error.message}</p>}
                </form>
              </td>}
              {(has(me, 'ROLES_VIEW') || has(me, 'ROLES_ASSIGN')) && <td>
                <MembershipRoleAssignments
                  member={member}
                  me={me}
                  roles={roles.data?.items ?? []}
                  branches={branches.data?.items ?? []}
                />
              </td>}
            </tr>)}
          </tbody></table></div>
        )}
      </section>
    </div>
  )
}


function MembershipRoleAssignments({
  member,
  me,
  roles,
  branches,
}: {
  member: MemberView
  me: CurrentIdentity
  roles: RoleView[]
  branches: BranchView[]
}) {
  const queryClient = useQueryClient()
  const [scopeError, setScopeError] = useState('')
  const canViewAssignments = has(me, 'ROLES_VIEW')
  const canAssign = has(me, 'ROLES_ASSIGN') && canViewAssignments
  const assignments = useQuery({
    queryKey: ['role-assignments', member.membershipId],
    queryFn: () => fetchRoleAssignments(member.membershipId),
    enabled: canViewAssignments,
  })
  const assignMutation = useMutation({
    mutationFn: assignRole,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['role-assignments', member.membershipId] })
      void queryClient.invalidateQueries({ queryKey: ['members'] })
    },
  })
  const unassignMutation = useMutation({
    mutationFn: unassignRole,
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['role-assignments', member.membershipId] })
      void queryClient.invalidateQueries({ queryKey: ['members'] })
    },
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const scopeKind = String(data.get('scopeKind')) === 'BRANCH' ? 'BRANCH' : 'TENANT'
    const scopeId = String(data.get('scopeId') ?? '')
    if (scopeKind === 'BRANCH' && !scopeId) {
      setScopeError('Select a branch before assigning a branch-scoped role.')
      return
    }
    setScopeError('')
    assignMutation.mutate({
      membershipId: member.membershipId,
      roleId: String(data.get('roleId') ?? ''),
      scopeKind,
      ...(scopeKind === 'BRANCH' ? { scopeId } : {}),
    })
  }

  if (!canViewAssignments && !has(me, 'ROLES_ASSIGN')) return null

  return (
    <section className="content-stack">
      <p className="eyebrow">Role assignments</p>
      {canViewAssignments && (
        assignments.isPending ? <LoadingLine /> : assignments.isError ? <ErrorLine error={assignments.error} /> : (
          <div className="chip-row">
            {assignments.data.items.length ? assignments.data.items.map((assignment: RoleAssignmentView) => (
              <span className="chip" key={assignment.id}>
                {assignment.roleName} · {assignment.scopeKind === 'TENANT' ? 'Tenant-wide' : `Branch ${shortId(assignment.scopeId ?? '')}`}
                {has(me, 'ROLES_ASSIGN') && (
                  <button
                    type="button"
                    className="quiet-button"
                    aria-label={`Remove ${assignment.roleName} assignment`}
                    disabled={unassignMutation.isPending}
                    onClick={() => unassignMutation.mutate(assignment.id)}
                  >Remove</button>
                )}
              </span>
            )) : <span className="muted">No role assignments.</span>}
          </div>
        )
      )}
      {canAssign && roles.length > 0 && member.membershipStatus !== 'ENDED' && (
        <form className="form-grid" onSubmit={submit}>
          <label>Assign role<select name="roleId" required defaultValue="">
            <option value="" disabled>Select a role</option>
            {roles.map(role => <option key={role.id} value={role.id}>{role.name}</option>)}
          </select></label>
          <label>Assignment scope<select name="scopeKind" defaultValue="TENANT">
            <option value="TENANT">Entire tenant</option>
            {has(me, 'BRANCHES_VIEW') && branches.length > 0 && <option value="BRANCH">One branch</option>}
          </select></label>
          {has(me, 'BRANCHES_VIEW') && branches.length > 0 && (
            <label>Branch<select name="scopeId" defaultValue="">
              <option value="">Select branch for branch scope</option>
              {branches.map(branch => <option key={branch.id} value={branch.id}>{branch.displayName}</option>)}
            </select></label>
          )}
          <button className="primary-button" disabled={assignMutation.isPending}>Assign role</button>
          {scopeError && <p className="error-text" role="alert">{scopeError}</p>}
          {assignMutation.isError && <p className="error-text" role="alert">{assignMutation.error.message}</p>}
        </form>
      )}
      {has(me, 'ROLES_ASSIGN') && !canViewAssignments && (
        <p className="muted">Role assignment listing requires ROLES_VIEW; this account currently has assignment permission without view permission.</p>
      )}
      {unassignMutation.isError && <p className="error-text" role="alert">{unassignMutation.error.message}</p>}
    </section>
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
        {roles.isPending ? <LoadingLine /> : roles.isError ? <ErrorLine error={roles.error} /> : <RoleCards roles={roles.data.items} canManage={has(me, 'ROLES_MANAGE')} />}
      </section>
    </div>
  )
}

function RoleCards({ roles, canManage }: { roles: RoleView[]; canManage: boolean }) {
  return <div className="card-grid">{roles.map(role => <RoleCard key={role.id} role={role} canManage={canManage} />)}</div>
}

function RoleCard({ role, canManage }: { role: RoleView; canManage: boolean }) {
  const queryClient = useQueryClient()
  const updateMutation = useMutation({
    mutationFn: (input: { name: string; description?: string; permissions: PermissionKey[]; expectedVersion: number }) => updateRole(role.id, input),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['roles'] }),
  })
  const deleteMutation = useMutation({
    mutationFn: () => deleteRole(role.id),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['roles'] }),
  })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const permissions = permissionOptions.filter(permission => data.get(permission) === 'on')
    updateMutation.mutate({
      name: String(data.get('name') ?? ''),
      description: String(data.get('description') ?? '') || undefined,
      permissions,
      expectedVersion: role.version,
    })
  }

  return (
    <article className="mini-card" data-role-id={role.id}>
      <div className="mini-card-title"><strong>{role.name}</strong><span>{role.systemManaged ? 'System' : 'Custom'}</span></div>
      <p>{role.description ?? 'No description'}</p>
      <div className="chip-row compact">{role.permissions.map(permission => <span className="chip" key={permission}>{humanize(permission)}</span>)}</div>
      {canManage && !role.systemManaged && (
        <details className="content-stack">
          <summary>Edit custom role</summary>
          <form key={`${role.id}:${role.version}`} className="form-grid" onSubmit={submit}>
            <label>Role name<input name="name" required maxLength={120} defaultValue={role.name} /></label>
            <label className="wide">Description<textarea name="description" maxLength={500} rows={2} defaultValue={role.description ?? ''} /></label>
            <fieldset className="wide permission-grid">
              <legend>Permissions</legend>
              {permissionOptions.map(permission => (
                <label className="check-row" key={permission}>
                  <input type="checkbox" name={permission} defaultChecked={role.permissions.includes(permission)} />
                  {humanize(permission)}
                </label>
              ))}
            </fieldset>
            <div className="form-actions">
              <button className="primary-button" disabled={updateMutation.isPending || deleteMutation.isPending}>Save role changes</button>
              <button type="button" className="secondary-button" disabled={updateMutation.isPending || deleteMutation.isPending} onClick={() => {
                if (window.confirm(`Delete custom role "${role.name}"? This cannot be undone.`)) deleteMutation.mutate()
              }}>Delete custom role</button>
            </div>
            <p className="muted small">A custom role must be unassigned from every membership before it can be deleted.</p>
            {updateMutation.isError && <p className="error-text" role="alert">{updateMutation.error.message}</p>}
            {deleteMutation.isError && <p className="error-text" role="alert">{deleteMutation.error.message}</p>}
          </form>
        </details>
      )}
    </article>
  )
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
  return <form className="form-grid" onSubmit={onSubmit} key={settings.version}><label>Default timezone<input name="defaultTimezone" defaultValue={settings.defaultTimezone} disabled={!editable} /></label><label>Locale<input name="defaultLocale" defaultValue={settings.defaultLocale} disabled={!editable} /></label><label>Week starts on<select name="weekStartsOn" defaultValue={settings.weekStartsOn} disabled={!editable}>{[1, 2, 3, 4, 5, 6, 7].map(day => <option key={day} value={day}>{day}</option>)}</select></label><label>Support email<input name="supportEmail" type="email" defaultValue={settings.supportEmail} disabled={!editable} /></label><label className="wide">Support URL<input name="supportUrl" type="url" defaultValue={settings.supportUrl} disabled={!editable} /></label>{editable && <div className="form-actions"><button className="primary-button" disabled={pending}>Save settings</button></div>}{error && <p className="error-text" role="alert">{error.message}</p>}</form>
}

function Security({ me }: { me: CurrentIdentity }) {
  const queryClient = useQueryClient()
  // Listing tenant memberships requires USERS_VIEW in addition to SESSIONS_VIEW.
  const canViewMemberSessions = has(me, 'USERS_VIEW') && has(me, 'SESSIONS_VIEW')
  const canManageMemberSessions = canViewMemberSessions && has(me, 'SESSIONS_MANAGE')
  const [selectedMembershipId, setSelectedMembershipId] = useState('')
  const sessions = useQuery({ queryKey: ['my-sessions'], queryFn: fetchMySessions })
  const members = useQuery({ queryKey: ['session-members'], queryFn: fetchMembers, enabled: canViewMemberSessions })
  const memberSessions = useQuery({
    queryKey: ['member-sessions', selectedMembershipId],
    queryFn: () => fetchMemberSessions(selectedMembershipId),
    enabled: canViewMemberSessions && Boolean(selectedMembershipId),
  })
  const revoke = useMutation({
    mutationFn: revokeOwnSession,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['my-sessions'] }),
  })
  const adminRevoke = useMutation({
    mutationFn: ({ sessionId, reason }: { sessionId: string; reason: string }) => revokeMemberSession(sessionId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['member-sessions', selectedMembershipId] })
      void queryClient.invalidateQueries({ queryKey: ['my-sessions'] })
    },
  })
  const assurance = me.authenticationAssurance
  const manageableMembers = (members.data?.items ?? []).filter(member => member.membershipId !== me.membershipId)

  return (
    <div className="content-stack">
      <section className="panel">
        <div className="panel-heading"><div><p className="eyebrow">Authentication</p><h3>Strong sign-in</h3></div></div>
        <div className="metric-grid">
          <Metric label="ACR" value={assurance.acr || 'Not asserted'} />
          <Metric label="OTP evidence" value={assurance.otpEvidence ? 'Present' : 'Not present'} />
          <Metric label="WebAuthn evidence" value={assurance.webAuthnEvidence ? 'Present' : 'Not present'} />
        </div>
        {localApiMode
          ? <p className="muted small">Keycloak is intentionally not started in this local-native profile. This Real UEP window uses one persisted Organization Owner identity and a launcher-managed loopback session; it does not impersonate seeded personas. To end this local session, stop the PowerShell launcher (Ctrl+C). To explore another role, use Open demo accounts in the workspace header. Production OIDC sessions retain the normal Sign out flow.</p>
          : <div className="button-row">
              <button className="primary-button" onClick={() => void enrollPasskey()}>Register passkey</button>
              <button className="secondary-button" onClick={() => void enrollOtp()}>Configure OTP</button>
              <button className="secondary-button" onClick={() => void openAccountManagement()}>Identity account</button>
            </div>}
      </section>

      <section className="panel">
        <div className="panel-heading"><div><p className="eyebrow">Self-service session control</p><h3>My platform sessions</h3></div></div>
        {sessions.isPending ? <LoadingLine /> : sessions.isError ? <ErrorLine error={sessions.error} /> : (
          <div className="session-list">
            {sessions.data.items.map(session => (
              <div className="session-row" key={session.id}>
                <div>
                  <strong>{session.current ? 'Current session' : 'Authenticated session'}</strong>
                  <span>Last seen {new Date(session.lastSeenAt).toLocaleString()}</span>
                </div>
                <div>
                  {session.revokedAt
                    ? <span className="danger-chip">Revoked</span>
                    : localApiMode && session.current
                      ? <span className="chip">Launcher-managed</span>
                      : <button className="text-button" disabled={revoke.isPending} onClick={() => revoke.mutate(session.id)}>Revoke</button>}
                </div>
              </div>
            ))}
          </div>
        )}
        {revoke.isError && <p className="error-text" role="alert">{revoke.error.message}</p>}
      </section>

      {canViewMemberSessions && (
        <section className="panel">
          <div className="panel-heading">
            <div><p className="eyebrow">Administrator session control</p><h3>Other member sessions</h3></div>
          </div>
          <p className="muted small">Requires Users View and Sessions View. Sessions Manage additionally enables revocation. Your own membership is excluded here; manage your own sessions above.</p>
          {members.isPending ? <LoadingLine /> : members.isError ? <ErrorLine error={members.error} /> : (
            <>
              <label>
                Tenant member
                <select value={selectedMembershipId} onChange={event => setSelectedMembershipId(event.target.value)}>
                  <option value="">Select a member</option>
                  {manageableMembers.map(member => (
                    <option key={member.membershipId} value={member.membershipId}>
                      {member.displayName} · {member.membershipStatus}
                    </option>
                  ))}
                </select>
              </label>
              {manageableMembers.length === 0 && <p className="muted">No other tenant memberships are available.</p>}
              {selectedMembershipId && (
                memberSessions.isPending ? <LoadingLine /> : memberSessions.isError ? <ErrorLine error={memberSessions.error} /> : (
                  <div className="session-list">
                    {memberSessions.data.items.length === 0 && <p className="muted">No observed platform sessions for this member.</p>}
                    {memberSessions.data.items.map(session => (
                      <div className="session-row" key={session.id}>
                        <div>
                          <strong>Member session · {shortId(session.id)}</strong>
                          <span>Last seen {new Date(session.lastSeenAt).toLocaleString()}</span>
                          {session.expiresAt && <span>Expires {new Date(session.expiresAt).toLocaleString()}</span>}
                          {session.revocationReason && <span>Reason: {session.revocationReason}</span>}
                        </div>
                        <div>
                          {session.revokedAt
                            ? <span className="danger-chip">Revoked</span>
                            : canManageMemberSessions
                              ? <button className="text-button" disabled={adminRevoke.isPending} onClick={() => {
                                  if (window.confirm('Revoke this member session? The member will need to authenticate again.')) {
                                    adminRevoke.mutate({ sessionId: session.id, reason: 'Administrator revoked session from the Security workspace' })
                                  }
                                }}>Revoke member session</button>
                              : <span className="muted small">Read only</span>}
                        </div>
                      </div>
                    ))}
                  </div>
                )
              )}
              {adminRevoke.isError && <p className="error-text" role="alert">{adminRevoke.error.message}</p>}
            </>
          )}
        </section>
      )}
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string }) { return <div className="metric"><span>{label}</span><strong>{value}</strong></div> }
function LoadingLine() { return <p className="muted" aria-live="polite">Loading…</p> }
function ErrorLine({ error }: { error: Error }) { return <p className="error-text" role="alert">{error.message}</p> }
function CenteredStatus({ title, detail }: { title: string; detail: string }) { return <main className="centered-status"><section className="hero-card"><p className="eyebrow">Universal Education Platform</p><h1>{title}</h1><p>{detail}</p></section></main> }
function has(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) }
function hasScoped(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) || me.primaryBranchPermissions.includes(permission) }
function shortId(value: string) { return value.slice(0, 8) }
function humanize(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()) }
function sectionTitle(section: Section) { return ({ overview: 'Today', advanced: 'Advanced academics', integrations: 'Integrations', analytics: 'Analytics & Operations', live: 'Live Learning', recordings: 'Classroom recordings', finance: 'Finance', assessment: 'Assessments & grading', assignments: 'Assignments & gradebook', content: 'Learning Content', schedule: 'Schedule', announcements: 'Announcements', notifications: 'Notifications', academics: 'Academic core', enrollment: 'Enrollment & teaching', people: 'People & membership', roles: 'Roles & permissions', organization: 'Organization', security: 'Security & sessions' })[section] }
