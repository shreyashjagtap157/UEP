import assert from 'node:assert/strict'
import test from 'node:test'
import { buildWorkspaceNavigation, filterWorkspaceNavigation } from '../src/app/workspace-navigation.ts'

const unrestrictedAccess = {
  has: () => true,
  hasScoped: () => true,
}

test('builds the complete Real workspace navigation in four stable work areas', () => {
  const groups = buildWorkspaceNavigation(unrestrictedAccess)

  assert.deepEqual(groups.map(group => group.label), [
    'My workspace',
    'Teaching & learning',
    'Institution management',
    'Platform & insights',
  ])
  assert.deepEqual(groups.flatMap(group => group.items.map(item => item.id)), [
    'overview', 'schedule', 'notifications',
    'academics', 'enrollment', 'content', 'assignments', 'assessment', 'live', 'recordings', 'advanced',
    'announcements', 'finance', 'people', 'roles', 'organization', 'security',
    'analytics', 'integrations',
  ])
  assert.ok(groups.every(group => group.items.every(item => item.visible)))
})

test('shows only destinations granted by the current identity', () => {
  const permissions = new Set(['CONTENT_VIEW', 'ASSIGNMENTS_TAKE', 'ANNOUNCEMENTS_VIEW', 'ANALYTICS_VIEW'])
  const groups = buildWorkspaceNavigation({
    has: permission => permissions.has(permission),
    hasScoped: permission => permissions.has(permission),
  })

  assert.deepEqual(groups.map(group => ({
    label: group.label,
    ids: group.items.filter(item => item.visible).map(item => item.id),
  })), [
    { label: 'My workspace', ids: ['overview', 'notifications'] },
    { label: 'Teaching & learning', ids: ['content', 'assignments'] },
    { label: 'Institution management', ids: ['announcements'] },
    { label: 'Platform & insights', ids: ['analytics'] },
  ])
})

test('recognizes branch-scoped schedule and announcement access', () => {
  const scopedPermissions = new Set(['SCHEDULE_VIEW', 'ANNOUNCEMENTS_VIEW'])
  const groups = buildWorkspaceNavigation({
    has: () => false,
    hasScoped: permission => scopedPermissions.has(permission),
  })

  assert.deepEqual(groups.map(group => ({
    label: group.label,
    ids: group.items.filter(item => item.visible).map(item => item.id),
  })), [
    { label: 'My workspace', ids: ['overview', 'schedule', 'notifications'] },
    { label: 'Institution management', ids: ['announcements'] },
  ])
})

test('filters destinations case-insensitively and removes empty groups', () => {
  const groups = buildWorkspaceNavigation(unrestrictedAccess)
  const filtered = filterWorkspaceNavigation(groups, '  LEARN  ')

  assert.deepEqual(filtered.map(group => ({
    label: group.label,
    labels: group.items.map(item => item.label),
  })), [
    { label: 'Teaching & learning', labels: ['Learning Content', 'Live Learning'] },
  ])
})

test('returns no groups when no destination matches the search', () => {
  const groups = buildWorkspaceNavigation(unrestrictedAccess)
  assert.deepEqual(filterWorkspaceNavigation(groups, 'does-not-exist'), [])
})

test('filters unauthorized destinations even when the search is empty and does not mutate inputs', () => {
  const groups = [{
    label: 'Example',
    items: [
      { id: 'overview', label: 'Today', visible: true },
      { id: 'schedule', label: 'Schedule', visible: false },
    ],
  }]
  const original = structuredClone(groups)

  assert.deepEqual(filterWorkspaceNavigation(groups, ''), [{
    label: 'Example',
    items: [{ id: 'overview', label: 'Today', visible: true }],
  }])
  assert.deepEqual(groups, original)
})
