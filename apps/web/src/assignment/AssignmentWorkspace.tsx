import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import type { AssignmentSubmission, AssignmentView, CurrentIdentity } from '../platform/api'
import {
  assignAssignmentToBatch,
  createAssignment,
  fetchAssignmentSubmissions,
  fetchAssignments,
  fetchMyAssignmentSubmissions,
  fetchMyGradebook,
  saveAssignmentDraft,
  gradeAssignmentSubmission,
  submitAssignment,
  updateAssignmentStatus,
} from '../platform/api'

export function AssignmentWorkspace({ me }: { me: CurrentIdentity }) {
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState<AssignmentView>()
  const [submissionText, setSubmissionText] = useState('')
  const [batchId, setBatchId] = useState('')

  const assignments = useQuery({
    queryKey: ['assignments'],
    queryFn: fetchAssignments,
    enabled: me.permissions.includes('ASSIGNMENTS_VIEW'),
  })
  const mine = useQuery({
    queryKey: ['assignment-submissions'],
    queryFn: fetchMyAssignmentSubmissions,
    enabled: me.permissions.includes('ASSIGNMENTS_TAKE'),
  })
  const submissions = useQuery({
    queryKey: ['assignment-submissions-admin', selected?.id],
    queryFn: () => fetchAssignmentSubmissions(selected!.id),
    enabled: Boolean(selected?.id) && me.permissions.includes('ASSIGNMENTS_MANAGE'),
  })
  const gradebook = useQuery({
    queryKey: ['gradebook-me', batchId],
    queryFn: () => fetchMyGradebook(batchId),
    enabled: Boolean(batchId) && me.permissions.includes('GRADEBOOK_VIEW'),
  })

  const create = useMutation({
    mutationFn: createAssignment,
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['assignments'] }),
  })
  const status = useMutation({
    mutationFn: ({ id, version, next }: { id: string; version: number; next: 'PUBLISHED' | 'CLOSED' }) =>
      updateAssignmentStatus(id, next, version),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['assignments'] })
      setSelected(undefined)
    },
  })
  const assign = useMutation({
    mutationFn: ({ id, batch }: { id: string; batch: string }) => assignAssignmentToBatch(id, batch),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['assignments'] }),
  })
  const submit = useMutation({
    mutationFn: ({ id, text }: { id: string; text: string }) => submitAssignment(id, text),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['assignment-submissions'] })
      setSelected(undefined)
      setSubmissionText('')
    },
  })
  const { mutate: saveDraft, isPending: isSavingDraft, isSuccess: draftSaved } = useMutation({
    mutationFn: ({ id, text }: { id: string; text: string }) => saveAssignmentDraft(id, text),
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['assignment-submissions'] }),
  })

  useEffect(() => {
    if (!selected || selected.status !== 'PUBLISHED' || !me.permissions.includes('ASSIGNMENTS_TAKE')) return
    if (!submissionText.trim()) return
    const timer = window.setTimeout(() => {
      saveDraft({ id: selected.id, text: submissionText })
    }, 800)
    return () => window.clearTimeout(timer)
  }, [selected, submissionText, me.permissions, saveDraft])
  const grade = useMutation({
    mutationFn: ({ sub }: { sub: AssignmentSubmission }) => {
      const points = Number((document.getElementById(`points-${sub.id}`) as HTMLInputElement | null)?.value ?? 0)
      const feedback = (document.getElementById(`feedback-${sub.id}`) as HTMLTextAreaElement | null)?.value ?? ''
      const rubricScoresJson = (document.getElementById(`rubric-${sub.id}`) as HTMLTextAreaElement | null)?.value ?? '{}'
      return gradeAssignmentSubmission(sub.id, points, feedback, rubricScoresJson, sub.version)
    },
    onSuccess: () => void queryClient.invalidateQueries({ queryKey: ['assignment-submissions-admin', selected?.id] }),
  })

  const manageable = me.permissions.includes('ASSIGNMENTS_MANAGE')

  return (
    <div className="content-stack">
      {manageable && (
        <section className="panel">
          <div className="panel-heading">
            <div>
              <p className="eyebrow">Authoring</p>
              <h3>Create assignment</h3>
            </div>
          </div>
          <form
            className="form-grid"
            onSubmit={event => {
              event.preventDefault()
              const form = event.currentTarget
              const data = new FormData(form)
              create.mutate({
                title: String(data.get('title') ?? ''),
                instructions: String(data.get('instructions') ?? '') || undefined,
                maxPoints: Number(data.get('maxPoints') ?? 100),
                weightBasisPoints: Math.round(Number(data.get('weightBasisPoints') ?? 10) * 100),
                dueAt: new Date(String(data.get('dueAt') ?? '')).toISOString(),
              }, { onSuccess: () => form.reset() })
            }}
          >
            <label>Title<input name="title" required maxLength={240} /></label>
            <label>Due at<input name="dueAt" type="datetime-local" required /></label>
            <label>Max points<input name="maxPoints" type="number" min="1" defaultValue="100" /></label>
            <label>Weight %<input name="weightBasisPoints" type="number" min="0" max="100" step="0.01" defaultValue="10" /></label>
            <label className="wide">Instructions<textarea name="instructions" rows={3} /></label>
            <div className="form-actions">
              <button className="primary-button" disabled={create.isPending}>Create draft</button>
            </div>
            {create.isError && <p className="error-text" role="alert">{create.error.message}</p>}
          </form>
        </section>
      )}

      <section className="panel">
        <div className="panel-heading">
          <div><p className="eyebrow">Assignments</p><h3>Course work</h3></div>
          <span className="count-badge">{assignments.data?.totalElements ?? 0}</span>
        </div>
        {assignments.isPending && <p className="muted">Loading…</p>}
        {assignments.isError && <p className="error-text" role="alert">{assignments.error.message}</p>}
        {assignments.data?.items.map(assignment => (
          <article key={assignment.id} className="list-row">
            <div>
              <strong>{assignment.title}</strong>
              <span>
                {assignment.maxPoints} pts · {assignment.status} · due {new Date(assignment.dueAt).toLocaleString()}
                {assignment.weightBasisPoints ? ` · ${(assignment.weightBasisPoints / 100).toFixed(2)}%` : ''}
              </span>
            </div>
            <div className="form-actions">
              {manageable && <button className="secondary-button" onClick={() => setSelected(assignment)}>Manage</button>}
              {manageable && (assignment.status === 'DRAFT' || assignment.status === 'PUBLISHED') && (
                <button className="secondary-button" disabled={assign.isPending || !batchId} onClick={() => assign.mutate({ id: assignment.id, batch: batchId })}>
                  Assign batch
                </button>
              )}
              {manageable && assignment.status === 'DRAFT' && (
                <button className="secondary-button" disabled={status.isPending || !batchId} onClick={() => status.mutate({ id: assignment.id, version: assignment.version, next: 'PUBLISHED' })}>
                  Publish
                </button>
              )}
              {assignment.status === 'PUBLISHED' && me.permissions.includes('ASSIGNMENTS_TAKE') && (
                <button className="primary-button" onClick={() => { setSelected(assignment); setSubmissionText('') }}>Work on it</button>
              )}
            </div>
          </article>
        ))}
      </section>

      {manageable && (
        <section className="panel">
          <div className="panel-heading">
            <div><p className="eyebrow">Batch assignment</p><h3>Operational scope</h3></div>
          </div>
          <label>Batch ID<input value={batchId} onChange={event => setBatchId(event.target.value)} placeholder="UUID of the target batch" /></label>
          {assign.isError && <p className="error-text" role="alert">{assign.error.message}</p>}
          {status.isError && <p className="error-text" role="alert">{status.error.message}</p>}
        </section>
      )}

      {selected && selected.status === 'PUBLISHED' && me.permissions.includes('ASSIGNMENTS_TAKE') && (
        <section className="panel">
          <div className="panel-heading">
            <div><p className="eyebrow">Submission</p><h3>{selected.title}</h3></div>
          </div>
          <p className="muted">Due {new Date(selected.dueAt).toLocaleString()} · {isSavingDraft ? 'Saving draft…' : draftSaved ? 'Draft saved' : ''}</p>
          <textarea rows={10} value={submissionText} onChange={event => setSubmissionText(event.target.value)} placeholder="Write your submission…" />
          <div className="form-actions">
            <button className="secondary-button" onClick={() => setSelected(undefined)}>Cancel</button>
            <button className="primary-button" disabled={submit.isPending || !submissionText.trim()} onClick={() => submit.mutate({ id: selected.id, text: submissionText })}>Submit work</button>
          </div>
          {submit.isError && <p className="error-text" role="alert">{submit.error.message}</p>}
        </section>
      )}

      {manageable && selected && (
        <section className="panel">
          <div className="panel-heading">
            <div><p className="eyebrow">Grading</p><h3>Submitted work</h3></div>
          </div>
          {submissions.isPending && <p className="muted">Loading submissions…</p>}
          {submissions.isError && <p className="error-text" role="alert">{submissions.error.message}</p>}
          {submissions.data?.items.map(submission => (
            <div key={submission.id} className="list-row">
              <div>
                <strong>Attempt {submission.attemptNumber}</strong>
                <span>{submission.status}{submission.late ? ' · Late' : ''} · {submission.awardedPoints == null ? 'Ungraded' : `${submission.awardedPoints}/${selected.maxPoints}`}</span>
                <p className="muted">{submission.textBody ?? 'File-backed submission'}</p>
              </div>
              {(submission.status === 'SUBMITTED' || submission.status === 'RESUBMITTED') && (
                <div>
                  <input id={`points-${submission.id}`} aria-label="Awarded points" type="number" min="0" max={selected.maxPoints} defaultValue={submission.awardedPoints ?? 0} />
                  <textarea id={`feedback-${submission.id}`} aria-label="Feedback" rows={2} placeholder="Feedback" />
                  <textarea id={`rubric-${submission.id}`} aria-label="Rubric scores JSON" rows={2} defaultValue={submission.rubricScoresJson} placeholder='Rubric scores JSON, e.g. {"criterion-1":4}' />
                  <button className="primary-button" disabled={grade.isPending} onClick={() => grade.mutate({ sub: submission })}>Save grade</button>
                </div>
              )}
            </div>
          ))}
        </section>
      )}

      {me.permissions.includes('GRADEBOOK_VIEW') && (
        <section className="panel">
          <div className="panel-heading">
            <div><p className="eyebrow">Gradebook</p><h3>My progress</h3></div>
          </div>
          <label>Batch ID<input value={batchId} onChange={event => setBatchId(event.target.value)} placeholder="Your enrolled batch UUID" /></label>
          {gradebook.isError && <p className="error-text" role="alert">{gradebook.error.message}</p>}
          {gradebook.data && (
            <>
              <div className="metric-grid">
                <div><strong>{gradebook.data.weightedPoints.toFixed(2)}</strong><span>Weighted points</span></div>
                <div><strong>{gradebook.data.completedAssignments}/{gradebook.data.totalAssignments}</strong><span>Assignments graded</span></div>
              </div>
              <div className="table-wrap">
                <table><thead><tr><th>Assignment</th><th>Weight</th><th>Score</th></tr></thead><tbody>
                  {gradebook.data.entries.map(entry => <tr key={entry.assignmentId}><td>{entry.title}</td><td>{(entry.weightBasisPoints / 100).toFixed(2)}%</td><td>{entry.awardedPoints == null ? '—' : `${entry.awardedPoints}/${entry.maxPoints}`}</td></tr>)}
                </tbody></table>
              </div>
            </>
          )}
        </section>
      )}

      {me.permissions.includes('ASSIGNMENTS_TAKE') && (
        <section className="panel">
          <div className="panel-heading"><div><p className="eyebrow">History</p><h3>My submissions</h3></div></div>
          {mine.data?.items.map(submission => <div key={submission.id} className="list-row"><div><strong>{submission.assignmentId}</strong><span>{submission.status}{submission.late ? ' · Late' : ''} · {submission.gradeStatus} · {submission.awardedPoints == null ? 'Not graded' : `${submission.awardedPoints} points`}</span></div></div>)}
        </section>
      )}
    </div>
  )
}
