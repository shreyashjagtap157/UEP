import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CurrentIdentity, RecordingQualityPreset, RecordingView } from '../platform/api'
import { fetchRecordings, issueRecordingPlayback, stopRecording } from '../platform/api'

export function RecordingWorkspace({ me }: { me: CurrentIdentity }) {
  const queryClient = useQueryClient()
  const recordings = useQuery({ queryKey: ['recordings'], queryFn: fetchRecordings, refetchInterval: 5000 })
  const [selected, setSelected] = useState<RecordingView | null>(null)
  const [playback, setPlayback] = useState<{ url: string; expiresAt: string; name: string; id: string } | null>(null)
  const [quality, setQuality] = useState<RecordingQualityPreset>('BALANCED')
  const play = useMutation({ mutationFn: issueRecordingPlayback, onSuccess: value => setPlayback({ url: value.streamUrl, expiresAt: value.expiresAt, name: value.watermarkName, id: value.watermarkId }) })
  const stop = useMutation({ mutationFn: stopRecording, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['recordings'] }) })

  useEffect(() => {
    if (!selected) return
    const current = recordings.data?.items.find(item => item.id === selected.id)
    if (current) setSelected(current)
  }, [recordings.data, selected?.id])

  return <div className="content-stack">
    <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">Media lifecycle</p><h3>Class recordings</h3></div><span className="count-badge">{recordings.data?.totalElements ?? 0}</span></div>
      <p className="muted">Recordings move through durable processing states and storage tiers. The platform stores media outside PostgreSQL and never buffers a complete recording in JVM memory.</p>
      {recordings.isPending ? <p className="muted">Loading recordings…</p> : recordings.isError ? <p className="error-text" role="alert">{recordings.error.message}</p> : <div className="card-grid">{recordings.data.items.map(item => <button className="mini-card interactive-card" key={item.id} onClick={() => { setSelected(item); setPlayback(null) }}><div className="mini-card-title"><strong>{item.qualityPreset.replaceAll('_', ' ')}</strong><span>{item.status}</span></div><p>{item.storageTier} · {item.storageProvider}{item.durationSeconds ? ` · ${Math.round(item.durationSeconds / 60)} min` : ''}</p>{item.failureReason && <p className="error-text">{item.failureReason}</p>}</button>)}</div>}
    </section>

    {selected && <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">Recording detail</p><h3>{selected.status} · {selected.qualityPreset.replaceAll('_', ' ')}</h3></div><button className="text-button" onClick={() => setSelected(null)}>Close</button></div>
      <div className="metric-grid"><Metric label="Storage" value={`${selected.storageTier} / ${selected.storageProvider}`} /><Metric label="Duration" value={selected.durationSeconds ? `${Math.round(selected.durationSeconds / 60)} min` : 'Processing'} /><Metric label="Size" value={selected.sizeBytes ? formatBytes(selected.sizeBytes) : 'Processing'} /><Metric label="Lifecycle" value={selected.status} /></div>
      {me.permissions.includes('RECORDINGS_MANAGE') && selected.status === 'RECORDING' && <div className="button-row"><button className="secondary-button" disabled={stop.isPending} onClick={() => stop.mutate(selected.id)}>Stop recording</button></div>}
      {(selected.status === 'READY' || selected.status === 'ARCHIVED') && <div className="button-row"><button className="primary-button" disabled={play.isPending} onClick={() => play.mutate(selected.id)}>Issue 5-minute playback access</button></div>}
      {play.isError && <p className="error-text" role="alert">{play.error.message}</p>}
      {playback && <WatermarkedPlayer playback={playback} />}
    </section>}
    {me.permissions.includes('RECORDINGS_MANAGE') && <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Quality policy</p><h3>Available recording presets</h3></div></div><label>Default classroom request preset<select value={quality} onChange={event => setQuality(event.target.value as RecordingQualityPreset)}><option value="ECONOMY">Economy · 720p</option><option value="BALANCED">Balanced · 720p</option><option value="HIGH_QUALITY">High Quality · 1080p</option><option value="SOURCE_ARCHIVE">Source Archive · 1080p composite</option></select></label><p className="muted small">This selector controls the request preset used from the Live Learning workspace. Tenant hot-cache/archive destinations are configured server-side.</p></section>}
  </div>
}

function WatermarkedPlayer({ playback }: { playback: { url: string; expiresAt: string; name: string; id: string } }) {
  const [position, setPosition] = useState(0)
  useEffect(() => { const timer = window.setInterval(() => setPosition(value => (value + 1) % 4), 15000); return () => window.clearInterval(timer) }, [])
  const positions = [{ top: '8%', left: '8%' }, { top: '8%', right: '8%' }, { bottom: '12%', left: '8%' }, { bottom: '12%', right: '8%' }] as const
  return <div className="recording-player"><video controls preload="metadata" src={playback.url} /><div className="recording-watermark" style={positions[position]}>Authorized to {playback.name} · {playback.id}</div><p className="muted small">Playback authorization expires {new Date(playback.expiresAt).toLocaleTimeString()}; the visible identity watermark changes position every 15 seconds.</p></div>
}

function Metric({ label, value }: { label: string; value: string }) { return <div className="metric"><span>{label}</span><strong>{value}</strong></div> }
function formatBytes(value: number) { if (value < 1024 * 1024) return `${Math.round(value / 1024)} KB`; if (value < 1024 * 1024 * 1024) return `${Math.round(value / 1024 / 1024)} MB`; return `${(value / 1024 / 1024 / 1024).toFixed(1)} GB` }
