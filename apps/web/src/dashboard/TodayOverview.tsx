import { useQuery } from '@tanstack/react-query'
import { fetchToday } from '../platform/api'

export function TodayOverview() {
  const today = useQuery({ queryKey: ['today'], queryFn: fetchToday, refetchInterval: 60_000 })
  if (today.isPending) return <section className="panel"><p className="muted">Loading today…</p></section>
  if (today.isError) return <section className="panel"><p className="error-text" role="alert">{today.error.message}</p></section>
  const value = today.data
  return (
    <div className="content-stack">
      <section className="metric-grid" aria-label="Today summary">
        <Metric label="Classes today" value={String(value.summary.classes)} />
        <Metric label="Exams today" value={String(value.summary.exams)} />
        {value.mode === 'ADMINISTRATIVE' && <Metric label="Scheduled learners" value={String(value.summary.scheduledLearners)} />}
        <Metric label="Unread notifications" value={String(value.summary.unreadNotifications)} />
      </section>
      <section className="panel">
        <div className="panel-heading"><div><p className="eyebrow">Today · {value.timezone}</p><h3>{value.mode === 'ADMINISTRATIVE' ? 'Institution operations' : 'Your schedule'}</h3></div><span className="count-badge">{value.schedule.length}</span></div>
        {value.schedule.length === 0 ? <p className="muted">Nothing is scheduled for today.</p> : (
          <div className="timeline-list">
            {value.schedule.map(item => (
              <article className="timeline-item" key={item.occurrenceId}>
                <time>{new Date(item.startsAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</time>
                <div><div className="mini-card-title"><strong>{item.title}</strong><span>{item.kind}</span></div><p>{new Date(item.endsAt).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })} · {humanize(item.deliveryMode)}{item.roomCode ? ` · ${item.roomCode}` : ''}</p></div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string }) { return <div className="metric"><span>{label}</span><strong>{value}</strong></div> }
function humanize(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()) }
