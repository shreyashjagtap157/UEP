import { useEffect, useRef, useState } from 'react'
import type { FormEvent } from 'react'
import {
  Room,
  RoomEvent,
  Track,
  VideoPresets,
  type ChatMessage,
} from 'livekit-client'
import type { CurrentIdentity, LiveClassView } from '../platform/api'
import {
  createLiveClass,
  endLiveClass,
  fetchLiveClasses,
  joinLiveClass,
  leaveLiveClass,
  startLiveClass,
  type LiveClassToken,
  requestRecording,
  stopRecording,
} from '../platform/api'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'

export function LiveLearningWorkspace({ me }: { me: CurrentIdentity }) {
  const queryClient = useQueryClient()
  const classes = useQuery({ queryKey: ['live-classes'], queryFn: fetchLiveClasses })
  const [selected, setSelected] = useState<LiveClassView | null>(null)
  const create = useMutation({ mutationFn: createLiveClass, onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['live-classes'] }) })

  function createFromForm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const form = event.currentTarget
    const data = new FormData(form)
    create.mutate({
      classSessionId: String(data.get('classSessionId') ?? ''),
      attendancePolicy: String(data.get('attendancePolicy') ?? 'JOIN_TIME') as LiveClassView['attendancePolicy'],
      minimumAttendanceSeconds: Number(data.get('minimumAttendanceSeconds') ?? 0),
      attendanceThresholdBasisPoints: Number(data.get('attendanceThresholdBasisPoints') ?? 7500),
      lowBandwidth: data.get('lowBandwidth') === 'on',
      chatEnabled: data.get('chatEnabled') === 'on',
    }, { onSuccess: () => form.reset() })
  }

  return <div className="content-stack">
    {me.permissions.includes('LIVE_CLASS_MANAGE') && <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">Web-first classroom setup</p><h3>Create live classroom</h3></div></div>
      <form className="form-grid" onSubmit={createFromForm}>
        <label>Class session ID<input name="classSessionId" required placeholder="scheduled class-session UUID" /></label>
        <label>Attendance policy<select name="attendancePolicy" defaultValue="JOIN_TIME"><option value="JOIN_TIME">Join time</option><option value="MINIMUM_DURATION">Minimum duration</option><option value="PERCENTAGE">Percentage</option><option value="MANUAL">Manual</option></select></label>
        <label>Minimum seconds<input name="minimumAttendanceSeconds" type="number" min="0" defaultValue="0" /></label>
        <label>Attendance threshold<input name="attendanceThresholdBasisPoints" type="number" min="0" max="10000" defaultValue="7500" /></label>
        <label className="check-row"><input name="lowBandwidth" type="checkbox" />Low-bandwidth profile</label>
        <label className="check-row"><input name="chatEnabled" type="checkbox" defaultChecked />Enable classroom chat</label>
        <div className="form-actions"><button className="primary-button" disabled={create.isPending}>Create classroom</button></div>
        {create.isError && <p className="error-text" role="alert">{create.error.message}</p>}
      </form>
    </section>}
    <section className="panel">
      <div className="panel-heading"><div><p className="eyebrow">Realtime classroom</p><h3>Live Learning</h3></div><span className="count-badge">{classes.data?.totalElements ?? 0}</span></div>
      {classes.isPending ? <p className="muted">Loading…</p> : classes.isError ? <p className="error-text" role="alert">{classes.error.message}</p> : <div className="card-grid">{classes.data.items.map(item => <button className="mini-card interactive-card" key={item.id} onClick={() => setSelected(item)}><div className="mini-card-title"><strong>{item.roomName}</strong><span>{item.status}</span></div><p>{item.attendancePolicy.replaceAll('_', ' ')}{item.lowBandwidth ? ' · low bandwidth' : ' · adaptive media'}</p></button>)}</div>}
    </section>
    {selected && <LiveRoom me={me} liveClass={selected} onClose={() => setSelected(null)} />}
  </div>
}

function LiveRoom({ me, liveClass, onClose }: { me: CurrentIdentity; liveClass: LiveClassView; onClose: () => void }) {
  const queryClient = useQueryClient()
  const start = useMutation({ mutationFn: startLiveClass, onSuccess: value => { queryClient.setQueryData(['live-classes'], (old: any) => old ? { ...old, items: old.items.map((x: LiveClassView) => x.id === value.id ? value : x) } : old) } })
  const end = useMutation({ mutationFn: endLiveClass, onSuccess: value => { queryClient.setQueryData(['live-classes'], (old: any) => old ? { ...old, items: old.items.map((x: LiveClassView) => x.id === value.id ? value : x) } : old) } })
  const admin = me.permissions.includes('LIVE_CLASS_MANAGE')
  const [token, setToken] = useState<LiveClassToken | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [joining, setJoining] = useState(false)
  const [recording, setRecording] = useState<'ECONOMY'|'BALANCED'|'HIGH_QUALITY'|'SOURCE_ARCHIVE'>('BALANCED')
  const record = useMutation({ mutationFn: () => requestRecording(liveClass.id, recording) })
  const stopRecordingMutation = useMutation({ mutationFn: stopRecording })

  useEffect(() => {
    if (liveClass.status !== 'LIVE') return
    let active = true
    setJoining(true)
    void joinLiveClass(liveClass.id, liveClass.lowBandwidth ? 'LOW_BANDWIDTH' : 'BALANCED')
      .then(value => { if (active) { setToken(value); setJoining(false) } })
      .catch(reason => { if (active) { setError(reason instanceof Error ? reason.message : String(reason)); setJoining(false) } })
    return () => { active = false }
  }, [liveClass.id, liveClass.status, liveClass.lowBandwidth])

  return <section className="panel live-room">
    <div className="panel-heading"><div><p className="eyebrow">Live session</p><h3>{liveClass.roomName}</h3></div><button className="text-button" onClick={onClose}>Close</button></div>
    <div className="button-row">
      {admin && liveClass.status === 'SCHEDULED' && <button className="primary-button" disabled={start.isPending} onClick={() => start.mutate(liveClass.id)}>Start class</button>}
      {admin && liveClass.status === 'LIVE' && <button className="secondary-button" disabled={end.isPending} onClick={() => end.mutate(liveClass.id)}>End class</button>}
      {admin && liveClass.status === 'LIVE' && <select aria-label="Recording quality" value={recording} onChange={event => setRecording(event.target.value as typeof recording)}><option value="ECONOMY">Record · Economy</option><option value="BALANCED">Record · Balanced</option><option value="HIGH_QUALITY">Record · High Quality</option><option value="SOURCE_ARCHIVE">Record · Source Archive</option></select>}
      {admin && liveClass.status === 'LIVE' && <button className="secondary-button" disabled={record.isPending} onClick={() => record.mutate()}>Start recording</button>}
      {record.isSuccess && <span className="chip">Recording queued</span>}
      {record.isError && <span className="error-text">{record.error.message}</span>}
    </div>
    {error && <p className="error-text" role="alert">{error}</p>}
    {liveClass.status === 'LIVE' && joining && <p className="muted">Authorizing classroom access…</p>}
    {liveClass.status === 'LIVE' && token && <ConnectedRoom token={token} liveClass={liveClass} />}
    {liveClass.status !== 'LIVE' && <p className="muted">The classroom becomes joinable when the scheduled session is started.</p>}
  </section>
}

function ConnectedRoom({ token, liveClass }: { token: LiveClassToken; liveClass: LiveClassView }) {
  const grid = useRef<HTMLDivElement>(null)
  const [room, setRoom] = useState<Room | null>(null)
  const [mic, setMic] = useState(true)
  const [camera, setCamera] = useState(true)
  const [screen, setScreen] = useState(false)
  const [participants, setParticipants] = useState(1)
  const [message, setMessage] = useState('')
  const [messages, setMessages] = useState<string[]>([])

  useEffect(() => {
    const livekit = new Room({
      adaptiveStream: true,
      dynacast: true,
      disconnectOnPageLeave: true,
      videoCaptureDefaults: { resolution: liveClass.lowBandwidth ? VideoPresets.h360.resolution : VideoPresets.h720.resolution },
    })
    setRoom(livekit)
    const attach = (track: any) => { const host = grid.current; if (!host || !track) return; const element = track.attach(); element.className = 'live-track'; host.appendChild(element) }
    const detach = (track: any) => track?.detach?.().forEach?.((el: HTMLElement) => el.remove())
    const refresh = () => setParticipants(livekit.remoteParticipants.size + 1)
    const chat = (payload: ChatMessage, participant?: { identity?: string }) => setMessages(current => [...current, `${participant?.identity ?? 'Participant'}: ${payload.message}`])
    livekit.on(RoomEvent.TrackSubscribed, track => attach(track))
    livekit.on(RoomEvent.TrackUnsubscribed, track => detach(track))
    livekit.on(RoomEvent.ParticipantConnected, refresh)
    livekit.on(RoomEvent.ParticipantDisconnected, refresh)
    livekit.on(RoomEvent.ChatMessage, chat)
    let stopped = false
    void livekit.connect(token.serverUrl, token.participantToken).then(async () => {
      if (stopped) return
      await livekit.localParticipant.setMicrophoneEnabled(true)
      await livekit.localParticipant.setCameraEnabled(true)
      for (const publication of livekit.localParticipant.trackPublications.values()) {
        if (publication.track && (publication.kind === Track.Kind.Video || publication.kind === Track.Kind.Audio)) attach(publication.track)
      }
      refresh()
    }).catch(() => undefined)
    const heartbeat = window.setInterval(() => { void import('../platform/api').then(api => api.heartbeatLiveClass(liveClass.id, liveClass.lowBandwidth ? 'LOW_BANDWIDTH' : 'BALANCED')).catch(() => undefined) }, 30000)
    return () => {
      stopped = true
      window.clearInterval(heartbeat)
      livekit.removeAllListeners()
      detach(livekit.localParticipant.trackPublications.values().next().value?.track)
      void livekit.disconnect()
      void import('../platform/api').then(api => api.leaveLiveClass(liveClass.id)).catch(() => undefined)
      setRoom(null)
    }
  }, [token.liveClassId, token.serverUrl, token.participantToken, liveClass.id, liveClass.lowBandwidth])

  async function toggleMic() { if (!room) return; const next = !mic; await room.localParticipant.setMicrophoneEnabled(next); setMic(next) }
  async function toggleCamera() { if (!room) return; const next = !camera; await room.localParticipant.setCameraEnabled(next); setCamera(next) }
  async function toggleScreen() { if (!room) return; const next = !screen; await room.localParticipant.setScreenShareEnabled(next); setScreen(next) }
  async function sendMessage() { if (!room || !message.trim() || !liveClass.chatEnabled) return; await room.localParticipant.sendText(message.trim(), { topic: 'uep-chat', reliable: true }); setMessages(current => [...current, `You: ${message.trim()}`]); setMessage('') }

  return <div>
    <div className="live-stage" ref={grid} aria-label="Live classroom media grid"><div className="live-stage-overlay"><strong>Connected</strong><span>{participants} participants</span></div></div>
    <div className="button-row">
      <button className="secondary-button" onClick={() => void toggleCamera()}>{camera ? 'Camera off' : 'Camera on'}</button>
      <button className="secondary-button" onClick={() => void toggleMic()}>{mic ? 'Mute' : 'Unmute'}</button>
      <button className="secondary-button" onClick={() => void toggleScreen()}>{screen ? 'Stop sharing' : 'Share screen'}</button>
    </div>
    {liveClass.chatEnabled && <div className="live-chat panel-subsection"><div className="live-chat-log" aria-live="polite">{messages.length === 0 ? <span className="muted">No chat messages yet.</span> : messages.map((item, index) => <p key={`${index}-${item}`}>{item}</p>)}</div><div className="button-row"><input aria-label="Chat message" value={message} onChange={event => setMessage(event.target.value)} onKeyDown={event => { if (event.key === 'Enter') void sendMessage() }} placeholder="Send a classroom message" /><button className="secondary-button" onClick={() => void sendMessage()}>Send</button></div></div>}
    <p className="muted small">LiveKit adaptive stream and dynacast are enabled. Low-bandwidth rooms publish at 360p by default; screen sharing and reliable classroom chat use the browser media/data channels.</p>
  </div>
}
