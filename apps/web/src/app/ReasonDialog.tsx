import { useEffect, useRef, useState } from 'react'

/**
 * Small inline modal that collects a required free-text reason.
 * Replaces window.prompt so the flow stays keyboard reachable and stylable.
 * Minimal focus handling: autofocus the input, Escape cancels.
 */
export function ReasonDialog({ title, description, confirmLabel, onConfirm, onCancel }: {
  title: string
  description?: string
  confirmLabel: string
  onConfirm: (reason: string) => void
  onCancel: () => void
}) {
  const [reason, setReason] = useState('')
  const inputRef = useRef<HTMLInputElement>(null)

  useEffect(() => {
    inputRef.current?.focus()
  }, [])

  useEffect(() => {
    function onKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') onCancel()
    }
    document.addEventListener('keydown', onKeyDown)
    return () => document.removeEventListener('keydown', onKeyDown)
  }, [onCancel])

  return (
    <div className="dialog-scrim" role="presentation">
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby="reason-dialog-title"
        className="panel dialog-panel"
      >
        <div className="panel-heading">
          <div>
            <p className="eyebrow">{title}</p>
            <h3 id="reason-dialog-title">A reason is required</h3>
          </div>
        </div>
        {description && <p className="muted">{description}</p>}
        <label>
          Reason
          <input
            ref={inputRef}
            value={reason}
            required
            onChange={event => setReason(event.target.value)}
            onKeyDown={event => {
              if (event.key === 'Enter' && reason.trim()) onConfirm(reason.trim())
            }}
          />
        </label>
        <div className="form-actions">
          <button className="secondary-button" onClick={onCancel}>Cancel</button>
          <button className="primary-button" disabled={!reason.trim()} onClick={() => onConfirm(reason.trim())}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
