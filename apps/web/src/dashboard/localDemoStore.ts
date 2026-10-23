import { localDemoData } from './localDemoData'

export type DemoStore = {
  assignments: typeof localDemoData.assignments
  submissions: typeof localDemoData.submissions
  students: typeof localDemoData.students
  sessions: typeof localDemoData.sessions
  organizations: typeof localDemoData.organizations
  products: typeof localDemoData.products
  releases: typeof localDemoData.releases
}

const STORAGE_KEY = 'uep-local-demo-store-v1'
const EVENT_NAME = 'uep-local-demo-store-updated'

function seed(): DemoStore {
  return structuredClone({
    assignments: localDemoData.assignments,
    submissions: localDemoData.submissions,
    students: localDemoData.students,
    sessions: localDemoData.sessions,
    organizations: localDemoData.organizations,
    products: localDemoData.products,
    releases: localDemoData.releases,
  })
}

export function loadDemoStore(): DemoStore {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    return raw ? (JSON.parse(raw) as DemoStore) : seed()
  } catch {
    return seed()
  }
}

export function saveDemoStore(store: DemoStore) {
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
    if (event.key === STORAGE_KEY) listener()
  }
  const onLocal = () => listener()
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
