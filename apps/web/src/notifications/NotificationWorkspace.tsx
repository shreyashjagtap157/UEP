import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchNotificationPreferences, fetchNotifications, markNotificationRead, updateNotificationPreference } from '../platform/api'
import type { NotificationPreferenceView } from '../platform/api'

export function NotificationWorkspace() {
  const client = useQueryClient()
  const notifications = useQuery({ queryKey: ['notifications'], queryFn: fetchNotifications })
  const preferences = useQuery({ queryKey: ['notification-preferences'], queryFn: fetchNotificationPreferences })
  const markRead = useMutation({ mutationFn: markNotificationRead, onSuccess: () => { void client.invalidateQueries({ queryKey: ['notifications'] }); void client.invalidateQueries({ queryKey: ['today'] }) } })
  const update = useMutation({ mutationFn: ({ item, inAppEnabled, emailEnabled }: { item: NotificationPreferenceView; inAppEnabled: boolean; emailEnabled: boolean }) => updateNotificationPreference(item.eventType, { inAppEnabled, emailEnabled, expectedVersion: item.version }), onSuccess: () => void client.invalidateQueries({ queryKey: ['notification-preferences'] }) })

  return <div className="content-stack">
    <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Inbox</p><h3>Notifications</h3></div><span className="count-badge">{notifications.data?.totalElements ?? 0}</span></div>
      {notifications.isPending ? <p className="muted">Loading notifications…</p> : notifications.isError ? <p className="error-text" role="alert">{notifications.error.message}</p> : notifications.data.items.length === 0 ? <p className="muted">No notifications yet.</p> : <div className="notification-list">{notifications.data.items.map(item => <article className={item.readAt ? 'notification-card read' : 'notification-card'} key={item.id}><div className="mini-card-title"><strong>{item.title}</strong><span>{humanize(item.eventType)}</span></div><p>{item.body}</p><div className="announcement-meta"><span>{new Date(item.createdAt).toLocaleString()}</span><span>{item.priority}</span></div>{!item.readAt && <button className="text-button" disabled={markRead.isPending} onClick={() => markRead.mutate(item.id)}>Mark read</button>}</article>)}</div>}
    </section>
    <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Delivery preferences</p><h3>Choose your channels</h3></div></div>
      {preferences.isPending ? <p className="muted">Loading preferences…</p> : preferences.isError ? <p className="error-text" role="alert">{preferences.error.message}</p> : <div className="preference-list">{preferences.data.map(item => <div className="preference-row" key={item.eventType}><strong>{humanize(item.eventType)}</strong><label className="check-row"><input type="checkbox" checked={item.inAppEnabled} disabled={update.isPending} onChange={event => update.mutate({ item, inAppEnabled: event.target.checked, emailEnabled: item.emailEnabled })} />In-app</label><label className="check-row"><input type="checkbox" checked={item.emailEnabled} disabled={update.isPending} onChange={event => update.mutate({ item, inAppEnabled: item.inAppEnabled, emailEnabled: event.target.checked })} />Email</label></div>)}</div>}
    </section>
  </div>
}
function humanize(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()) }
