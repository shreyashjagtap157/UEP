import { useQuery } from '@tanstack/react-query'
import { exportAnalytics, getAnalyticsForecast, getAnalyticsOverview } from '../platform/api'
import type { CurrentIdentity } from '../platform/api'

export function AnalyticsWorkspace({ me }: { me: CurrentIdentity }) {
  const overview = useQuery({ queryKey: ['analytics-overview'], queryFn: () => getAnalyticsOverview() })
  const forecast = useQuery({ queryKey: ['analytics-forecast'], queryFn: getAnalyticsForecast })
  if (overview.isPending) return <section className="panel"><p>Loading analytics…</p></section>
  if (overview.isError) return <section className="panel"><p className="error-text">{overview.error.message}</p></section>
  const o = overview.data
  const canExport = me.permissions.includes('REPORTS_EXPORT')
  const bytes = (n: number) => n > 1024 ** 3 ? `${(n / 1024 ** 3).toFixed(2)} GB` : n > 1024 ** 2 ? `${(n / 1024 ** 2).toFixed(1)} MB` : `${n} B`
  const csv = async (report: string) => { const text = await exportAnalytics(report); const a=document.createElement('a'); a.href=URL.createObjectURL(new Blob([text],{type:'text/csv'})); a.download=`uep-${report}-report.csv`; a.click(); URL.revokeObjectURL(a.href) }
  return <div className="content-stack">
    <section className="hero-card"><p className="eyebrow">Operational intelligence</p><h3>Tenant analytics</h3><p>Derived from authoritative attendance, assessment, recording, finance, and licensing records. The default window is the last 30 days.</p></section>
    <section className="metric-grid">
      <Metric label="Active memberships" value={String(o.activeMemberships)} /><Metric label="Enrollments in window" value={String(o.enrollments)} /><Metric label="Attendance rate" value={`${o.attendance.presentRatePercent.toFixed(1)}%`} /><Metric label="Assessment average" value={`${Number(o.assessment.averagePercent).toFixed(1)}%`} />
      <Metric label="Recording storage" value={bytes(o.recording.totalBytes)} /><Metric label="Recording hours" value={(o.recording.totalSeconds / 3600).toFixed(1)} /><Metric label="Outstanding finance" value={`${Number(o.finance.outstanding).toFixed(2)} ${o.finance.invoiced ? '' : ''}`} /><Metric label="Payments" value={String(o.finance.paymentCount)} />
    </section>
    <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Attendance</p><h3>Participation distribution</h3></div></div><div className="chip-row"><span className="chip">Present {o.attendance.present}</span><span className="chip">Partial {o.attendance.partial}</span><span className="chip">Absent {o.attendance.absent}</span><span className="chip">Excused {o.attendance.excused}</span></div></section>
    <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Assessment</p><h3>Assessment operations</h3></div></div><div className="chip-row"><span className="chip">Attempts {o.assessment.attempts}</span><span className="chip">Submitted {o.assessment.submitted}</span><span className="chip">Published grades {o.assessment.published}</span><span className="chip">Passed {o.assessment.passed}</span></div></section>
    <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Capacity planning</p><h3>Recording forecast</h3></div></div>{forecast.isPending ? <p>Calculating forecast…</p> : forecast.isError ? <p className="error-text">{forecast.error.message}</p> : <p>Recent 30-day storage growth is {bytes(forecast.data.recentBytes)}. At the observed rate, projected additional storage is {bytes(forecast.data.projected30DayBytes)} over 30 days and {bytes(forecast.data.projected365DayBytes)} over 365 days.</p>}</section>
    {canExport && <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Administrative exports</p><h3>Download reports</h3></div></div><div className="form-actions"><button className="secondary-button" onClick={() => void csv('attendance')}>Attendance CSV</button><button className="secondary-button" onClick={() => void csv('assessments')}>Assessment CSV</button><button className="secondary-button" onClick={() => void csv('recordings')}>Recording CSV</button><button className="secondary-button" onClick={() => void csv('finance')}>Finance CSV</button></div></section>}
  </div>
}

function Metric({label,value}:{label:string,value:string}) { return <div className="metric-card"><span>{label}</span><strong>{value}</strong></div> }
