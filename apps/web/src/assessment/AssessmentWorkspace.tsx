import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type {
  AttemptView,
  AssessmentVersionView,
  CurrentIdentity,
  PacketQuestion,
  PermissionKey,
  QuestionType,
} from '../platform/api'
import type { UseMutationResult } from '@tanstack/react-query'
import {
  assignAssessmentVersion,
  autosaveAnswer,
  createAssessment,
  createAssessmentVersion,
  createQuestion,
  fetchAssessmentPacket,
  fetchAssessmentVersions,
  fetchAssessments,
  fetchBatches,
  fetchQuestions,
  publishAssessment,
  startAttempt,
  submitAttempt,
} from '../platform/api'
import { GradingReviewWorkspace } from './GradingReviewWorkspace'

function has(me: CurrentIdentity, permission: PermissionKey) { return me.permissions.includes(permission) }
const questionTypes: QuestionType[] = ['SINGLE_MCQ', 'MULTIPLE_SELECTION', 'TRUE_FALSE', 'NUMERIC', 'FILL_BLANK', 'SHORT_ANSWER', 'LONG_ANSWER', 'ESSAY', 'MATCHING', 'ORDERING', 'FILE_SUBMISSION']
const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

interface AssessmentVersionInput {
  durationSeconds?: number
  maxAttempts: number
  totalMarks: number
  passMarks: number
  shuffleQuestions: boolean
  allowBacktracking: boolean
  availableFrom: string
  availableUntil: string
}

export function AssessmentWorkspace({ me }: { me: CurrentIdentity }) {
  const client = useQueryClient()
  const [payload, setPayload] = useState('{"prompt":"","options":[{"id":"option-1","label":" "}]}')
  const [selectedVersion, setSelectedVersion] = useState<string>()
  const [activeAttemptId, setActiveAttemptId] = useState<string>()
  const questions = useQuery({ queryKey: ['questions'], queryFn: fetchQuestions, enabled: has(me, 'ASSESSMENTS_MANAGE') })
  const assessments = useQuery({
    queryKey: ['assessments'],
    queryFn: fetchAssessments,
    enabled: has(me, 'ASSESSMENTS_VIEW') || has(me, 'ASSESSMENTS_TAKE'),
  })
  const packet = useQuery({ queryKey: ['assessment-packet', selectedVersion], queryFn: () => fetchAssessmentPacket(selectedVersion!), enabled: Boolean(selectedVersion) })
  const qMutation = useMutation({ mutationFn: createQuestion, onSuccess: () => void client.invalidateQueries({ queryKey: ['questions'] }) })
  const aMutation = useMutation({ mutationFn: createAssessment, onSuccess: () => void client.invalidateQueries({ queryKey: ['assessments'] }) })
  const vMutation = useMutation({
    mutationFn: (input: AssessmentVersionInput) => createAssessmentVersion(selectedVersion!, input),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['assessment-versions', selectedVersion] }),
  })
  const attemptMutation = useMutation({
    mutationFn: startAttempt,
    onSuccess: attempt => {
      setActiveAttemptId(attempt.id)
      void client.invalidateQueries({ queryKey: ['assessment-packet', selectedVersion] })
    },
  })
  const submitMutation = useMutation({
    mutationFn: (attemptId: string) => submitAttempt(attemptId),
    onSuccess: () => {
      setActiveAttemptId(undefined)
      void client.invalidateQueries({ queryKey: ['assessment-packet', selectedVersion] })
    },
  })
  const canManage = has(me, 'ASSESSMENTS_MANAGE')
  const canTake = has(me, 'ASSESSMENTS_TAKE')

  return <div className="content-stack">
    {canManage && <>
      <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Question bank</p><h3>Create versioned question</h3></div></div>
        <form className="form-grid" onSubmit={e => { e.preventDefault(); const form = e.currentTarget; const d = new FormData(form); qMutation.mutate({ title: String(d.get('title') ?? ''), type: String(d.get('type') ?? 'SINGLE_MCQ') as QuestionType, difficulty: String(d.get('difficulty') ?? 'medium'), language: String(d.get('language') ?? '') || undefined, payloadJson: payload, positiveMarks: Number(d.get('positiveMarks') ?? 1), negativeMarks: Number(d.get('negativeMarks') ?? 0) }, { onSuccess: () => { form.reset(); setPayload('{"prompt":"","options":[{"id":"option-1","label":" "}]}') } }) }}>
          <label>Title<input name="title" required maxLength={200} /></label><label>Type<select name="type">{questionTypes.map(t => <option key={t}>{t}</option>)}</select></label><label>Difficulty<select name="difficulty"><option>easy</option><option>medium</option><option>hard</option></select></label><label>Language<input name="language" placeholder="en" /></label><label>Positive marks<input name="positiveMarks" type="number" min="1" defaultValue="1" /></label><label>Negative marks<input name="negativeMarks" type="number" min="0" defaultValue="0" /></label>
          <label className="wide">
            Payload JSON
            <textarea value={payload} onChange={e => setPayload(e.target.value)} rows={6} required />
            <span className="muted small">
              Canonical shape: <code>{'{"prompt":"","options":[{"id":"option-1","label":"…"}]}'}</code>.
              Matching uses <code>left</code>/<code>right</code>, ordering uses <code>items</code>.
              Answer keys are never stored here.
            </span>
          </label><div className="form-actions"><button className="primary-button" disabled={qMutation.isPending}>Create question</button></div>{qMutation.isError && <p className="error-text wide" role="alert">{qMutation.error.message}</p>}
        </form>
      </section>
      <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Exam construction</p><h3>Create assessment</h3></div></div>
        <form className="form-grid" onSubmit={e => { e.preventDefault(); const f = e.currentTarget; const d = new FormData(f); aMutation.mutate({ title: String(d.get('title') ?? '') }, { onSuccess: () => f.reset() }) }}><label>Assessment title<input name="title" required maxLength={240} /></label><div className="form-actions"><button className="primary-button" disabled={aMutation.isPending}>Create assessment</button></div>{aMutation.isError && <p className="error-text" role="alert">{aMutation.error.message}</p>}</form>
      </section>
    </>}

    {canManage && <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Question bank</p><h3>Questions</h3></div><span className="count-badge">{questions.data?.totalElements ?? 0}</span></div>{questions.isPending ? <p className="muted">Loading…</p> : questions.isError ? <p className="error-text" role="alert">{questions.error.message}</p> : <div className="table-wrap"><table aria-label="Question bank"><thead><tr><th>Title</th><th>Type</th><th>Difficulty</th><th>Version</th><th>Status</th></tr></thead><tbody>{questions.data?.items.map(q => <tr key={q.id}><td><strong>{q.title}</strong><span>{q.latestVersion.language ?? '—'}</span></td><td>{q.latestVersion.type}</td><td>{q.latestVersion.difficulty}</td><td>{q.latestVersion.versionNumber}</td><td>{q.status}</td></tr>)}</tbody></table></div>}</section>}

    <section className="panel"><div className="panel-heading"><div><p className="eyebrow">Assessments</p><h3>Examinations</h3></div><span className="count-badge">{assessments.data?.totalElements ?? 0}</span></div>
      {assessments.isPending ? <p className="muted">Loading…</p> : assessments.isError ? <p className="error-text" role="alert">{assessments.error.message}</p> : <div className="table-wrap"><table aria-label="Assessments"><thead><tr><th>Title</th><th>Status</th><th>Versions</th><th>Action</th></tr></thead><tbody>{assessments.data?.items.map(a => <AssessmentRow key={a.id} assessmentId={a.id} title={a.title} status={a.status} version={a.version} selectedVersion={selectedVersion} onSelectVersion={setSelectedVersion} canTake={canTake} canManage={canManage} />)}</tbody></table></div>}
    </section>

    {canManage && selectedVersion && <VersionBuilder versionId={selectedVersion} create={vMutation} />}

    {selectedVersion && packet.isError && <section className="panel"><p className="error-text" role="alert">Packet could not be loaded: {packet.error.message}</p></section>}
    {selectedVersion && packet.data && <ExamPanel version={packet.data.version} questions={packet.data.questions} attemptId={activeAttemptId} attemptMutation={attemptMutation} submitMutation={submitMutation} />}

    <GradingReviewWorkspace me={me} />
  </div>
}

/** Teacher flow: build a version for a selected assessment, publish the assessment, then assign it to a batch. */
function VersionBuilder({ versionId, create }: {
  versionId: string
  create: UseMutationResult<AssessmentVersionView, Error, AssessmentVersionInput>
}) {
  const batches = useQuery({ queryKey: ['batches'], queryFn: fetchBatches })
  const [batchId, setBatchId] = useState('')
  const assign = useMutation({
    mutationFn: (input: { versionId: string; batchId: string }) => assignAssessmentVersion(input.versionId, input.batchId),
  })

  return <section className="panel">
    <div className="panel-heading"><div><p className="eyebrow">Exam construction</p><h3>Build version &amp; assign batch</h3></div></div>
    <form className="form-grid" onSubmit={event => {
      event.preventDefault()
      const form = event.currentTarget
      const data = new FormData(form)
      const duration = String(data.get('durationSeconds') ?? '')
      create.mutate({
        ...(duration ? { durationSeconds: Number(duration) } : {}),
        maxAttempts: Number(data.get('maxAttempts') ?? 1),
        totalMarks: Number(data.get('totalMarks') ?? 0),
        passMarks: Number(data.get('passMarks') ?? 0),
        shuffleQuestions: data.get('shuffleQuestions') === 'on',
        allowBacktracking: data.get('allowBacktracking') === 'on',
        availableFrom: String(data.get('availableFrom') ?? ''),
        availableUntil: String(data.get('availableUntil') ?? ''),
      })
    }}>
      <label>Duration (seconds)<input name="durationSeconds" type="number" min="60" defaultValue="3600" /></label>
      <label>Max attempts<input name="maxAttempts" type="number" min="1" defaultValue="1" /></label>
      <label>Total marks<input name="totalMarks" type="number" min="0" defaultValue="10" /></label>
      <label>Pass marks<input name="passMarks" type="number" min="0" defaultValue="5" /></label>
      <label className="check-row"><input type="checkbox" name="shuffleQuestions" />Shuffle questions</label>
      <label className="check-row"><input type="checkbox" name="allowBacktracking" defaultChecked />Allow backtracking</label>
      <label>Available from<input name="availableFrom" type="datetime-local" required /></label>
      <label>Available until<input name="availableUntil" type="datetime-local" required /></label>
      <div className="form-actions"><button className="primary-button" disabled={create.isPending}>Create version</button></div>
      {create.isError && <p className="error-text" role="alert">Version creation failed: {create.error.message}</p>}
      {create.isSuccess && <p className="success-text" role="status">Version created. Publish the assessment, then assign it to a batch.</p>}
    </form>
    <div className="form-grid">
      <label>
        Assign to batch
        <select value={batchId} onChange={event => setBatchId(event.target.value)}>
          <option value="">Select batch</option>
          {batches.data?.items.map(batch => <option key={batch.id} value={batch.id}>{batch.displayName}</option>)}
        </select>
      </label>
      <div className="form-actions">
        <button className="secondary-button" disabled={!batchId || assign.isPending} onClick={() => batchId && void assign.mutateAsync({ versionId, batchId }).catch(() => undefined)}>
          Assign batch
        </button>
      </div>
    </div>
    {assign.isError && <p className="error-text" role="alert">Batch assignment failed: {assign.error.message}</p>}
    {assign.isSuccess && <p className="success-text" role="status">Assessment assigned to the selected batch.</p>}
    {batches.isError && <p className="error-text" role="alert">Batches could not be loaded: {batches.error.message}</p>}
  </section>
}

function AssessmentRow({ assessmentId, title, status, version, selectedVersion, onSelectVersion, canTake, canManage }: {
  assessmentId: string
  title: string
  status: string
  version: number
  selectedVersion?: string
  onSelectVersion: (id: string) => void
  canTake: boolean
  canManage: boolean
}) {
  const client = useQueryClient()
  const versions = useQuery({ queryKey: ['assessment-versions', assessmentId], queryFn: () => fetchAssessmentVersions(assessmentId) })
  const publish = useMutation({
    mutationFn: () => publishAssessment(assessmentId, version),
    onSuccess: () => void client.invalidateQueries({ queryKey: ['assessments'] }),
  })
  return <tr><td>{title}</td><td>{status}</td><td>{versions.data?.length ?? '—'}</td><td>{versions.data?.length
    ? canTake
      ? <select aria-label={`Select an attempt-able version of ${title}`} value={selectedVersion ?? ''} onChange={e => e.target.value && onSelectVersion(e.target.value)}><option value="">Select version</option>{versions.data.map(v => <option key={v.id} value={v.id}>v{v.versionNumber}</option>)}</select>
      : <span className="muted">{versions.data.length} built</span>
    : <span className="muted">No versions</span>}
    {canManage && status !== 'PUBLISHED' && status !== 'CLOSED' && (versions.data?.length ?? 0) > 0 && <button className="primary-button" disabled={publish.isPending} onClick={() => publish.mutate()}>{publish.isPending ? 'Publishing…' : 'Publish'}</button>}
    {publish.isSuccess && <span className="success-text" role="status">Published.</span>}
    {publish.isError && <span className="error-text" role="alert">{publish.error.message}</span>}</td></tr>
}

/** Answer-selection state kept beside the plain-text responses map. */
type ChoiceState =
  | { kind: 'single'; value?: string }
  | { kind: 'multi'; values: string[] }
  | { kind: 'match'; map: Record<string, string> }
  | { kind: 'order'; seq: string[] }

type DraftState = { choice: ChoiceState | undefined; text: string }

interface ChoiceItem { id: string; label: string }

function toItems(value: unknown): ChoiceItem[] {
  if (!Array.isArray(value)) return []
  return value.map(entry => {
    if (typeof entry === 'string') return { id: entry, label: entry }
    if (entry && typeof entry === 'object') {
      const record = entry as { id?: unknown; label?: unknown; key?: unknown; text?: unknown }
      const id = typeof record.id === 'string' ? record.id : typeof record.key === 'string' ? record.key : ''
      const label = typeof record.label === 'string' ? record.label : typeof record.text === 'string' ? record.text : id
      if (id) return { id, label }
    }
    return null
  }).filter((item): item is ChoiceItem => item !== null)
}

function parsePayload(raw: string): Record<string, unknown> {
  try {
    const parsed = JSON.parse(raw) as unknown
    return parsed && typeof parsed === 'object' ? parsed as Record<string, unknown> : {}
  } catch {
    return {}
  }
}

function initialChoice(question: PacketQuestion, draft: DraftState | undefined): ChoiceState {
  if (draft?.choice) return draft.choice
  const parsed = parsePayload(question.payloadJson)
  switch (question.type) {
    case 'MULTIPLE_SELECTION':
      return { kind: 'multi', values: [] }
    case 'MATCHING': {
      const map: Record<string, string> = {}
      for (const item of toItems(parsed.left)) map[item.id] = ''
      return { kind: 'match', map }
    }
    case 'ORDERING':
      return { kind: 'order', seq: toItems(parsed.items).map(item => item.id) }
    default:
      return { kind: 'single', value: undefined }
  }
}

/** Serializes a draft into the exact answer shape the grading engine compares. */
function toAnswerPayload(type: string, choice: ChoiceState, text: string): string {
  if (type === 'MULTIPLE_SELECTION' && choice.kind === 'multi') return JSON.stringify({ answers: choice.values })
  if (type === 'MATCHING' && choice.kind === 'match') {
    return JSON.stringify({ matches: Object.entries(choice.map).map(([left, right]) => ({ left, right: right || '' })) })
  }
  if (type === 'ORDERING' && choice.kind === 'order') return JSON.stringify({ order: choice.seq })
  if (choice.kind === 'single') return JSON.stringify({ answer: choice.value ?? '' })
  return JSON.stringify({ answer: text })
}

function formatClock(totalSeconds: number): string {
  return `${Math.floor(totalSeconds / 60).toString().padStart(2, '0')}:${(totalSeconds % 60).toString().padStart(2, '0')}`
}

function ExamPanel({ version, questions, attemptId, attemptMutation, submitMutation }: {
  version: AssessmentVersionView
  questions: PacketQuestion[]
  attemptId: string | undefined
  attemptMutation: UseMutationResult<AttemptView, Error, string>
  submitMutation: UseMutationResult<AttemptView, Error, string>
}) {
  const [index, setIndex] = useState(0)
  const [drafts, setDrafts] = useState<Record<string, DraftState>>({})
  const [now, setNow] = useState(() => Date.now())
  const [tickwarning, setTickWarning] = useState('')
  const announced = useRef<Set<number>>(new Set())
  const attempt = attemptMutation.data
  const attemptUuid = attemptId ?? attempt?.id
  const validAttemptId = attemptUuid && UUID_PATTERN.test(attemptUuid) ? attemptUuid : undefined
  const autosave = useMutation({
    mutationFn: (input: { questionId: string; payloadJson: string }) =>
      autosaveAnswer(validAttemptId!, input.questionId, input.payloadJson, crypto.randomUUID(), Date.now()),
  })
  const q = questions[index]
  const draftKey = `uep-assessment-draft-${version.id}-${validAttemptId ?? 'new'}`
  const draft = q ? (drafts[q.id] ?? { choice: undefined, text: '' }) : undefined
  const choice = q ? initialChoice(q, draft) : undefined
  const structuredPayload = q && choice ? toAnswerPayload(q.type, choice, draft?.text ?? '') : ''
  const remainingSeconds = validAttemptId && attempt?.expiresAt
    ? Math.max(0, Math.floor((new Date(attempt.expiresAt).getTime() - now) / 1000))
    : undefined

  useEffect(() => {
    if (!validAttemptId) return
    const id = window.setInterval(() => setNow(Date.now()), 1000)
    return () => window.clearInterval(id)
  }, [validAttemptId])

  useEffect(() => {
    const raw = sessionStorage.getItem(draftKey)
    if (raw) {
      try {
        setDrafts(JSON.parse(raw) as Record<string, DraftState>)
      } catch {
        sessionStorage.removeItem(draftKey)
      }
    }
  }, [draftKey])

  useEffect(() => {
    sessionStorage.setItem(draftKey, JSON.stringify(drafts))
  }, [draftKey, drafts])

  useEffect(() => {
    announced.current = new Set<number>()
    setTickWarning('')
  }, [validAttemptId])

  useEffect(() => {
    if (validAttemptId === undefined || remainingSeconds === undefined) return
    for (const threshold of [300, 60]) {
      if (remainingSeconds <= threshold && remainingSeconds > threshold - 30 && !announced.current.has(threshold)) {
        announced.current.add(threshold)
        setTickWarning(`Less than ${Math.round(threshold / 60)} minute(s) remain`)
      }
    }
  })

  useEffect(() => {
    if (!validAttemptId || !q || structuredPayload === '') return
    const previous = drafts[q.id]
    if (!previous) return
    const previousPayload = toAnswerPayload(q.type, initialChoice(q, previous), previous.text)
    if (previousPayload === structuredPayload) return
    const id = window.setTimeout(() => autosave.mutate({ questionId: q.id, payloadJson: structuredPayload }), 500)
    return () => window.clearTimeout(id)
  }, [validAttemptId, autosave.mutate, structuredPayload, drafts, q])

  function updateDraft(next: DraftState) {
    if (!q || !validAttemptId) return
    setDrafts(current => ({ ...current, [q.id]: next }))
  }

  if (!q) return null

  return <section className="panel">
    <div className="panel-heading">
      <div><p className="eyebrow">Web examination client</p><h3>Question {index + 1} of {questions.length}</h3></div>
      {validAttemptId && remainingSeconds !== undefined
        ? <span role="timer" aria-label="Time remaining">{formatClock(remainingSeconds)}</span>
        : <span className="muted small">No running attempt — the countdown starts with the server-timed attempt.</span>}
      {tickwarning && <span className="sr-only" role="status">{tickwarning}</span>}
    </div>
    {!validAttemptId ? <div className="form-actions">
      <button className="primary-button" disabled={attemptMutation.isPending} onClick={() => attemptMutation.mutate(version.id)}>Start server-timed attempt</button>
      {attemptMutation.isError && <p className="error-text" role="alert">{attemptMutation.error.message}</p>}
    </div> : <>
      <p className="muted">The server controls the expiry timestamp. Browser storage is only a resilience aid; every changed answer is also autosaved to the server.</p>
      <article className="hero-card">
        <p className="eyebrow">{q.type} · {q.marks} marks</p>
        <h3>{q.title}</h3>
        <QuestionView q={q} choice={choice} text={draft?.text ?? ''} onChange={updateDraft} />
      </article>
      {autosave.isError && <p className="error-text" role="alert">Answer could not be synchronized: {autosave.error.message}</p>}
      <div className="form-actions">
        <button className="secondary-button" disabled={index === 0} onClick={() => setIndex(value => Math.max(0, value - 1))}>Previous</button>
        <button className="secondary-button" disabled={index === questions.length - 1} onClick={() => setIndex(value => Math.min(questions.length - 1, value + 1))}>Next</button>
        <button className="primary-button" disabled={submitMutation.isPending} onClick={() => validAttemptId && submitMutation.mutate(validAttemptId)}>Submit attempt</button>
      </div>
      {submitMutation.isError && <p className="error-text" role="alert">Attempt submission failed: {submitMutation.error.message}</p>}
      <p className="muted">Attempt ID: <code>{validAttemptId}</code></p>
    </>}
  </section>
}

function QuestionView({ q, choice, text, onChange }: {
  q: PacketQuestion
  choice: ChoiceState | undefined
  text: string
  onChange: (draft: DraftState) => void
}) {
  const payload = parsePayload(q.payloadJson)
  const prompt = typeof payload.prompt === 'string' ? payload.prompt : undefined

  if (!choice) return <pre style={{ whiteSpace: 'pre-wrap' }}>{q.payloadJson}</pre>

  switch (q.type) {
    case 'SINGLE_MCQ':
    case 'TRUE_FALSE': {
      const options = toItems(payload.options).length
        ? toItems(payload.options)
        : q.type === 'TRUE_FALSE' ? [{ id: 'TRUE', label: 'True' }, { id: 'FALSE', label: 'False' }] : []
      if (!options.length || choice.kind !== 'single') {
        return <AnswerTextarea value={text} onChange={value => onChange({ choice, text: value })} label="Answer" />
      }
      const name = `question-${q.id}`
      return <fieldset>
        {prompt && <legend>{prompt}</legend>}
        {options.map(option => (
          <label className="check-row" key={option.id}>
            <input
              type="radio"
              name={name}
              value={option.id}
              checked={choice.value === option.id}
              onChange={() => onChange({ choice: { kind: 'single', value: option.id }, text: '' })}
            />
            {option.label || option.id}
          </label>
        ))}
      </fieldset>
    }
    case 'MULTIPLE_SELECTION': {
      const options = toItems(payload.options)
      if (!options.length || choice.kind !== 'multi') {
        return <AnswerTextarea value={text} onChange={value => onChange({ choice, text: value })} label="Answer" />
      }
      const values = choice.values
      return <fieldset>
        {prompt && <legend>{prompt}</legend>}
        {options.map(option => (
          <label className="check-row" key={option.id}>
            <input
              type="checkbox"
              checked={values.includes(option.id)}
              onChange={event => onChange({ choice: { kind: 'multi', values: event.target.checked ? [...values, option.id] : values.filter(id => id !== option.id) }, text: '' })}
            />
            {option.label || option.id}
          </label>
        ))}
      </fieldset>
    }
    case 'MATCHING': {
      const left = toItems(payload.left)
      const right = toItems(payload.right)
      if (!left.length || !right.length || choice.kind !== 'match') {
        return <AnswerTextarea value={text} onChange={value => onChange({ choice, text: value })} label="Answer (matching JSON)" />
      }
      return <fieldset>
        {prompt && <legend>{prompt}</legend>}
        {left.map(item => (
          <label key={item.id}>
            {item.label || item.id}
            <select
              aria-label={`Match for ${item.label || item.id}`}
              value={choice.map[item.id] ?? ''}
              onChange={event => onChange({ choice: { kind: 'match', map: { ...choice.map, [item.id]: event.target.value } }, text: '' })}
            >
              <option value="">No selection</option>
              {right.map(option => <option key={option.id} value={option.id}>{option.label || option.id}</option>)}
            </select>
          </label>
        ))}
      </fieldset>
    }
    case 'ORDERING': {
      const items = toItems(payload.items)
      if (!items.length || choice.kind !== 'order') {
        return <AnswerTextarea value={text} onChange={value => onChange({ choice, text: value })} label="Answer (ordering JSON)" />
      }
      const seq = choice.seq
      function move(position: number, direction: -1 | 1) {
        const next = [...seq]
        const swapWith = position + direction
        if (swapWith < 0 || swapWith >= next.length) return
        const moved = next[position] ?? ''
        const displaced = next[swapWith] ?? ''
        next[position] = displaced
        next[swapWith] = moved
        onChange({ choice: { kind: 'order', seq: next }, text: '' })
      }
      return <fieldset>
        {prompt && <legend>{prompt}</legend>}
        <p className="muted small">Reorder with the arrows; the submitted order is the list below.</p>
        <ol className="ordering-list">
          {seq.map((id, position) => {
            const item = items.find(entry => entry.id === id)
            return <li key={id}>
              <span>{item?.label || item?.id || id}</span>
              <span className="ordering-actions">
                <button type="button" className="text-button" aria-label={`Move ${item?.label || id} up`} disabled={position === 0} onClick={() => move(position, -1)}>↑ Up</button>
                <button type="button" className="text-button" aria-label={`Move ${item?.label || id} down`} disabled={position === seq.length - 1} onClick={() => move(position, 1)}>↓ Down</button>
              </span>
            </li>
          })}
        </ol>
      </fieldset>
    }
    default:
      return <AnswerTextarea value={text} onChange={value => onChange({ choice, text: value })} label="Answer" />
  }
}

function AnswerTextarea({ value, onChange, label }: { value: string; onChange: (value: string) => void; label: string }) {
  return <label>
    {label}
    <textarea rows={6} value={value} onChange={event => onChange(event.target.value)} />
  </label>
}
