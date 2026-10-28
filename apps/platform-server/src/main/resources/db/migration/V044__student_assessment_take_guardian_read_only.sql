-- Make the seeded Student/Guardian permission correction effective for existing tenants.
-- The Java role seeder keeps future/reconciled tenants aligned with these same grants.
INSERT INTO role_permission (role_id, permission_key)
SELECT rd.id, 'ASSESSMENTS_TAKE'
FROM role_definition rd
WHERE rd.system_managed = true
  AND rd.system_key = 'STUDENT'
ON CONFLICT (role_id, permission_key) DO NOTHING;

DELETE FROM role_permission rp
USING role_definition rd
WHERE rp.role_id = rd.id
  AND rd.system_managed = true
  AND rd.system_key = 'GUARDIAN'
  AND rp.permission_key IN (
      'ASSESSMENTS_TAKE',
      'ASSIGNMENTS_TAKE',
      'REVIEW_SUBMIT',
      'LIVE_CLASS_CHAT'
  );
