import { useEffect, useMemo, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { CurrentIdentity, PermissionKey, QuestionType } from '../platform/api'
import { autosaveAnswer, createAssessment, createQuestion, fetchAssessmentPacket, fetchAssessmentVersions, fetchAssessments, fetchQuestions, startAttempt, submitAttempt } from '../platform/api'

function has(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) }
const questionTypes: QuestionType[] = ['SINGLE_MCQ','MULTIPLE_SELECTION','TRUE_FALSE','NUMERIC','FILL_BLANK','SHORT_ANSWER','LONG_ANSWER','ESSAY','MATCHING','ORDERING','FILE_SUBMISSION']

export function AssessmentWorkspace({ me }: { me: CurrentIdentity }) {
  const client = useQueryClient()
  const [payload, setPayload] = useState('{"prompt":"","options":[]}')
  const [selectedVersion, setSelectedVersion] = useState<string>()
  const [activeAttempt, setActiveAttempt] = useState<string>()
  const questions = useQuery({ queryKey: ['questions'], queryFn: fetchQuestions })
  const assessments = useQuery({ queryKey: ['assessments'], queryFn: fetchAssessments })
  const versions = useQuery({ queryKey: ['assessment-versions', selectedVersion], queryFn: () => fetchAssessmentVersions(selectedVersion!), enabled: Boolean(selectedVersion) })
  const packet = useQuery({ queryKey: ['assessment-packet', selectedVersion], queryFn: () => fetchAssessmentPacket(selectedVersion!), enabled: Boolean(selectedVersion) })
  const qMutation = useMutation({ mutationFn: createQuestion, onSuccess: () => void client.invalidateQueries({ queryKey: ['questions'] }) })
  const aMutation = useMutation({ mutationFn: createAssessment, onSuccess: () => void client.invalidateQueries({ queryKey: ['assessments'] }) })
  const attemptMutation = useMutation({ mutationFn: startAttempt, onSuccess: attempt => setActiveAttempt(attempt.id) })
  const submitMutation = useMutation({ mutationFn: submitAttempt, onSuccess: () => setActiveAttempt(undefined) })

  return <div className="content-stack">
    {has(me, 'ASSESSMENTS_MANAGE') && <>
      <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Question bank</p><h3>Create versioned question</h3></div></div>
        <form className="form-grid" onSubmit={e => { e.preventDefault(); const form = e.currentTarget; const d = new FormData(form); qMutation.mutate({ title: String(d.get('title') ?? ''), type: String(d.get('type') ?? 'SINGLE_MCQ') as QuestionType, difficulty: String(d.get('difficulty') ?? 'medium'), language: String(d.get('language') ?? '') || undefined, payloadJson: payload, positiveMarks: Number(d.get('positiveMarks') ?? 1), negativeMarks: Number(d.get('negativeMarks') ?? 0) }, { onSuccess: () => { form.reset(); setPayload('{"prompt":"","options":[]}') } }) }}>
          <label>Title<input name="title" required maxLength={200} /></label><label>Type<select name="type">{questionTypes.map(t => <option key={t}>{t}</option>)}</select></label><label>Difficulty<select name="difficulty"><option>easy</option><option>medium</option><option>hard</option></select></label><label>Language<input name="language" placeholder="en" /></label><label>Positive marks<input name="positiveMarks" type="number" min="1" defaultValue="1" /></label><label>Negative marks<input name="negativeMarks" type="number" min="0" defaultValue="0" /></label>
          <label className="wide">Payload JSON<textarea value={payload} onChange={e => setPayload(e.target.value)} rows={6} required /></label><div className="form-actions"><button className="primary-button" disabled={qMutation.isPending}>Create question</button></div>{qMutation.isError && <p className="error-text" role="alert">{qMutation.error.message}</p>}
        </form>
      </section>
      <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Exam construction</p><h3>Create assessment</h3></div></div>
        <form className="form-grid" onSubmit={e => { e.preventDefault(); const f=e.currentTarget; const d=new FormData(f); aMutation.mutate({ title: String(d.get('title') ?? '') }, { onSuccess: () => f.reset() }) }}><label>Assessment title<input name="title" required maxLength={240}/></label><div className="form-actions"><button className="primary-button" disabled={aMutation.isPending}>Create assessment</button></div></form>
      </section>
    </>}

    <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Question bank</p><h3>Questions</h3></div><span className="count-badge">{questions.data?.totalElements ?? 0}</span></div>{questions.isPending ? <p className="muted">Loading…</p> : questions.isError ? <p className="error-text" role="alert">{questions.error.message}</p> : <div className="table-wrap"><table><thead><tr><th>Title</th><th>Type</th><th>Difficulty</th><th>Version</th><th>Status</th></tr></thead><tbody>{questions.data?.items.map(q => <tr key={q.id}><td><strong>{q.title}</strong><span>{q.latestVersion.language ?? '—'}</span></td><td>{q.latestVersion.type}</td><td>{q.latestVersion.difficulty}</td><td>{q.latestVersion.versionNumber}</td><td>{q.status}</td></tr>)}</tbody></table></div>}</section>

    <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Assessments</p><h3>Examinations</h3></div><span className="count-badge">{assessments.data?.totalElements ?? 0}</span></div>{assessments.isPending ? <p className="muted">Loading…</p> : assessments.isError ? <p className="error-text" role="alert">{assessments.error.message}</p> : <div className="table-wrap"><table><thead><tr><th>Title</th><th>Status</th><th>Version</th><th>Action</th></tr></thead><tbody>{assessments.data?.items.map(a => <AssessmentRow key={a.id} title={a.title} status={a.status} version={a.version} assessmentId={a.id} selectedVersion={selectedVersion} onSelect={setSelectedVersion} />)}</tbody></table></div>}</section>

    {selectedVersion && packet.data && <ExamPanel version={packet.data.version} questions={packet.data.questions} activeAttempt={activeAttempt} setActiveAttempt={setActiveAttempt} attemptMutation={attemptMutation} submitMutation={submitMutation} />}

    {selectedVersion && versions.data && <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Version history</p><h3>Assessment versions</h3></div></div><div className="chip-row">{versions.data.map(v => <button key={v.id} className={v.id === selectedVersion ? 'chip active' : 'chip'} onClick={() => setSelectedVersion(v.id)}>v{v.versionNumber} · {v.maxAttempts} attempts · {v.totalMarks} marks</button>)}</div></section>}
  </div>
}

function AssessmentRow({ assessmentId, title, status, version, selectedVersion, onSelect }: { assessmentId: string; title: string; status: string; version: number; selectedVersion?: string; onSelect: (id: string) => void }) {
  const versions = useQuery({ queryKey: ['assessment-versions', assessmentId], queryFn: () => fetchAssessmentVersions(assessmentId) })
  return <tr><td>{title}</td><td>{status}</td><td>{version}</td><td>{versions.data?.length ? <select aria-label={`Version for ${title}`} value={selectedVersion ?? ''} onChange={e => e.target.value && onSelect(e.target.value)}><option value="">Select version</option>{versions.data.map(v => <option key={v.id} value={v.id}>v{v.versionNumber}</option>)}</select> : <span className="muted">No versions</span>}</td></tr>
}

function ExamPanel({ version, questions, activeAttempt, setActiveAttempt, attemptMutation, submitMutation }: any) {
  const [index, setIndex] = useState(0)
  const draftKey = `uep-assessment-draft-${version.id}-${activeAttempt ?? 'new'}`
  const [responses, setResponses] = useState<Record<string, string>>({})
  const [now, setNow] = useState(Date.now())
  const autosave = useMutation({ mutationFn: ({ questionId, value }: { questionId: string; value: string }) => autosaveAnswer(attemptMutation.data.id, questionId, JSON.stringify({ answer: value }), crypto.randomUUID(), Date.now()) })
  const q = questions[index]

  useEffect(() => { const id = window.setInterval(() => setNow(Date.now()), 1000); return () => window.clearInterval(id) }, [])
  useEffect(() => {
    const raw = sessionStorage.getItem(draftKey)
    if (raw) { try { setResponses(JSON.parse(raw) as Record<string, string>) } catch { sessionStorage.removeItem(draftKey) } }
  }, [draftKey])
  useEffect(() => { sessionStorage.setItem(draftKey, JSON.stringify(responses)) }, [draftKey, responses])
  useEffect(() => {
    if (!activeAttempt || !q) return
    const value = responses[q.id]
    if (value === undefined) return
    const id = window.setTimeout(() => autosave.mutate({ questionId: q.id, value }), 500)
    return () => window.clearTimeout(id)
  }, [activeAttempt, q, responses, autosave.mutate])

  const attempt = attemptMutation.data
  const displayExpiry = attempt?.expiresAt ? new Date(attempt.expiresAt).getTime() : undefined
  const fallbackRemaining = Math.max(0, Math.floor((new Date(version.availableUntil).getTime() - now) / 1000))
  const remaining = displayExpiry ? Math.max(0, Math.floor((displayExpiry - now) / 1000)) : fallbackRemaining
  const minutes = Math.floor(remaining / 60).toString().padStart(2, '0')
  const seconds = (remaining % 60).toString().padStart(2, '0')

  if (!q) return null
  return <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Web examination client</p><h3>Question {index + 1} of {questions.length}</h3></div><strong aria-live="polite">{minutes}:{seconds}</strong></div>
    {!activeAttempt ? <div className="form-actions"><button className="primary-button" disabled={attemptMutation.isPending} onClick={() => attemptMutation.mutate(version.id)}>Start server-timed attempt</button>{attemptMutation.isError && <p className="error-text" role="alert">{attemptMutation.error.message}</p>}</div> : <>
      <p className="muted">The server controls the expiry timestamp. Browser storage is only a resilience aid; every changed answer is also autosaved to the server.</p>
      <article className="hero-card"><p className="eyebrow">{q.type} · {q.marks} marks</p><h3>{q.title}</h3><pre style={{whiteSpace:'pre-wrap'}}>{q.payloadJson}</pre></article>
      <label>Answer<textarea rows={7} value={responses[q.id] ?? ''} onChange={e => setResponses(current => ({ ...current, [q.id]: e.target.value }))} /></label>
      {autosave.isError && <p className="error-text" role="alert">Answer could not be synchronized: {autosave.error.message}</p>}
      <div className="form-actions"><button className="secondary-button" disabled={index === 0} onClick={() => setIndex(value => Math.max(0, value - 1))}>Previous</button><button className="secondary-button" disabled={index === questions.length - 1} onClick={() => setIndex(value => Math.min(questions.length - 1, value + 1))}>Next</button><button className="primary-button" disabled={submitMutation.isPending} onClick={() => submitMutation.mutate(attempt.id)}>Submit attempt</button></div>
    </>}
    {activeAttempt && <p className="muted">Attempt ID: <code>{activeAttempt}</code></p>}
  </section>
}
