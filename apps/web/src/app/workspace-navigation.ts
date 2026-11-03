import type { PermissionKey } from '../platform/api'

export type WorkspaceSection =
  | 'overview'
  | 'advanced'
  | 'integrations'
  | 'analytics'
  | 'live'
  | 'recordings'
  | 'finance'
  | 'assessment'
  | 'assignments'
  | 'content'
  | 'schedule'
  | 'announcements'
  | 'notifications'
  | 'academics'
  | 'enrollment'
  | 'people'
  | 'roles'
  | 'organization'
  | 'security'

export interface WorkspaceNavigationItem {
  id: WorkspaceSection
  label: string
  visible: boolean
}

export interface WorkspaceNavigationGroup {
  label: string
  items: WorkspaceNavigationItem[]
}

export interface WorkspaceNavigationAccess {
  has: (permission: PermissionKey) => boolean
  hasScoped: (permission: PermissionKey) => boolean
}

/** Builds the Real workspace's navigation from the signed-in identity's capabilities. */
export function buildWorkspaceNavigation(access: WorkspaceNavigationAccess): WorkspaceNavigationGroup[] {
  return [
    {
      label: 'My workspace',
      items: [
        { id: 'overview', label: 'Today', visible: true },
        { id: 'schedule', label: 'Schedule', visible: access.hasScoped('SCHEDULE_VIEW') },
        { id: 'notifications', label: 'Notifications', visible: true },
      ],
    },
    {
      label: 'Teaching & learning',
      items: [
        { id: 'academics', label: 'Academics', visible: access.has('ACADEMICS_VIEW') || access.has('CURRICULUM_VIEW') },
        { id: 'enrollment', label: 'Enrollment', visible: access.has('ENROLLMENTS_VIEW') || access.has('TEACHING_ASSIGNMENTS_VIEW') },
        { id: 'content', label: 'Learning Content', visible: access.has('CONTENT_VIEW') },
        { id: 'assignments', label: 'Assignments', visible: access.has('ASSIGNMENTS_VIEW') || access.has('ASSIGNMENTS_TAKE') || access.has('GRADEBOOK_VIEW') },
        { id: 'assessment', label: 'Assessments', visible: access.has('ASSESSMENTS_VIEW') || access.has('ASSESSMENTS_TAKE') },
        { id: 'live', label: 'Live Learning', visible: access.has('LIVE_CLASS_VIEW') || access.has('LIVE_CLASS_MANAGE') },
        { id: 'recordings', label: 'Recordings', visible: access.has('RECORDINGS_VIEW') || access.has('RECORDINGS_MANAGE') },
        { id: 'advanced', label: 'Advanced Academics', visible: access.has('LEARNING_OUTCOMES_MANAGE') || access.has('CREDENTIALS_MANAGE') || access.has('MENTORING_MANAGE') || access.has('SURVEYS_MANAGE') || access.has('LEARNING_OUTCOMES_VIEW') || access.has('CREDENTIALS_VIEW') || access.has('MENTORING_VIEW') || access.has('SURVEYS_VIEW') || access.has('FEEDBACK_MANAGE') },
      ],
    },
    {
      label: 'Institution management',
      items: [
        { id: 'announcements', label: 'Announcements', visible: access.hasScoped('ANNOUNCEMENTS_VIEW') },
        { id: 'finance', label: 'Finance', visible: access.has('FINANCE_VIEW') || access.has('FINANCE_MANAGE') },
        { id: 'people', label: 'People', visible: access.has('USERS_VIEW') },
        { id: 'roles', label: 'Roles', visible: access.has('ROLES_VIEW') },
        { id: 'organization', label: 'Organization', visible: access.has('ORGANIZATION_VIEW') || access.has('BRANCHES_VIEW') },
        { id: 'security', label: 'Security', visible: access.has('SESSIONS_VIEW') || access.has('SESSIONS_MANAGE') },
      ],
    },
    {
      label: 'Platform & insights',
      items: [
        { id: 'analytics', label: 'Analytics & Operations', visible: access.has('ANALYTICS_VIEW') },
        { id: 'integrations', label: 'Integrations', visible: access.has('API_VIEW') || access.has('WEBHOOKS_VIEW') || access.has('FEDERATION_VIEW') || access.has('INTEROPERABILITY_VIEW') },
      ],
    },
  ]
}

/** Applies the text filter after permission checks and removes groups with no remaining destinations. */
export function filterWorkspaceNavigation(
  groups: readonly WorkspaceNavigationGroup[],
  query: string,
): WorkspaceNavigationGroup[] {
  const normalizedQuery = query.trim().toLowerCase()
  return groups
    .map(group => ({
      ...group,
      items: group.items.filter(item =>
        item.visible && (!normalizedQuery || item.label.toLowerCase().includes(normalizedQuery)),
      ),
    }))
    .filter(group => group.items.length > 0)
}
