import { useState } from 'react'
import type { FormEvent } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  cancelScheduleOccurrence,
  checkScheduleConflicts,
  createScheduleSeries,
  fetchBatches,
  fetchBranches,
  fetchCourses,
  fetchMembers,
  fetchScheduleOccurrences,
  fetchScheduleSeries,
  fetchSubjects,
  rescheduleOccurrence,
} from '../platform/api'
import type {
  CurrentIdentity,
  DeliveryMode,
  PermissionKey,
  ScheduleConflict,
  ScheduleCreateInput,
  ScheduleOccurrenceView,
  RecurrenceFrequency,
} from '../platform/api'

export function ScheduleWorkspace({ me }: { me: CurrentIdentity }) {
  const client = useQueryClient()
  const canManage = hasScoped(me, 'SCHEDULE_MANAGE')
  const [range, setRange] = useState(() => defaultRange())
  const occurrences = useQuery({
    queryKey: ['schedule-occurrences', range.from, range.to],
    queryFn: () => fetchScheduleOccurrences(range.from, range.to),
  })
  const series = useQuery({ queryKey: ['schedule-series'], queryFn: fetchScheduleSeries, enabled: canManage })
  const branches = useQuery({ queryKey: ['branches'], queryFn: fetchBranches, enabled: canManage })
  const batches = useQuery({ queryKey: ['batches'], queryFn: fetchBatches, enabled: canManage })
  const courses = useQuery({ queryKey: ['courses'], queryFn: fetchCourses, enabled: canManage })
  const subjects = useQuery({ queryKey: ['subjects'], queryFn: fetchSubjects, enabled: canManage })
  const members = useQuery({ queryKey: ['members'], queryFn: fetchMembers, enabled: canManage && has(me, 'USERS_VIEW') })
  const [conflicts, setConflicts] = useState<ScheduleConflict[]>([])

  const refresh = () => Promise.all([
    client.invalidateQueries({ queryKey: ['schedule-occurrences'] }),
    client.invalidateQueries({ queryKey: ['schedule-series'] }),
    client.invalidateQueries({ queryKey: ['today'] }),
  ])
  const create = useMutation({ mutationFn: createScheduleSeries, onSuccess: () => { setConflicts([]); void refresh() } })
  const preflight = useMutation({ mutationFn: checkScheduleConflicts, onSuccess: setConflicts })
  const reschedule = useMutation({ mutationFn: ({ id, input }: { id: string; input: Parameters<typeof rescheduleOccurrence>[1] }) => rescheduleOccurrence(id, input), onSuccess: () => void refresh() })
  const cancel = useMutation({ mutationFn: ({ id, reason, expectedVersion }: { id: string; reason: string; expectedVersion: number }) => cancelScheduleOccurrence(id, { reason, expectedVersion }), onSuccess: () => void refresh() })

  function buildInput(form: HTMLFormElement): ScheduleCreateInput {
    const data = new FormData(form)
    const recurrenceFrequency = String(data.get('recurrenceFrequency') ?? 'NONE') as RecurrenceFrequency
    const recurrenceDays = ['MONDAY', 'TUESDAY', 'WEDNESDAY', 'THURSDAY', 'FRIDAY', 'SATURDAY', 'SUNDAY']
      .filter(day => data.get(`day-${day}`) === 'on')
    const value = (name: string) => String(data.get(name) ?? '').trim() || undefined
    const numeric = (name: string) => value(name) ? Number(value(name)) : undefined
    const input: ScheduleCreateInput = {
      kind: String(data.get('kind') ?? 'CLASS') as ScheduleCreateInput['kind'],
      title: String(data.get('title') ?? ''),
      deliveryMode: String(data.get('deliveryMode') ?? 'OFFLINE') as DeliveryMode,
      startLocal: String(data.get('startLocal') ?? ''),
      durationMinutes: Number(data.get('durationMinutes') ?? 60),
      ...(value('description') ? { description: value('description')! } : {}),
      ...(value('timezone') ? { timezone: value('timezone')! } : {}),
      ...(value('branchId') ? { branchId: value('branchId')! } : {}),
      ...(value('batchId') ? { batchId: value('batchId')! } : {}),
      ...(value('courseId') ? { courseId: value('courseId')! } : {}),
      ...(value('subjectId') ? { subjectId: value('subjectId')! } : {}),
      ...(value('primaryTeacherMembershipId') ? { primaryTeacherMembershipId: value('primaryTeacherMembershipId')! } : {}),
      ...(value('roomCode') ? { roomCode: value('roomCode')! } : {}),
      ...(recurrenceFrequency !== 'NONE' ? { recurrenceFrequency, recurrenceInterval: numeric('recurrenceInterval') ?? 1 } : {}),
      ...(recurrenceFrequency === 'WEEKLY' ? { recurrenceDays } : {}),
      ...(recurrenceFrequency === 'MONTHLY' && numeric('recurrenceDayOfMonth') ? { recurrenceDayOfMonth: numeric('recurrenceDayOfMonth')! } : {}),
      ...(recurrenceFrequency !== 'NONE' && value('recurrenceUntilLocal') ? { recurrenceUntilLocal: value('recurrenceUntilLocal')! } : {}),
      ...(recurrenceFrequency !== 'NONE' && !value('recurrenceUntilLocal') && numeric('recurrenceCount') ? { recurrenceCount: numeric('recurrenceCount')! } : {}),
      ...(data.get('allowConflicts') === 'on' ? { allowConflicts: true } : {}),
      ...(value('conflictOverrideReason') ? { conflictOverrideReason: value('conflictOverrideReason')! } : {}),
    }
    return input
  }

  function submitCreate(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    create.mutate(buildInput(form), { onSuccess: () => form.reset() })
  }



  return (
    <div className="content-stack">
      <section className="panel">
        <div className="panel-heading"><div><p className="eyebrow">Calendar</p><h3>Schedule window</h3></div><span className="count-badge">{occurrences.data?.length ?? 0}</span></div>
        <RangePicker range={range} onChange={setRange} />
        {occurrences.isPending ? <p className="muted">Loading schedule…</p> : occurrences.isError ? <p className="error-text" role="alert">{occurrences.error.message}</p> : <OccurrenceList items={occurrences.data} />}
      </section>

      {canManage && (
        <section className="panel">
          <div className="panel-heading"><div><p className="eyebrow">Scheduling</p><h3>Create class or event series</h3></div><span className="count-badge">{series.data?.totalElements ?? 0} series</span></div>
          <form className="form-grid" onSubmit={submitCreate} onChange={() => setConflicts([])}>
            <label>Kind<select name="kind" defaultValue="CLASS"><option>CLASS</option><option>EXAM</option><option>ASSIGNMENT</option><option>MEETING</option><option>EVENT</option><option>HOLIDAY</option><option>APPOINTMENT</option></select></label>
            <label>Title<input name="title" required maxLength={240} /></label>
            <label className="wide">Description<textarea name="description" maxLength={4000} rows={2} /></label>
            <label>Local start<input name="startLocal" type="datetime-local" required /></label>
            <label>Duration minutes<input name="durationMinutes" type="number" min={1} max={10080} defaultValue={60} required /></label>
            <label>Delivery<select name="deliveryMode" defaultValue="OFFLINE"><option>OFFLINE</option><option>ONLINE</option><option>HYBRID</option><option>NOT_APPLICABLE</option></select></label>
            <label>Timezone<input name="timezone" maxLength={80} placeholder="Use branch/organization default" /></label>
            <label>Branch<select name="branchId"><option value="">No branch</option>{branches.data?.items.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label>
            <label>Batch<select name="batchId"><option value="">No batch</option>{batches.data?.items.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label>
            <label>Course<select name="courseId"><option value="">No course</option>{courses.data?.items.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label>
            <label>Subject<select name="subjectId"><option value="">No subject</option>{subjects.data?.items.map(item => <option key={item.id} value={item.id}>{item.displayName}</option>)}</select></label>
            <label>Primary teacher<select name="primaryTeacherMembershipId"><option value="">No assigned teacher</option>{members.data?.items.map(item => <option key={item.membershipId} value={item.membershipId}>{item.displayName}</option>)}</select></label>
            <label>Room/resource code<input name="roomCode" maxLength={120} placeholder="A-204" /></label>
            <label>Recurrence<select name="recurrenceFrequency" defaultValue="NONE"><option>NONE</option><option>DAILY</option><option>WEEKLY</option><option>MONTHLY</option></select></label>
            <label>Recurrence interval<input name="recurrenceInterval" type="number" min={1} max={365} defaultValue={1} /></label>
            <fieldset className="wide permission-grid"><legend>Weekly days</legend>{['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY'].map(day => <label className="check-row" key={day}><input type="checkbox" name={`day-${day}`} />{title(day)}</label>)}</fieldset>
            <label>Monthly day<input name="recurrenceDayOfMonth" type="number" min={1} max={31} /></label>
            <label>Repeat count<input name="recurrenceCount" type="number" min={1} max={1000} placeholder="Or use recurrence-until" /></label>
            <label>Recurrence until<input name="recurrenceUntilLocal" type="datetime-local" /></label>
            <label className="check-row"><input type="checkbox" name="allowConflicts" />Allow detected conflicts</label>
            <label>Conflict override reason<input name="conflictOverrideReason" maxLength={500} /></label>
            <div className="form-actions button-row"><button className="secondary-button" type="button" disabled={preflight.isPending} onClick={event => { const form = event.currentTarget.form; if (form) preflight.mutate(buildInput(form)) }}>Check conflicts</button><button className="primary-button" disabled={create.isPending}>Create schedule</button></div>
            {preflight.isError && <p className="error-text wide" role="alert">{preflight.error.message}</p>}
            {create.isError && <p className="error-text wide" role="alert">{create.error.message}</p>}
          </form>
          {conflicts.length > 0 && <ConflictList conflicts={conflicts} />}
          {preflight.isSuccess && conflicts.length === 0 && <p className="success-text" role="status">No teacher, batch, room, exam, or holiday conflicts detected.</p>}
        </section>
      )}

      {canManage && occurrences.data && occurrences.data.length > 0 && (
        <OccurrenceManagement items={occurrences.data} members={members.data?.items ?? []} reschedule={reschedule} cancel={cancel} />
      )}
    </div>
  )
}

function RangePicker({ range, onChange }: { range: { from: string; to: string }; onChange: (range: { from: string; to: string }) => void }) {
  const fromDate = range.from.slice(0, 10)
  const toDate = new Date(new Date(range.to).getTime() - 1).toISOString().slice(0, 10)
  return <div className="range-row"><label>From<input type="date" value={fromDate} onChange={event => onChange(toRange(event.target.value, toDate))} /></label><label>Through<input type="date" value={toDate} onChange={event => onChange(toRange(fromDate, event.target.value))} /></label></div>
}

function OccurrenceList({ items }: { items: ScheduleOccurrenceView[] }) {
  if (!items.length) return <p className="muted">Nothing is scheduled in this window.</p>
  return <div className="timeline-list">{items.map(item => <article className="timeline-item" key={item.id}><time>{new Date(item.startsAt).toLocaleString()}</time><div><div className="mini-card-title"><strong>{item.title}</strong><span>{item.kind}</span></div><p>{new Date(item.endsAt).toLocaleTimeString()} · {title(item.deliveryMode)}{item.roomCode ? ` · ${item.roomCode}` : ''}</p>{item.status !== 'SCHEDULED' && <span className="danger-chip">{item.status}</span>}</div></article>)}</div>
}

function ConflictList({ conflicts }: { conflicts: ScheduleConflict[] }) {
  return <div className="conflict-box" role="alert"><strong>{conflicts.length} scheduling conflict{conflicts.length === 1 ? '' : 's'} detected</strong><ul>{conflicts.map(conflict => <li key={`${conflict.type}-${conflict.existingOccurrenceId}`}><b>{conflict.type}</b> — {conflict.existingTitle}: {conflict.message}</li>)}</ul></div>
}

function OccurrenceManagement({ items, members, reschedule, cancel }: {
  items: ScheduleOccurrenceView[]
  members: Array<{ membershipId: string; displayName: string }>
  reschedule: { mutate: (value: { id: string; input: Parameters<typeof rescheduleOccurrence>[1] }) => void; isPending: boolean; isError: boolean; error: Error | null }
  cancel: { mutate: (value: { id: string; reason: string; expectedVersion: number }) => void; isPending: boolean; isError: boolean; error: Error | null }
}) {
  const [selectedId, setSelectedId] = useState(items[0]?.id ?? '')
  const selected = items.find(item => item.id === selectedId) ?? items[0]
  if (!selected) return null
  const active = selected
  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const data = new FormData(event.currentTarget)
    const startLocal = String(data.get('startLocal') ?? '')
    const reason = String(data.get('reason') ?? '')
    const substituteTeacherMembershipId = String(data.get('substituteTeacherMembershipId') ?? '') || undefined
    const roomCode = String(data.get('roomCode') ?? '') || undefined
    const input: Parameters<typeof rescheduleOccurrence>[1] = { startLocal, reason, expectedVersion: active.version,
      ...(substituteTeacherMembershipId ? { substituteTeacherMembershipId } : {}),
      ...(roomCode ? { roomCode } : {}) }
    reschedule.mutate({ id: active.id, input })
  }
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Exceptions</p><h3>Reschedule, substitute, or cancel</h3></div></div><form className="form-grid" onSubmit={submit} key={`${active.id}-${active.version}`}><label className="wide">Occurrence<select value={active.id} onChange={event => setSelectedId(event.target.value)}>{items.map(item => <option key={item.id} value={item.id}>{item.title} — {new Date(item.startsAt).toLocaleString()}</option>)}</select></label><label>New local start<input name="startLocal" type="datetime-local" required /></label><label>Substitute teacher<select name="substituteTeacherMembershipId"><option value="">Keep primary teacher</option>{members.map(item => <option key={item.membershipId} value={item.membershipId}>{item.displayName}</option>)}</select></label><label>Room override<input name="roomCode" maxLength={120} /></label><label>Reason<input name="reason" maxLength={500} required /></label><div className="form-actions button-row"><button className="secondary-button danger-action" type="button" disabled={cancel.isPending} onClick={() => { const reason = window.prompt('Cancellation reason'); if (reason) cancel.mutate({ id: active.id, reason, expectedVersion: active.version }) }}>Cancel occurrence</button><button className="primary-button" disabled={reschedule.isPending}>Apply exception</button></div>{reschedule.isError && <p className="error-text wide" role="alert">{reschedule.error?.message}</p>}{cancel.isError && <p className="error-text wide" role="alert">{cancel.error?.message}</p>}</form></section>
}

function defaultRange() {
  const now = new Date()
  const from = new Date(now); from.setHours(0, 0, 0, 0)
  const to = new Date(from); to.setDate(to.getDate() + 31)
  return { from: from.toISOString(), to: to.toISOString() }
}
function toRange(from: string, through: string) { const start = new Date(`${from}T00:00:00`); const end = new Date(`${through}T00:00:00`); end.setDate(end.getDate() + 1); return { from: start.toISOString(), to: end.toISOString() } }
function has(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) }
function hasScoped(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) || me.primaryBranchPermissions.includes(permission) }
function title(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()) }
