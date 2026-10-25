import { DEMO_PROFILES } from './LocalDemoStore.ts'
import type { DemoProfile, DemoRole } from './LocalDemoStore.ts'

export type LocalAccount = {
  email: string
  role: DemoRole
  name: string
  profile: DemoProfile
}

// Keep the six familiar sign-in email aliases, but resolve them to canonical
// DEMO_PROFILES so every persona shares the same state and authorization rules.
const familiarLoginAliases: Partial<Record<DemoRole, string>> = {
  student: 'student@demo.uep',
  teacher: 'teacher@demo.uep',
  client: 'client@demo.uep',
  developer: 'developer@demo.uep',
  admin: 'admin@demo.uep',
  guardian: 'guardian@demo.uep',
}

export const demoAccounts: LocalAccount[] = DEMO_PROFILES.map(profile => ({
  email: familiarLoginAliases[profile.role] ?? profile.email,
  role: profile.role,
  name: profile.name,
  profile,
}))
