import { spawnSync } from 'node:child_process'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

// Vercel sets VERCEL=1 during its build. Local development and normal CI builds
// can still run without deployment credentials; build:vercel exercises the
// same gate explicitly in CI.
if (process.env.VERCEL !== '1') process.exit(0)

const here = dirname(fileURLToPath(import.meta.url))
const verifier = resolve(here, 'verify-deployment-env.mjs')
const result = spawnSync(process.execPath, [verifier], { stdio: 'inherit', env: process.env })
if (result.error) {
  console.error('Unable to run the Vercel deployment environment verifier:', result.error.message)
  process.exit(1)
}
process.exit(result.status ?? 1)
