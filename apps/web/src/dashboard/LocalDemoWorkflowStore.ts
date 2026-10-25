import { localDemoData } from './localDemoData.ts'

type Widen<T> = T extends string ? string : T extends number ? number : T extends boolean ? boolean : T extends readonly (infer U)[] ? Widen<U>[] : T extends object ? { -readonly [K in keyof T]: Widen<T[K]> } : T

export type DemoStore = Widen<{
  assignments: typeof localDemoData.assignments
  submissions: typeof localDemoData.submissions
  students: typeof localDemoData.students
  sessions: typeof localDemoData.sessions
  organizations: typeof localDemoData.organizations
  products: typeof localDemoData.products
  releases: typeof localDemoData.releases
}>

const STORAGE_KEY = 'uep-local-demo-store-v2'
const LEGACY_STORAGE_KEY = 'uep-local-demo-store-v1'
const EVENT_NAME = 'uep-local-demo-store-updated'

type MutableDemoData = {
  assignments: DemoStore['assignments']
  submissions: DemoStore['submissions']
  students: DemoStore['students']
  sessions: DemoStore['sessions']
  organizations: DemoStore['organizations']
  products: DemoStore['products']
  releases: DemoStore['releases']
}

// Snapshot pristine module data once. The dashboard-facing arrays are intentionally mutable,
// so deriving reset state from localDemoData after actions would preserve the mutated values.
const INITIAL_DEMO_SEED = structuredClone({
  assignments: localDemoData.assignments,
  submissions: localDemoData.submissions,
  students: localDemoData.students,
  sessions: localDemoData.sessions,
  organizations: localDemoData.organizations,
  products: localDemoData.products,
  releases: localDemoData.releases,
}) as unknown as DemoStore

function seed(): DemoStore {
  return structuredClone(INITIAL_DEMO_SEED) as DemoStore
}

function applyToDashboard(store: DemoStore) {
  const target = localDemoData as unknown as MutableDemoData
  target.assignments = store.assignments
  target.submissions = store.submissions
  target.students = store.students
  target.sessions = store.sessions
  target.organizations = store.organizations
  target.products = store.products
  target.releases = store.releases
}

function mergeStoredRows<T extends { id: string }>(defaults: T[], stored: unknown): T[] {
  const rows: Array<Partial<T> & { id: string }> = Array.isArray(stored)
    ? stored.filter((value): value is Partial<T> & { id: string } => typeof value === 'object' && value !== null && typeof (value as { id?: unknown }).id === 'string')
    : []
  return [
    ...defaults.map(item => {
      const saved = rows.find(row => row.id === item.id)
      return saved ? ({ ...item, ...saved } as T) : structuredClone(item)
    }),
    ...rows.filter(row => !defaults.some(item => item.id === row.id)).map(row => ({ ...row } as T)),
  ]
}

function normalizeStoredStore(value: unknown): DemoStore {
  const defaults = seed()
  const stored = typeof value === 'object' && value !== null ? value as Partial<DemoStore> : {}
  return {
    assignments: mergeStoredRows(defaults.assignments, stored.assignments),
    submissions: mergeStoredRows(defaults.submissions, stored.submissions),
    students: mergeStoredRows(defaults.students, stored.students),
    sessions: mergeStoredRows(defaults.sessions, stored.sessions),
    organizations: mergeStoredRows(defaults.organizations, stored.organizations),
    products: mergeStoredRows(defaults.products, stored.products),
    releases: mergeStoredRows(defaults.releases, stored.releases),
  }
}

export function loadDemoStore(): DemoStore {
  try {
    const raw = localStorage.getItem(STORAGE_KEY) ?? localStorage.getItem(LEGACY_STORAGE_KEY)
    const store = raw ? normalizeStoredStore(JSON.parse(raw)) : seed()
    applyToDashboard(store)
    localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
    return store
  } catch {
    const store = seed()
    applyToDashboard(store)
    try { localStorage.setItem(STORAGE_KEY, JSON.stringify(store)) } catch { /* storage may be unavailable */ }
    return store
  }
}
export function saveDemoStore(store: DemoStore) {
  applyToDashboard(store)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(store))
  window.dispatchEvent(new CustomEvent(EVENT_NAME))
}

export function resetDemoStore() {
  const store = seed()
  saveDemoStore(store)
  return store
}

export function subscribeDemoStore(listener: () => void) {
  const onStorage = (event: StorageEvent) => {
    if (event.key === STORAGE_KEY || event.key === LEGACY_STORAGE_KEY) {
      loadDemoStore()
      listener()
    }
  }
  const onLocal = () => {
    loadDemoStore()
    listener()
  }
  window.addEventListener('storage', onStorage)
  window.addEventListener(EVENT_NAME, onLocal)
  return () => {
    window.removeEventListener('storage', onStorage)
    window.removeEventListener(EVENT_NAME, onLocal)
  }
}

export function updateDemoStore(mutator: (store: DemoStore) => void) {
  const store = loadDemoStore()
  mutator(store)
  saveDemoStore(store)
  return store
}

export function submitAaravAssignment() {
  updateDemoStore(store => {
    const assignment = store.assignments.find(item => item.id === 'assignment-001')
    if (assignment) assignment.status = 'Submitted'
    const existing = store.submissions.find(item => item.id === 'submission-001')
    if (existing) existing.status = 'Needs grading'
    else store.submissions.push({ id: 'submission-001', student: 'Aarav Mehta', assignment: 'REST API architecture review', submitted: 'Just now', status: 'Needs grading' })
  })
}

export function gradeAaravAssignment(score = 91) {
  updateDemoStore(store => {
    const assignment = store.assignments.find(item => item.id === 'assignment-001')
    if (assignment) {
      assignment.status = 'Graded'
      assignment.score = score
    }
    const submission = store.submissions.find(item => item.id === 'submission-001')
    if (submission) {
      submission.status = 'Graded'
      submission.score = score
    }
  })
}

export function recordAaravAttendance() {
  updateDemoStore(store => {
    const learner = store.students.find(item => item.id === 'stu-001')
    if (learner) learner.attendance = Math.min(100, learner.attendance + 1)
  })
}

export function resolveOrganizationAction() {
  updateDemoStore(store => {
    const organization = store.organizations.find(item => item.id === 'org-001')
    if (organization) organization.actions = Math.max(0, organization.actions - 1)
  })
}

export function publishDataEngineeringProduct() {
  updateDemoStore(store => {
    const product = store.products.find(item => item.id === 'product-002')
    if (product) product.status = 'Published'
  })
}

export function markApiReleaseReady() {
  updateDemoStore(store => {
    const release = store.releases.find(item => item.id === 'release-002')
    if (release) {
      release.status = 'Ready for review'
      release.passed = release.checks
    }
  })
}
