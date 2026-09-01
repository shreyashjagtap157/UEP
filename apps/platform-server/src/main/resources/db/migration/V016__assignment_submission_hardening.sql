ALTER TABLE assignment_submission ADD COLUMN IF NOT EXISTS rubric_scores_json text NOT NULL DEFAULT '{}';
ALTER TABLE assignment_submission ADD COLUMN IF NOT EXISTS late boolean NOT NULL DEFAULT false;
