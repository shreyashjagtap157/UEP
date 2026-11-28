# Implementation traceability audit — Messaging

**Audit date:** 2026-11-28  
**Source baseline:** [`b5d7034`](https://github.com/shreyashjagtap157/UEP/commit/b5d7034f13d83022115121c4b21848e68ed91a54)  
**Branch / release:** `main` / `0.15.0.0-SNAPSHOT`  
**CI evidence:** [UEP CI #1385 — 7/7 jobs passed](https://github.com/shreyashjagtap157/UEP/actions/runs/46112848647)

## 1. Scope and evidence vocabulary

This is a focused audit of messaging, not a platform-wide conformance certification. It compares master plan §38, API contract, implementation, migrations, tests and exact-head CI.

- **Specified:** required by plan, architecture, API contract or accepted decision.
- **Implemented:** behavior exists in the reviewed code/schema.
- **Covered:** automated tests exercise the behavior and relevant denied paths.
- **CI-verified:** applicable tests/gates passed on the exact reviewed commit.
- **Environment-qualified:** exercised against the target operational/provider environment.
- **Open/not established:** available evidence does not prove the requirement or the requirement is not fully specified.

The reviewed repository tree contained 522 production Java files, 83 Java test files, 50 Flyway migrations, 16 architecture notes and 13 ADRs. The roadmap is `docs/MASTER_IMPLEMENTATION_PLAN.md` (102 numbered sections), with ADRs under `docs/adr/`, domain notes under `docs/architecture/` and API contract `docs/api/openapi.yaml`. No top-level `spec/` directory existed at this baseline. These are structure counts, not completeness metrics.

## 2. Requirement-to-implementation matrix

| Requirement | Source evidence | Tests / validation | Assessment |
| --- | --- | --- | --- |
| Tenant-scoped direct/group conversations | `MessagingService`, conversation/participant entities and repositories; tenant from authenticated context | `MessagingApiBoundaryIntegrationTest`, `MessagingPersistenceIntegrityIntegrationTest` | Implemented; tested; PostgreSQL CI-verified |
| Active same-tenant participant integrity | Membership resolution in service; V035/V036 tenant/member integrity | Foreign-tenant participant non-disclosure 404; inactive participant 403; creator/participant persistence constraints | Implemented; negative paths covered |
| Conversation and message pagination | Controller GET paths and service page methods; OpenAPI page schemas | API boundary workflows and contract build | Implemented; pagination extremes/scale remain |
| Message send and notification intent | Send persists message/delivery and recipient notification intent transactionally; sender excluded and blocks considered | Test asserts non-sender intent, no sender intent and no message body in notification payload | Implemented; transaction path tested |
| Recipient delivery/read receipts | `MessageDelivery` and repository implement `PENDING → DELIVERED → READ`; service enforces receipt visibility | Group receipt and recipient privacy tests | Implemented; application semantics only |
| Blocking | Block/unblock service and API paths | `blockingPreventsDirectMessagesUntilTheBlockIsRemoved` | Implemented; tested |
| Reporting/moderation | Report evidence snapshots, moderator queue, resolve/redaction actions | `reportCanBeSubmittedAndModeratorActionRedactsTheReportedMessage` | Implemented; core flow tested |
| Tenant policy | Enablement, student-to-student restriction, retention and quotas; V041 default-policy seed | Restriction/disable and new-tenant default policy tests | Implemented; tested |
| Per-membership quotas | PostgreSQL rate-window rows and atomic update by tenant + membership | Concurrent send quota, concurrent report quota and cross-tenant membership-isolation tests | Single-instance concurrency covered; multi-instance not established |
| Retention/evidence | `MessagingRetentionWorker` and `MessagingRetentionService`; reported message evidence receives longer retention | `retentionPreservesReportedEvidenceUntilItsLongerRetentionExpires` | Implemented; basic policy tested; no legal-hold lifecycle |
| Edit/delete/versioning | Sender-scope checks, optimistic version checks, soft-delete and audit | `messageEditRequiresExplicitOptimisticVersion` and boundary tests | Implemented; basic conflict path tested |
| API contract | `docs/api/openapi.yaml` covers conversation, message, read, policy, block, receipt and moderation paths | OpenAPI in source; CI production-contract build | Documented; no comprehensive generated source/contract parity gate |
| Web client | `apps/web/src/platform/api-messaging.ts` plus messaging workspace | Web tests/build pass; Windows presentation job does not prove every live messaging workflow | Implemented; broader live E2E needed |

### Receipt semantics

`DELIVERED` means the recipient fetched the message through the UEP messaging API. `READ` means the server recorded the participant's read action. These do **not** mean an external email, push, SMS or other transport provider accepted or delivered a notification.

## 3. Concrete implementation inventory

### API paths

The OpenAPI contract declares:
- `/messaging/conversations`;
- `/messaging/conversations/{conversationId}/messages`;
- `/messaging/conversations/{conversationId}/read`;
- `/messaging/messages/{messageId}`;
- `/messaging/messages/{messageId}/delivery`;
- `/messaging/messages/{messageId}/reports`;
- `/messaging/blocks` and `/messaging/blocks/{membershipId}`;
- `/messaging/policy`;
- `/messaging/reports` and `/messaging/reports/{reportId}/resolve`.

The controller is `MessagingController.java`; orchestration and authorization are in `MessagingService.java`.

### Database evolution

V033/V034 introduce messaging persistence/permissions; V035/V036 strengthen tenant/member integrity; V037 adds blocking; V038 adds reports/moderation/evidence; V040 adds tenant policy/rate windows; V041 seeds new-tenant default policy. PostgreSQL 17.11 and 18.4 migration and least-privilege checks passed in CI #1385.

### Test inventory

- `MessagingApiBoundaryIntegrationTest` covers foreign-tenant rejection without identity leakage, sender-excluded notification intent, group receipt privacy, blocks, reports/moderation/redaction, policy restrictions/disablement, message and report quotas, retention of reported evidence, default policy creation, optimistic versioning and inactive membership rejection.
- `MessagingPersistenceIntegrityIntegrationTest` covers same-tenant creator/participants, sender participation and a valid tenant-scoped write.
- `MessagingRateLimitConcurrencyIntegrationTest` covers concurrent message quotas, concurrent report quotas and isolation for one identity using memberships in two tenants.

The parallel quota tests execute against one application deployment and shared PostgreSQL database. They do not demonstrate coordination across independently running application nodes.

## 4. Classification and open requirements

### Implemented and CI-verified

- Tenant-scoped records and database integrity for conversation creator, participants and message sender.
- Participant and sender authorization, moderator gating and stable API error behavior.
- Message/notification intent transaction path, sender exclusion and block-aware audience filtering.
- Receipt visibility/state, block/unblock, reports/moderation and report evidence preservation.
- Tenant policy, default policy, single-instance DB-backed quotas and basic retention.
- PostgreSQL 17.11/18.4 migration and privilege checks in CI #1385.

### Implemented but insufficiently qualified

- Every API/client workflow, pagination edge, cross-tenant fuzz path and authorization-bypass variant.
- Concurrent edit/delete/report/read/resolve race behavior.
- Retention worker restart/retry with prolonged failures.
- Quota behavior across independently running application instances and connection pools.

### Not implemented or not established

- Real email/push/SMS messaging provider delivery, provider retries/idempotency/backoff, dead-letter inspection/replay and transport delivery evidence.
- WebSocket/SSE/push transport, reconnect/ordering/offline behavior and its SLO.
- Legal hold/litigation freeze, institution-specific preservation overrides, export and defensible deletion lifecycle.
- Production-scale abuse/fuzz testing beyond existing quota/block/report coverage.

## 5. Risks and specification questions

1. **Multi-node quotas:** run bursts through two independent Spring instances with separate pools against one PostgreSQL database.
2. **Retention/legal hold:** current retention preserves reported messages until the longer report period; it is not legal hold. Define authority, freeze/unfreeze permission, audit, export, expiry and tenant-policy precedence before implementing.
3. **Provider delivery:** decide provider classes and define retry/idempotency, provider acceptance vs recipient delivery, PII retention/redaction and operator controls.
4. **Realtime:** decide polling, WebSocket, SSE or push; define ordering, reconnect and unread consistency. Do not conflate message receipts with provider delivery.
5. **Institutional audit:** define retention, supervisor access and audit policy for teacher/student, administrator/user and support/tenant communications.

## 6. Next execution milestone — boundary fuzzing and multi-instance qualification

Acceptance criteria:
- Exercise sender, other participant, nonparticipant, inactive membership, moderator, same-user second membership and foreign-tenant actors with real and guessed conversation/message/report IDs. Assert specified 401/403/404/409 semantics, no foreign-tenant disclosure and no writes/side effects for denied operations.
- Synchronize message/report bursts through two separately running service instances against one PostgreSQL database. Enforce tenant/membership quota ceilings and assert rejected requests create no message, delivery, report or notification intent.
- Race edit/delete/report/read/resolve and verify optimistic locking, evidence snapshots, receipt consistency and audit.
- Exercise retention worker restart/retry for reported/unreported records and tenant cutoffs. Do not invent legal-hold semantics before requirements are decided.
- Record provider and realtime transport choices as explicit architecture decisions before building adapters.
- Run targeted checks on PostgreSQL 17.11 and 18.4; preserve Windows Real/Demo, security/privacy and policy gates.

## 7. Conclusion

Messaging is substantially implemented and has meaningful tenant-boundary, persistence, authorization, moderation, concurrency-quota and retention coverage. CI verifies the covered tests and PostgreSQL migration/privilege contracts on GitHub runners. It does not yet prove multi-node coordination, external delivery, legal-hold behavior, realtime transport, broad abuse resistance or production operations. The next high-value work is focused qualification at these boundaries, not cosmetic refactoring.
