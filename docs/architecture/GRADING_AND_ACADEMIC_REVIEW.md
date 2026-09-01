# Grading and Academic Review — 0.7.0.0

## Purpose

This milestone adds immutable grading history and evidence-based academic review on top of Assessment Core. The first-class production client remains the web application; native clients consume the same stable APIs later.

## Grading model

A submitted attempt can produce append-only `GradeRevision` records. Each revision contains source, actor, scores, rationale, and immutable `GradeItem` snapshots. Published revisions supersede earlier published revisions instead of mutating history.

System objective grading supports the question types implemented by Assessment Core. Unanswered objective/text responses receive zero rather than a negative penalty; answered incorrect responses may receive the configured negative mark. File-submission questions remain manual.

Teacher overrides create a new revision and preserve the previous system score and teacher score for traceability.

## Academic review

A review case may challenge an entire grade or a specific answer. Participants can discuss the case and propose answer revisions. Review managers can accept/reject revisions, analyze projected score impact, and execute a reconciliation regrade. Review records are tenant-scoped and auditable.

## Security boundaries

Learners can view only published grades for their own submitted attempts. Graders and review managers use tenant-scoped authorization. Answer references are checked against the reviewed attempt before impact analysis or revision proposals.

## Native-client contract

No grading or review rule is implemented only in React. The web client is the first UI, while server APIs remain authoritative for score calculation, publication, review lifecycle, revision acceptance, and regrading.
