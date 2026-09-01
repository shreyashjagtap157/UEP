# Assignments and Gradebook — 0.8.0.0

Assignments are tenant-owned operational work items. They may be attached to multiple batches, but published work is not mutable back to draft. Submission attempts are immutable once submitted; resubmission creates a new attempt number. Late submission is retained explicitly rather than changing the original deadline.

Rubrics are stored independently from submissions and grading stores the rubric score payload with feedback. Assignment weights are expressed in basis points and a batch's assigned weights may not exceed 100%.

The Gradebook module consumes `AssignmentDirectory` instead of repositories from another module. It computes weighted assignment progress from the latest submission for the learner and never grants access outside the learner's active batch scope.

The web application is the first production client. Desktop and mobile clients are future API consumers and must not reimplement grading, submission-state, deadline, or authorization logic.
