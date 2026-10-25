import { DEMO_PROFILES } from './LocalDemoStore.ts'
import type { DemoProfile, DemoRole } from './LocalDemoStore.ts'
import type { LocalRole } from './localDemoData.ts'

export type CoreLocalAccount = {
  email: string
  role: LocalRole
  name: string
}

export type SpecialistLocalAccount = {
  email: string
  role: DemoRole
  name: string
  profile: DemoProfile
}

export type LocalAccount = CoreLocalAccount | SpecialistLocalAccount

const coreProfileRoles = new Set<DemoRole>([
  'student', 'teacher', 'client', 'developer', 'admin', 'guardian',
])

const coreAccounts: CoreLocalAccount[] = [
  { email: 'student@demo.uep', role: 'student', name: 'Aarav Mehta' },
  { email: 'teacher@demo.uep', role: 'teacher', name: 'Priya Nair' },
  { email: 'client@demo.uep', role: 'client', name: 'Pune Technology Academy' },
  { email: 'developer@demo.uep', role: 'developer', name: 'UEP Product Team' },
  { email: 'admin@demo.uep', role: 'administrator', name: 'Platform Administrator' },
  { email: 'guardian@demo.uep', role: 'guardian', name: 'Meera Mehta' },
]

const specialistAccounts: SpecialistLocalAccount[] = DEMO_PROFILES
  .filter(profile => !coreProfileRoles.has(profile.role))
  .map(profile => ({
    email: profile.email,
    role: profile.role,
    name: profile.name,
    profile,
  }))

export const demoAccounts: LocalAccount[] = [...coreAccounts, ...specialistAccounts]

export function isCoreLocalAccount(account: LocalAccount): account is CoreLocalAccount {
  return !('profile' in account)
}
