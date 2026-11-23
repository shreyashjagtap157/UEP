import { DEMO_PROFILES } from './LocalDemoStore.ts'
import type { DemoProfile, DemoRole } from './LocalDemoStore.ts'

export type LocalAccount = {
  email: string
  role: DemoRole
  name: string
  profile: DemoProfile
}

// Keep predictable role-specific sign-in aliases, but resolve them to canonical
// DEMO_PROFILES so every persona shares the same state and authorization rules.
const familiarLoginAliases: Partial<Record<DemoRole, string>> = {
  student: 'student@demo.uep',
  teacher: 'teacher@demo.uep',
  client: 'client@demo.uep',
  developer: 'developer@demo.uep',
  'product-maintainer': 'product-maintainer@demo.uep',
  admin: 'admin@demo.uep',
  guardian: 'guardian@demo.uep',
  'exam-controller': 'exam-controller@demo.uep',
  'finance-admin': 'finance-admin@demo.uep',
  'teaching-assistant': 'teaching-assistant@demo.uep',
  evaluator: 'evaluator@demo.uep',
  mentor: 'mentor@demo.uep',
  support: 'support@demo.uep',
  auditor: 'auditor@demo.uep',
}

export const demoAccounts: LocalAccount[] = DEMO_PROFILES.map(profile => ({
  email: familiarLoginAliases[profile.role] ?? profile.email,
  role: profile.role,
  name: profile.name,
  profile,
}))
