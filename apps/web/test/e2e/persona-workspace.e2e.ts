/**
 * Layer A E2E: every canonical Demo persona has a distinct identity label and
 * the exact navigation declared for its role.
 */
import { test, expect } from '@playwright/test'
import { signInToDemo, switchDemoUser } from './_helpers'

const personas = [
  { email: 'student@demo.uep', label: 'Student', name: 'Asha Sharma', nav: ['Today', 'My learning', 'Assignments', 'Results & feedback', 'Calendar & classes', 'Attendance', 'Inbox'] },
  { email: 'teacher@demo.uep', label: 'Teacher', name: 'Rahul Deshmukh', nav: ['Teaching overview', 'My classes', 'Learner roster', 'Submission queue', 'Attendance register', 'Course catalog', 'Class analytics', 'Inbox'] },
  { email: 'client@demo.uep', label: 'Client / Institution', name: 'Priya Kulkarni', nav: ['Organization overview', 'Programs & actions', 'People', 'Outcomes & reports', 'Delivery schedule', 'Operations', 'Inbox'] },
  { email: 'developer@demo.uep', label: 'Platform Developer', name: 'Dev Patel', nav: ['Platform overview', 'Integrations & webhooks', 'Client service operations'] },
  { email: 'product-maintainer@demo.uep', label: 'Product Maintainer', name: 'Mira Shah', nav: ['Product portfolio', 'Products & publishing', 'Product activity'] },
  { email: 'admin@demo.uep', label: 'Platform Administrator', name: 'Morgan Admin', nav: ['Platform overview', 'People & roles', 'Organizations', 'Course catalog', 'Academic records', 'Scheduling', 'Operations', 'Audit activity', 'Configuration'] },
  { email: 'guardian@demo.uep', label: 'Parent / Guardian', name: 'Nina Sharma', nav: ['Learner overview', 'Learner progress', 'Assignments & results', 'Calendar', 'Attendance', 'Inbox'] },
  { email: 'exam-controller@demo.uep', label: 'Exam Controller', name: 'Aarav Shah', nav: ['Exam overview', 'Exam schedule', 'Exam submissions', 'Exam activity'] },
  { email: 'finance-admin@demo.uep', label: 'Finance Administrator', name: 'Anaya Joshi', nav: ['Finance overview', 'Invoices & payments'] },
  { email: 'teaching-assistant@demo.uep', label: 'Teaching Assistant', name: 'Ishaan Patil', nav: ['Teaching overview', 'Assigned classes', 'Submission triage', 'Attendance', 'My activity', 'Inbox'] },
  { email: 'evaluator@demo.uep', label: 'Evaluator', name: 'Sara Khan', nav: ['Evaluation overview', 'Grading queue', 'Evaluation activity'] },
  { email: 'mentor@demo.uep', label: 'Mentor', name: 'Rohan Mehta', nav: ['Mentor overview', 'Mentee progress', 'Learning evidence', 'Mentoring activity', 'Inbox'] },
  { email: 'support@demo.uep', label: 'Support Operator', name: 'Alex Support', nav: ['Support overview', 'Support queue'] },
  { email: 'auditor@demo.uep', label: 'Auditor', name: 'Jordan Audit', nav: ['Audit overview', 'Audit log'] },
]

test.describe('Layer A: Persona workspace identification', () => {
  test('all 14 personas render their own identity label and exact navigation', async ({ page }) => {
    for (let index = 0; index < personas.length; index += 1) {
      const persona = personas[index]
      await signInToDemo(page, persona.email)
      await expect(page.locator('.workspace-header .eyebrow')).toContainText(persona.label)
      await expect(page.locator('.session-meta strong')).toHaveText(persona.name)
      const navigation = page.getByRole('navigation', { name: 'Primary navigation' })
      const navigationButtons = navigation.getByRole('button')
      await expect(navigationButtons).toHaveText(persona.nav)
      await expect(navigationButtons.first()).toHaveAttribute('aria-current', 'page')
      await navigationButtons.nth(1).click()
      await expect(navigationButtons.nth(1)).toHaveAttribute('aria-current', 'page')

      if (index < personas.length - 1) await switchDemoUser(page)
    }
  })
})
