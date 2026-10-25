import { useEffect, useState } from 'react'
import type { CoreLocalAccount } from './localDemoAccounts'
import type { LocalRole } from './localDemoData'
import {
  gradeAaravAssignment,
  markApiReleaseReady,
  publishDataEngineeringProduct,
  recordAaravAttendance,
  resetDemoStore,
  resolveOrganizationAction,
  submitAaravAssignment,
  subscribeDemoStore,
} from './LocalDemoWorkflowStore'

export function LocalDemoControls({ account }: { account: CoreLocalAccount }) {
  const [, setRevision] = useState(0)
  useEffect(() => subscribeDemoStore(() => setRevision(value => value + 1)), [])

  const actions = actionsFor(account.role)
  return (
    <section className="panel" aria-label="Cross-role demo workflow">
      <div className="panel-heading">
        <div>
          <p className="eyebrow">Live demo controls</p>
          <h3>Demonstrate shared state</h3>
        </div>
        <span className="count-badge">Shared</span>
      </div>
      <p className="muted small">These actions change the same local dataset used by every role. Open another role in a second tab to demonstrate propagation.</p>
      <div className="chip-row">
        {actions.map(action => <button key={action.label} className="primary-button" type="button" onClick={action.run}>{action.label}</button>)}
        {account.role === 'administrator' && <button className="text-button" type="button" onClick={resetDemoStore}>Reset demo data</button>}
      </div>
    </section>
  )
}

type DemoAction = { label: string; run: () => void }

function actionsFor(role: LocalRole): DemoAction[] {
  switch (role) {
    case 'student': return [
      { label: 'Submit Aarav assignment', run: submitAaravAssignment },
    ]
    case 'teacher': return [
      { label: 'Grade Aarav · 91%', run: () => gradeAaravAssignment(91) },
      { label: 'Record Aarav attendance', run: recordAaravAttendance },
    ]
    case 'client': return [
      { label: 'Resolve organization action', run: resolveOrganizationAction },
    ]
    case 'developer': return [
      { label: 'Publish Data Engineering product', run: publishDataEngineeringProduct },
      { label: 'Mark API release ready', run: markApiReleaseReady },
    ]
    case 'administrator': return [
      { label: 'Resolve organization action', run: resolveOrganizationAction },
      { label: 'Publish Data Engineering product', run: publishDataEngineeringProduct },
    ]
    case 'guardian': return []
  }
}
