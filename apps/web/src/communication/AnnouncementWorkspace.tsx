import { useState } from 'react'
import type { FormEvent, ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  acknowledgeAnnouncement,
  cancelAnnouncement,
  createAnnouncement,
  fetchAnnouncements,
  fetchBatches,
  fetchBranches,
  fetchCourses,
  fetchMembers,
  fetchMyAnnouncements,
  fetchRoles,
  fetchSubjects,
  publishAnnouncement,
} from '../platform/api'
import type { AnnouncementTarget, AnnouncementTargetKind, AnnouncementView, CurrentIdentity, PermissionKey } from '../platform/api'

export function AnnouncementWorkspace({ me }: { me: CurrentIdentity }) {
  const client = useQueryClient()
  const canManage = hasScoped(me, 'ANNOUNCEMENTS_MANAGE')
  const mine = useQuery({ queryKey: ['announcements-me'], queryFn: fetchMyAnnouncements })
  const admin = useQuery({ queryKey: ['announcements-admin'], queryFn: fetchAnnouncements, enabled: canManage })
  const branches = useQuery({ queryKey: ['branches'], queryFn: fetchBranches, enabled: canManage })
  const batches = useQuery({ queryKey: ['batches'], queryFn: fetchBatches, enabled: canManage })
  const courses = useQuery({ queryKey: ['courses'], queryFn: fetchCourses, enabled: canManage })
  const subjects = useQuery({ queryKey: ['subjects'], queryFn: fetchSubjects, enabled: canManage })
  const roles = useQuery({ queryKey: ['roles'], queryFn: fetchRoles, enabled: canManage && has(me, 'ROLES_VIEW') })
  const members = useQuery({ queryKey: ['members'], queryFn: fetchMembers, enabled: canManage && has(me, 'USERS_VIEW') })
  const [targetKind, setTargetKind] = useState<AnnouncementTargetKind>('ORGANIZATION')
  const refresh = () => Promise.all([client.invalidateQueries({ queryKey: ['announcements-me'] }), client.invalidateQueries({ queryKey: ['announcements-admin'] }), client.invalidateQueries({ queryKey: ['notifications'] }), client.invalidateQueries({ queryKey: ['today'] })])
  const create = useMutation({ mutationFn: createAnnouncement, onSuccess: () => void refresh() })
  const publish = useMutation({ mutationFn: ({ id, version }: { id: string; version: number }) => publishAnnouncement(id, version), onSuccess: () => void refresh() })
  const cancel = useMutation({ mutationFn: ({ id, version, reason }: { id: string; version: number; reason: string }) => cancelAnnouncement(id, reason, version), onSuccess: () => void refresh() })
  const acknowledge = useMutation({ mutationFn: acknowledgeAnnouncement, onSuccess: () => void refresh() })

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    const publishMode = String(data.get('publication') ?? 'DRAFT')
    const targets = targetsFromForm(form, targetKind)
    const publishAt = dateTimeToIso(String(data.get('publishAt') ?? ''))
    const expiresAt = dateTimeToIso(String(data.get('expiresAt') ?? ''))
    create.mutate({
      title: String(data.get('title') ?? ''), body: String(data.get('body') ?? ''),
      priority: String(data.get('priority') ?? 'NORMAL') as 'NORMAL' | 'IMPORTANT' | 'URGENT' | 'EMERGENCY',
      acknowledgementRequired: data.get('acknowledgementRequired') === 'on',
      publishNow: publishMode === 'NOW', publishAt: publishMode === 'SCHEDULED' ? publishAt : undefined,
      expiresAt, targets,
    }, { onSuccess: () => form.reset() })
  }

  const targetOptions = optionsFor(targetKind, { branches: branches.data?.items ?? [], batches: batches.data?.items ?? [], courses: courses.data?.items ?? [], subjects: subjects.data?.items ?? [], roles: roles.data?.items ?? [], members: members.data?.items ?? [] })

  return <div className="content-stack">
    {canManage && <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">Communication</p><h3>Create announcement</h3></div><span className="count-badge">{admin.data?.totalElements ?? 0}</span></div>
      <form className="form-grid" onSubmit={submit}>
        <label>Title<input name="title" required maxLength={240} /></label>
        <label>Priority<select name="priority" defaultValue="NORMAL"><option>NORMAL</option><option>IMPORTANT</option><option>URGENT</option><option>EMERGENCY</option></select></label>
        <label className="wide">Message<textarea name="body" required maxLength={20000} rows={5} /></label>
        <label>Target<select value={targetKind} onChange={event => setTargetKind(event.target.value as AnnouncementTargetKind)}><option>ORGANIZATION</option><option>BRANCH</option><option>BATCH</option><option>COURSE</option><option>SUBJECT</option><option>ROLE</option><option>MEMBERSHIP</option></select></label>
        {targetKind !== 'ORGANIZATION' && <label>Recipients<select name="targetIds" multiple required size={Math.min(7, Math.max(3, targetOptions.length))}>{targetOptions.map(item => <option key={item.id} value={item.id}>{item.label}</option>)}</select></label>}
        <label>Publication<select name="publication" defaultValue="DRAFT"><option value="DRAFT">Save draft</option><option value="NOW">Publish now</option><option value="SCHEDULED">Schedule</option></select></label>
        <label>Publish at<input name="publishAt" type="datetime-local" /></label>
        <label>Expires at<input name="expiresAt" type="datetime-local" /></label>
        <label className="check-row"><input type="checkbox" name="acknowledgementRequired" />Require acknowledgement</label>
        <div className="form-actions"><button className="primary-button" disabled={create.isPending}>Create announcement</button></div>
        {create.isError && <p className="error-text wide" role="alert">{create.error.message}</p>}
      </form>
    </section>}

    {canManage && <AnnouncementList title="Managed announcements" items={admin.data?.items ?? []} empty="No announcements created yet." actions={item => <div className="button-row">{(item.status === 'DRAFT' || item.status === 'SCHEDULED') && <button className="secondary-button" disabled={publish.isPending} onClick={() => publish.mutate({ id: item.id, version: item.version })}>Publish now</button>}{(item.status === 'DRAFT' || item.status === 'SCHEDULED') && <button className="secondary-button danger-action" disabled={cancel.isPending} onClick={() => { const reason = window.prompt('Cancellation reason'); if (reason) cancel.mutate({ id: item.id, version: item.version, reason }) }}>Cancel</button>}</div>} />}

    <AnnouncementList title="Announcements for you" items={mine.data?.items ?? []} empty={mine.isPending ? 'Loading announcements…' : 'No active announcements.'} actions={item => item.acknowledgementRequired && !item.acknowledgedAt ? <button className="primary-button" disabled={acknowledge.isPending} onClick={() => acknowledge.mutate(item.id)}>Acknowledge</button> : null} />
  </div>
}

function AnnouncementList({ title, items, empty, actions }: { title: string; items: AnnouncementView[]; empty: string; actions: (item: AnnouncementView) => ReactNode }) {
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Announcements</p><h3>{title}</h3></div><span className="count-badge">{items.length}</span></div>{items.length === 0 ? <p className="muted">{empty}</p> : <div className="announcement-list">{items.map(item => <article className={`announcement-card priority-${item.priority.toLowerCase()}`} key={item.id}><div className="mini-card-title"><strong>{item.title}</strong><span>{item.status}</span></div><p>{item.body}</p><div className="announcement-meta"><span>{item.priority}</span>{item.publishedAt && <span>Published {new Date(item.publishedAt).toLocaleString()}</span>}{item.expiresAt && <span>Expires {new Date(item.expiresAt).toLocaleString()}</span>}{item.acknowledgedAt && <span>Acknowledged</span>}</div>{actions(item)}</article>)}</div>}</section>
}

function targetsFromForm(form: HTMLFormElement, kind: AnnouncementTargetKind): AnnouncementTarget[] {
  if (kind === 'ORGANIZATION') return [{ kind }]
  const select = form.elements.namedItem('targetIds') as HTMLSelectElement | null
  return Array.from(select?.selectedOptions ?? []).map(option => ({ kind, id: option.value }))
}
function optionsFor(kind: AnnouncementTargetKind, data: { branches: Array<{ id: string; displayName: string }>; batches: Array<{ id: string; displayName: string }>; courses: Array<{ id: string; displayName: string }>; subjects: Array<{ id: string; displayName: string }>; roles: Array<{ id: string; name: string }>; members: Array<{ membershipId: string; displayName: string }> }) {
  switch (kind) {
    case 'BRANCH': return data.branches.map(item => ({ id: item.id, label: item.displayName }))
    case 'BATCH': return data.batches.map(item => ({ id: item.id, label: item.displayName }))
    case 'COURSE': return data.courses.map(item => ({ id: item.id, label: item.displayName }))
    case 'SUBJECT': return data.subjects.map(item => ({ id: item.id, label: item.displayName }))
    case 'ROLE': return data.roles.map(item => ({ id: item.id, label: item.name }))
    case 'MEMBERSHIP': return data.members.map(item => ({ id: item.membershipId, label: item.displayName }))
    default: return []
  }
}
function dateTimeToIso(value: string) { return value ? new Date(value).toISOString() : undefined }
function has(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) }
function hasScoped(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) || me.primaryBranchPermissions.includes(permission) }
