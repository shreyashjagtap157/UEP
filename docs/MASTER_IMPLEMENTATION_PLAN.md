# Master Implementation Plan

## Universal Education, Training and Learning Operations Platform

### Target

Build a production-grade, commercially licensable, multi-tenant education and training platform with the **web application completed first**, while preserving first-class architectural support for:

- Windows desktop
- Linux desktop
- macOS desktop
- Android
- iOS/iPadOS
- Progressive Web App
- institutional kiosk clients
- dedicated enterprise deployments
- on-premise deployments
- private-cloud deployments
- public SaaS
- public/enterprise APIs
- third-party integrations

The finished platform must combine:

- institute/organization administration
- teachers and faculty management
- students and enrollment
- programs/courses/subjects/modules
- batches/cohorts
- scheduling and timetables
- live classes
- classroom recordings
- attendance and online-presence tracking
- class notes and learning resources
- question banks
- MCQ and other examinations
- automatic and manual grading
- grading explanations
- answer disputes and counterarguments
- academic reconciliation/review
- assignments/projects
- announcements
- notifications
- messaging
- results and gradebooks
- analytics
- competency tracking
- certificates/credentials
- payments and student fees
- commercial platform licensing
- white-labeling
- security/compliance
- integrations
- optional AI capabilities

The system must remain affordable enough that small institutions can operate it without enterprise-cloud infrastructure while still being architecturally capable of scaling to large organizations.

---

# 1. Fundamental engineering principles

The project shall follow these non-negotiable principles.

## 1.1 Web-first, API-first

The first production user interface is the web application.

However:

```
Browser UI
Desktop UI
Mobile UI
External integration
        │
        ▼
Stable Platform APIs
        │
        ▼
Shared Business Platform

```

No important academic or licensing rule lives exclusively in the browser.

The server remains authoritative for:

- authorization
- exam state
- grading
- results
- attendance
- entitlement enforcement
- resource permissions
- payment state
- recording access
- audit history

This makes future native applications feasible without duplicating business logic.

---

# 2. Architecture style

Use a **domain-driven modular monolith** initially rather than microservices.

Spring Modulith is specifically designed for domain-oriented modular Spring applications and currently has a stable 2.1 line.

Initial application:

```
platform-server
│
├── identity
├── tenancy
├── licensing
├── organization
├── people
├── academics
├── enrollment
├── curriculum
├── scheduling
├── liveclass
├── presence
├── recording
├── content
├── assessment
├── questionbank
├── grading
├── academicreview
├── assignment
├── gradebook
├── communication
├── notification
├── competency
├── credential
├── finance
├── analytics
├── workflow
├── integration
├── storage
├── audit
└── platformops

```

Each module:

- owns its tables/model;
- exposes explicit interfaces;
- cannot directly use another module's repository;
- publishes domain events;
- has module-specific tests;
- has documented invariants.

Extraction into independent services occurs only when load, isolation, compliance, or operational requirements justify it.

---

# 3. Separate control plane from media plane

This is essential.

```
                 PLATFORM
                    │
          ┌─────────┴─────────┐
          ▼                   ▼
   CONTROL PLANE          MEDIA PLANE
       
Java/Spring              WebRTC SFU
PostgreSQL               TURN
Authentication           Recording
Courses                   Transcoding
Exams                     Media routing
Payments                  Captions
Licensing                 Streaming
Analytics

```

Do not run high-load video processing inside Spring application processes.

The Java application controls meetings.

The media platform transports media.

---

# 4. Recommended technology baseline

## Backend

```
Java 25 LTS
Spring Boot 4.1.x
Spring Framework 7.x
Spring Modulith 2.1.x
Spring Security
Hibernate/JPA selectively
jOOQ or native SQL for performance-sensitive reporting
Flyway or Liquibase
Gradle or Maven

```

Spring Boot 4.1 currently supports Java versions up through Java 26.

## Database

```
PostgreSQL 18

```

PostgreSQL 18 introduced native asynchronous I/O improvements, skip-scan support, `uuidv7()`, temporal constraints, and other useful database enhancements.

Use UUIDv7-style ordered identifiers for externally visible entities where appropriate.

## Cache / short-lived coordination

```
Valkey 9.1

```

Valkey 9.1 currently carries maintenance support through 2029 and extended security support through 2031.

Valkey must remain optional.

The product should work without it in compact deployments, at reduced scale.

## Identity

Preferred production solution:

```
Keycloak 26.7+

```

with:

```
OIDC
OAuth
SAML
WebAuthn/passkeys
MFA
enterprise federation
future SCIM

```

Keycloak 26.7 is the current documented release and includes a preview SCIM provisioning API plus additional enterprise-authentication enhancements.

## Web frontend

```
React
TypeScript
Vite-based application build
TanStack Query-style server-state layer
accessible component primitives
design-token based UI system

```

Do not share business rules by copying Java logic into TypeScript.

Share:

- OpenAPI contracts;
- generated clients;
- enum/schema contracts where safe.

## Public marketing site

Keep separate from authenticated application.

This permits:

- SSR/static optimization;
- public SEO;
- documentation;
- pricing;
- customer signup;

without complicating the authenticated SPA.

---

# 5. Real-time media platform

I recommend making the media layer pluggable.

```
MeetingProvider
RecordingProvider
StreamingProvider
TranscriptionProvider

```

Initial preferred implementation:

```
LiveKit self-hosted

```

because it currently supports self-hosted WebRTC, recording/egress, end-to-end encryption support, web/mobile/native SDKs, webhooks, and distributed deployments. Its documentation describes self-hosted deployments ranging from VMs to Kubernetes and multi-region arrangements.

A Java/Kotlin server SDK is available for server integration, while React, Swift/macOS, Android, Flutter, React Native, C++ and other client paths exist, which is particularly valuable for future deployments beyond the browser.

Maintain an alternative adapter for Jitsi.

Jitsi remains a viable open-source conferencing backend and offers web embedding APIs, but conventional Jibri recording currently needs one Jibri system per simultaneous recording and substantially more CPU/RAM than the base Jitsi server. That makes heavy recording concurrency operationally expensive.

Therefore:

```
Media abstraction
      │
      ├── LiveKit
      ├── Jitsi
      ├── External provider
      └── Future provider

```

Never tightly couple course/session tables to LiveKit room IDs.

---

# 6. Storage architecture

This is one of the most important cost-reduction features.

Create:

```
StorageProvider
│
├── LocalFilesystemProvider
├── NetworkFilesystemProvider
├── S3Provider
├── GoogleDriveProvider
├── GoogleCloudStorageProvider
├── AzureBlobProvider
├── ClientManagedProvider
└── Future providers

```

Each tenant can select storage independently.

---

# 7. Bring Your Own Storage

Make **BYOS — Bring Your Own Storage** a first-class product capability.

This can drastically lower operating costs for both you and the customer.

Example tenant configuration:

```
ABC Institute

Primary documents:
Google Drive

Recordings:
Google Shared Drive

Temporary processing:
Local SSD

Backups:
S3-compatible NAS

```

or:

```
XYZ College

Everything:
on-premise NAS

```

or:

```
Enterprise Customer

Hot recordings:
S3

Archive:
Azure Blob Archive

```

---

# 8. Google Drive recording storage

Google Drive must be implemented as a genuine storage adapter rather than a hyperlink integration.

The Drive API supports resumable uploads specifically so interrupted large uploads can resume instead of restarting.

Preferred flow:

```
Class ends
   ↓
Recording completes
   ↓
Temporary local object
   ↓
SHA-256 verification
   ↓
Google Drive resumable upload
   ↓
Verify remote metadata
   ↓
Record provider object ID
   ↓
Delete temporary file

```

Use narrow OAuth permissions wherever practical.

Google recommends `drive.file` where possible because it limits the application to files specifically created/shared through the application instead of granting broad access to an entire Drive.

For institution-managed storage, support Shared Drives.

A service account cannot own normal Drive files because service accounts have no Drive storage quota; Google specifically directs service-account use toward Shared Drives or delegated human accounts.

Model:

```
TenantStorageConnection
├── provider = GOOGLE_DRIVE
├── driveId
├── folderId
├── encrypted credentials/token reference
├── retention policy
├── enabled
└── health state

```

---

# 9. Google Drive must not become the database

Store only the object in Drive.

Platform metadata stays in PostgreSQL:

```
recording_id
tenant_id
class_session_id
storage_provider
provider_object_id
content_hash
mime_type
size
duration
created_at
retention_state
availability

```

Never depend on folder names for relational meaning.

---

# 10. Drive capacity considerations

Google Drive is excellent for client-owned inexpensive storage but should not be treated as unlimited infrastructure.

Google currently documents:

- 750 GB per Workspace user per 24-hour upload window;
- maximum individual upload size of 5 TB;
- API quota accounting;
- 500,000 items per Shared Drive.

Therefore use it as:

```
excellent:
institution-owned archival storage
class recordings
documents
low/moderate concurrency playback

not ideal:
massive public video CDN
extreme playback concurrency
millions of tiny platform objects

```

For high-volume customers, object storage/CDN should be preferred.

---

# 11. Physical disk/NAS storage

Support:

```
local disk
external hard drive
NAS
NFS
SMB
mounted distributed filesystem

```

through a filesystem adapter.

Write strategy:

```
recording.part
     ↓
stream output
     ↓
fsync
     ↓
checksum
     ↓
atomic rename
     ↓
recording.mp4

```

Never expose filesystem paths directly to users.

Store opaque storage IDs.

Use configurable:

```
disk quota
minimum free-space threshold
retention
archive path
health checks
read/write probes

```

If disk free space falls below the safety threshold:

```
stop new recording allocation
notify administrators
continue critical academic services

```

Do not permit recordings to consume the filesystem until PostgreSQL fails.

---

# 12. Hybrid recording storage

For excellent UX at low cost:

```
HOT CACHE
recent recordings
local/object storage
7–30 days
          ↓
ARCHIVE
Google Drive/NAS/cold object storage

```

When a student requests an archived recording:

```
authorize
   ↓
fetch/cache if needed
   ↓
stream

```

Frequently accessed recordings stay cached.

Rare recordings remain inexpensive.

---

# 13. Recording modes

Offer several modes.

### Economy

```
teacher/presentation focused
720p
moderate bitrate

```

### Balanced

```
composite classroom
720p

```

### High Quality

```
1080p where viable

```

### Source Archive

Store selected source tracks where possible without unnecessary transcoding.

LiveKit's egress system supports composite recording, HLS, MP4, image outputs, and direct track export; track egress can export media without transcoding.

The tenant chooses policy.

Avoid automatically recording every class at maximum resolution.

That is how a small LMS turns into a storage company.

---

# 14. Tenant architecture

Every client organization is a tenant.

```
Platform
│
├── Tenant A
│   ├── Branches
│   ├── Users
│   ├── Courses
│   └── Data
│
├── Tenant B
│
└── Tenant C

```

Initial SaaS strategy:

```
shared application
shared PostgreSQL cluster
tenant_id on tenant-owned data
strict tenant filtering
database-level defensive controls

```

Enterprise options later:

```
Dedicated database
Dedicated storage
Dedicated deployment
On-premise

```

---

# 15. User model

Never model people as mutually exclusive user types.

Use:

```
User
   │
   ├── TenantMembership
   │
   ├── RoleAssignment
   │
   └── Enrollment

```

One person can simultaneously be:

- teacher;
- administrator;
- learner;
- evaluator.

---

# 16. Authorization

Implement:

```
RBAC
+
resource/context policy
+
tenant boundary
+
license entitlement

```

Example:

```
Can user grade attempt?

must satisfy:

authenticated
AND tenant matches
AND GRADING permission
AND assigned course/batch
AND assessment state allows grading
AND tenant assessment entitlement enabled

```

Never rely on UI visibility as authorization.

---

# 17. Platform roles

Default roles:

```
Platform Super Administrator

Organization Owner
Organization Administrator
Branch Administrator
Academic Administrator
Exam Controller
Finance Administrator

Teacher
Evaluator
Teaching Assistant
Mentor

Student
Guardian

Support Operator
Auditor

```

Customers can define custom roles by combining permissions.

---

# 18. Academic model

Use a flexible hierarchy.

```
Organization
  ↓
Academic Period
  ↓
Program
  ↓
Course
  ↓
Subject
  ↓
Module
  ↓
Lesson

```

Alongside:

```
Batch / Cohort
Section
Enrollment
Teacher Assignment

```

Not every level is mandatory.

This permits:

### Coaching institute

```
Course → Batch

```

### School

```
Grade → Section → Subject

```

### University

```
Program → Semester → Course → Section

```

### Corporate training

```
Program → Module → Activity

```

---

# 19. Scheduling module

Implement:

```
one-time classes
recurring classes
offline classes
online classes
hybrid classes
exams
assignments
meetings
events
holidays
appointments

```

Support:

```
time zones
recurrence
exceptions
rescheduling
cancellation
substitute teachers
rooms/resources
conflict detection

```

Conflict detection must identify:

- teacher conflicts;
- batch conflicts;
- room conflicts;
- exam conflicts;
- institutional holidays.

---

# 20. Live classroom

Implement:

```
audio
video
screen sharing
presentation sharing
teacher controls
co-hosts
hand raise
chat
Q&A
polls
whiteboard
participant list
mute controls
waiting room
removal
recording controls
connection-quality indication
low-bandwidth mode

```

The Java backend issues short-lived meeting authorization.

Clients never receive permanent media-administrator credentials.

---

# 21. Presence/attendance

Trechto's missing attendance capability becomes a major upgrade.

Record presence as event intervals:

```
JOIN
LEAVE
DISCONNECT
RECONNECT

```

For example:

```
10:00 JOIN
10:22 DISCONNECT
10:27 RECONNECT
11:02 LEAVE

```

Calculate:

```
session duration
connected duration
late duration
early-leave duration
percentage

```

Attendance modes:

```
DISABLED
MANUAL
AUTOMATIC
AUTOMATIC_WITH_OVERRIDE
HYBRID

```

Teacher overrides require:

```
old status
new status
reason
actor
timestamp

```

Do not call online presence "attention."

---

# 22. Offline classroom attendance

Support future adapters for:

- manual attendance;
- rotating QR;
- NFC;
- RFID;
- external biometric systems;
- kiosk;
- import/API.

The attendance engine receives normalized observations regardless of source.

---

# 23. Learning resources / class notes

Class notes can contain:

```
rich text
plain text
Markdown
PDF
DOC/DOCX
PPT/PPTX
spreadsheets
images
audio
video
archives
source code
external URL
other allowed media

```

Model:

```
LearningResource
├── owner
├── course
├── module
├── lesson
├── class session
├── version
├── storage object
├── MIME type
├── hash
├── language
├── release time
├── expiration
├── download policy
└── visibility

```

Resources must be versioned.

---

# 24. Upload subsystem

Use streamed and resumable uploads.

Never load a 4 GB class recording into JVM heap.

Flow:

```
initiate
 ↓
chunk/upload
 ↓
progress
 ↓
resume
 ↓
hash validation
 ↓
malware scan
 ↓
finalize

```

---

# 25. Question bank

Make this a core strategic subsystem.

Each question:

```
Question
├── stable ID
├── version
├── question type
├── language
├── difficulty
├── course
├── subject
├── topic
├── competencies
├── tags
├── body
├── attachments
├── options
├── canonical answer
├── accepted alternatives
├── methodologies
├── explanation
├── distractor explanations
├── rubric
├── positive marks
├── negative marks
├── author
├── reviewer
├── approval status
└── revision history

```

---

# 26. Supported assessment question types

Initial:

```
single MCQ
multiple-selection MCQ
true/false
numeric
fill blank
short answer
long answer
essay
matching
ordering
file submission

```

Subsequent:

```
matrix
image hotspot
image annotation
mathematical expression
audio answer
video answer
programming
SQL
case studies
compound questions

```

---

# 27. Exam construction

Teacher/admin can create examinations manually or from question-bank rules.

Exam settings:

```
batch
availability window
duration
attempts
marks
pass criteria
negative marking
shuffle
randomization
sections
navigation rules
backtracking
late-submission policy
answer-review policy
result-release policy

```

Add blueprint generation:

```
20% easy
50% medium
30% hard

40% topic A
30% topic B
30% topic C

```

Persist generated papers permanently.

---

# 28. Automatic MCQ grading

This is a direct upgrade over Trechto's manual workflow.

For objective questions:

```
student answer
     ↓
immutable submitted answer
     ↓
answer key version
     ↓
grading rule
     ↓
system score

```

Store separately:

```
SYSTEM_SCORE
TEACHER_SCORE
FINAL_SCORE

```

Do not overwrite system decisions when teachers intervene.

---

# 29. Answer explanations

Question creators should optionally provide:

```
Why correct answer is correct
Why option A is wrong
Why option B is wrong
Why option C is wrong
Why option D is wrong
Alternative accepted methodology
References

```

After result publication students can review these according to exam policy.

---

# 30. Academic challenge and counterargument system

This should be a distinctive feature.

Student can challenge:

```
incorrect key
alternative methodology
ambiguous question
multiple valid answers
manual grading error
calculation discrepancy
missing information
other

```

Workflow:

```
Student challenge
     ↓
Teacher review
     ↓
Discussion
     ↓
Optional evaluator/moderator
     ↓
Resolution

```

Possible resolutions:

```
ORIGINAL_GRADE_UPHELD

PARTIAL_CREDIT

FULL_CREDIT

ALTERNATIVE_ANSWER_ACCEPTED

QUESTION_AMBIGUOUS

QUESTION_INVALIDATED

ANSWER_KEY_CORRECTED

RUBRIC_REVISED

```

All discussion becomes part of the academic review case.

---

# 31. Versioned academic truth

Never silently edit a question that students have already answered.

Instead:

```
Question v3
       ↓
revision
       ↓
Question v4

```

Completed attempts retain references to the exact version used.

Changes maintain:

```
actor
timestamp
reason
previous content
new content
affected assessments

```

---

# 32. Automatic regrading

If Question v4 changes the correct answer:

```
change
  ↓
impact analysis
  ↓
affected attempts discovered
  ↓
proposed new results
  ↓
authorized approval
  ↓
regrade events
  ↓
notifications

```

Student result history:

```
Original: 74
Revised:  76

Reason:
Alternative answer accepted after academic review.

```

No invisible historical mutation.

---

# 33. Manual grading

For subjective work:

```
submission
 ↓
grader
 ↓
rubric
 ↓
score
 ↓
feedback
 ↓
optional moderator
 ↓
publication

```

Support:

- question-by-question grading;
- student-by-student grading;
- batch grading;
- anonymous grading;
- double grading;
- moderation.

---

# 34. Assignments and projects

Implement:

```
text submissions
files
links
audio/video
source code
groups
multiple submissions
drafts
deadlines
late penalties
resubmission
rubrics
feedback
peer review

```

Projects add milestones.

---

# 35. Gradebook

Unified:

```
Exams
Assignments
Projects
Practical
Participation
Manual scores

```

Formula engine:

```
40% exams
30% project
20% assignments
10% practical

```

Support:

- best-of-N;
- lowest-score drop;
- minimum component marks;
- extra credit;
- pass conditions;
- rounding rules.

Formula versions must be retained.

---

# 36. Announcements

Implement target scopes:

```
organization
branch
course
batch
subject
role
selected users

```

Priorities:

```
normal
important
urgent
emergency

```

Support:

- future publication;
- expiration;
- attachments;
- acknowledgement-required.

---

# 37. Notification engine

System events generate notifications.

Examples:

```
CLASS_SCHEDULED
CLASS_RESCHEDULED
CLASS_STARTING
RESOURCE_RELEASED
EXAM_SCHEDULED
EXAM_STARTING
ASSIGNMENT_DUE
RESULT_PUBLISHED
RECORDING_READY
PAYMENT_DUE
CHALLENGE_UPDATED

```

Delivery:

```
In-app
Web Push
Email
Desktop Push
Mobile Push
SMS adapter
WhatsApp adapter

```

Notification delivery must happen asynchronously through durable jobs/outbox events.

---

# 38. Messaging

Support controlled communication:

```
teacher ↔ student
teacher ↔ batch
admin ↔ user
support ↔ tenant

```

Policy can disable student-to-student communication.

Institutional communication must be auditable where required.

---

# 39. Competency tracking

Define learning outcomes and map them to:

```
classes
questions
assignments
courses
credentials

```

Then report mastery by learning outcome rather than simply overall percentage.

---

# 40. Certificates and credentials

Implement after core academics.

Support:

```
completion
achievement
assessment
attendance
participation
custom certification

```

Each credential gets:

```
unique ID
verification URL
QR code
issue date
issuer
recipient
revocation state

```

Long-term architecture should permit Open Badges/CLR-style portable credentials.

---

# 41. Student fees

Separate customer SaaS billing from student fees.

```
Student
 ↓
Institution payment system

```

versus:

```
Institution
 ↓
Your SaaS subscription

```

Student fee system:

```
fee plan
installment
invoice
discount
scholarship
payment
refund
receipt
reconciliation

```

Payment-provider integrations remain adapters.

---

# 42. Commercial licensing

Create a dedicated entitlement service.

Never write:

```
if plan == PRO

```

Use:

```
Tenant
 ↓
Subscription
 ↓
Entitlements
 ↓
Limits
 ↓
Usage

```

Features:

```
LIVE_CLASS
RECORDING
ASSESSMENTS
AUTO_GRADING
ADVANCED_REVIEW
ASSIGNMENTS
PAYMENTS
CERTIFICATES
API
WHITE_LABEL
SSO
AI

```

Limits:

```
active students
teachers
administrators
storage
recording hours
concurrent meetings
meeting participants
SMS
AI consumption

```

---

# 43. Subscription lifecycle

```
TRIAL
 ↓
ACTIVE
 ↓
GRACE
 ↓
SUSPENDED
 ↓
EXPIRED

```

Separate:

```
REVOKED
TERMINATED

```

Do not immediately destroy customer data when a subscription expires.

Move the tenant into restricted/read-only/grace states according to policy.

---

# 44. Low-cost pricing architecture

Avoid making every capability consume your infrastructure.

Default commercial strategy:

### Customer supplies

where desired:

```
Google Drive
NAS
object storage
SMTP
SMS provider
payment account
SSO

```

### Platform supplies

```
application
database
authentication
academic workflows
media coordination
licensing

```

This creates a **BYOI/BYOS model**:

> Bring Your Own Infrastructure/Storage where economical.

Premium managed plans can include everything.

---

# 45. Deployment profiles

## Profile A — Development

```
one workstation
PostgreSQL
Valkey
Keycloak
Spring application
React
local storage
local media

```

## Profile B — Small institution / economy

```
VM 1:
reverse proxy
web
Java backend
Keycloak
PostgreSQL
Valkey

VM 2 when needed:
WebRTC/media

Storage:
client Google Drive/NAS/S3

```

Avoid Kubernetes.

## Profile C — Standard SaaS

```
load balancer

2+ application nodes
managed/self-hosted PostgreSQL
Valkey
separate media nodes
recording workers
object storage

```

Many tenants share resources.

## Profile D — Large SaaS

```
horizontal app tier
HA PostgreSQL
distributed cache
multiple media nodes
autoscaled recording
CDN
regional storage
analytics workers

```

## Profile E — Enterprise dedicated

```
dedicated database
dedicated storage
dedicated application pool
optional dedicated media

```

## Profile F — On-premise

```
containerized deployment
local PostgreSQL
local/NAS storage
optional internet-independent operation
local identity federation

```

---

# 46. Keep Kubernetes out of initial production unless necessary

For early customers use:

```
Docker/Podman
VMs
Compose/system services
reverse proxy

```

Kubernetes becomes appropriate when:

- horizontal scaling is routine;
- multiple nodes are required;
- media workers autoscale;
- regional deployments exist;
- enterprise operations justify it.

Avoid paying operational complexity before it produces value.

---

# 47. Web UX architecture

Create three substantially different experiences using the same design system.

### Student

```
Today
Learn
Classes
Tasks
Exams
Results
Attendance
Recordings
Messages
Calendar

```

### Teacher

```
Today
My Classes
Students
Content
Assessments
Grading
Reviews
Communication
Analytics

```

### Administrator

```
Overview
Academics
People
Scheduling
Assessments
Operations
Finance
Reports
Configuration

```

Don't expose backend module names to ordinary users.

---

# 48. Universal Today dashboard

This should be the primary page.

Student:

```
09:00 Java Class
11:00 Assignment due
14:00 Database exam

1 new result
2 new notes

```

Teacher:

```
09:00 Java Class
27 submissions waiting
14:00 Database exam
3 answer challenges

```

Admin:

```
61 classes today
4 exams
1,429 scheduled students
3 storage warnings
7 approvals pending

```

---

# 49. Accessibility

Treat WCAG 2.2 AA as a release requirement.

Implement:

```
keyboard navigation
screen reader semantics
visible focus
sufficient target size
captions
high contrast
zoom
reduced motion
accessible examinations
accessible graphs

```

---

# 50. Responsive strategy

Web application must work on:

```
large desktop
laptop
tablet
mobile browser

```

But desktop-scale administration does not need to be awkwardly identical on a 360 px phone.

Use task-appropriate responsive experiences.

---

# 51. PWA

Make the web app installable.

Use PWA capabilities for:

```
application shell
notifications
limited offline metadata
cached resources
home-screen launch

```

Do not make PWA offline behavior authoritative for exams without the dedicated synchronization subsystem.

---

# 52. Native-client readiness

Before completing the web product, ensure these contracts exist:

```
OpenAPI specification
WebSocket protocol
media-session protocol
file-transfer protocol
notification contract
auth/OIDC flows
offline-sync versioning
license-entitlement protocol
client-version policy

```

Then:

```
Web
Windows
Linux
macOS
Android
iOS

```

can reuse server capabilities.

---

# 53. Future macOS native path

Because the selected media architecture supports Apple's native SDK ecosystem, a future macOS client can use a platform-native media implementation instead of embedding the entire browser experience. LiveKit currently exposes Swift support for iOS/macOS/visionOS.

---

# 54. Future Windows/Linux path

Keep UI and business APIs platform-neutral.

Potential future desktop approaches can be benchmarked before implementation:

```
JavaFX + native/media integration

or

lightweight native shell + shared web UI

or

C++/Rust media shell + platform UI

```

Do not freeze this choice during webapp development.

The critical requirement now is **protocol independence**.

---

# 55. API architecture

Use:

```
REST
OpenAPI 3.x
WebSocket
SSE where one-way streaming fits

```

Example namespaces:

```
/api/v1/auth
/api/v1/me

/api/v1/organizations
/api/v1/users
/api/v1/roles

/api/v1/programs
/api/v1/courses
/api/v1/batches
/api/v1/enrollments

/api/v1/schedule
/api/v1/classes
/api/v1/meetings
/api/v1/presence

/api/v1/resources
/api/v1/recordings

/api/v1/questions
/api/v1/assessments
/api/v1/attempts
/api/v1/grading
/api/v1/reviews

/api/v1/assignments
/api/v1/gradebook

/api/v1/announcements
/api/v1/notifications
/api/v1/messages

/api/v1/payments

/api/v1/licensing
/api/v1/usage

/api/v1/reports
/api/v1/audit

```

---

# 56. Optimistic concurrency

All editable high-value entities should contain versions:

```
question
exam
result
note
policy
schedule

```

If two administrators edit the same exam:

```
409 Conflict

```

and the UI presents changed fields.

Do not silently overwrite.

---

# 57. Autosave

Implement autosave for:

```
exam construction
question writing
notes
grading
student examination answers
assignments
announcements

```

Draft state remains distinct from published state.

---

# 58. Exam resilience

During an exam:

```
browser
   ↓
encrypted/local temporary state where appropriate
   ↓
periodic server save

```

Each answer:

```
attempt ID
question ID
revision
payload
server sequence
timestamp

```

Retry safely through idempotency keys.

Connection failure must not erase existing work.

---

# 59. Server-authoritative time

Exam timing is based on server timestamps.

Browser clock is display assistance only.

Persist:

```
started_at
expires_at
submitted_at

```

on the server.

---

# 60. Database design

Core domains approximately:

```
tenant
user
membership
role
permission

academic_period
program
course
subject
module
batch
enrollment

class_session
schedule_series
schedule_occurrence
meeting
presence_event

resource
resource_version
storage_object
recording

question
question_version
assessment
assessment_version
assessment_question

attempt
answer
answer_revision
grade
grade_revision
review_case
review_message
regrade_event

assignment
submission

announcement
notification
delivery

invoice
payment

license
entitlement
usage

audit_event

```

---

# 61. Large-table strategy

Likely high-growth tables:

```
audit_event
presence_event
learning_event
notification_delivery
answer_revision

```

Design them for:

```
append-oriented writes
time indexes
later partitioning
archival

```

Do not partition everything on day one.

---

# 62. Data integrity

Use database constraints for invariants.

Examples:

```
attempt belongs to assessment

enrollment belongs to tenant

final score cannot exceed allowed maximum unless policy explicitly permits bonus

storage object hash required before AVAILABLE state

published assessment must reference immutable question versions

```

Application validation alone is insufficient.

---

# 63. Security baseline

Release security requirements:

```
TLS everywhere
secure cookies
CSRF protection
CSP
XSS protection
rate limiting
MFA
passkeys
short-lived authorization
password hashing
tenant isolation
secret management
malware scanning
audit logging
signed releases
dependency scanning
SBOM
backup encryption

```

---

# 64. File security

Uploads:

```
type validation
size validation
extension-independent MIME detection
malware scanning
archive bomb protection
filename normalization
content hash
quarantine

```

Uploaded content must never execute in the application environment.

---

# 65. Exam security

Support configurable:

```
attempt limits
password
IP restrictions
device restrictions
question randomization
option randomization
time limits
copy controls
fullscreen warning
tab-switch telemetry
proctoring adapter

```

Do not claim browser controls make cheating impossible.

They provide signals and deterrence.

---

# 66. Recording protection

Use layers:

```
authorization
short-lived playback token
signed requests
watermarks
download policy
audit
OS capture restrictions in native apps where available

```

Dynamic watermark:

```
student name
student ID
timestamp
session

```

moves periodically.

Never advertise absolute screen-recording prevention.

---

# 67. Secrets

Production secrets live in:

```
KMS
Vault-like secret manager
container secret store

```

depending on deployment.

Never store:

```
Google refresh tokens
payment secrets
media keys
license private keys

```

as plaintext configuration values.

---

# 68. Audit architecture

Append-oriented:

```
actor
tenant
action
resource
old value reference
new value reference
reason
timestamp
IP
device/session
trace

```

High-value events include:

- result changes;
- answer-key changes;
- grade overrides;
- permission modifications;
- payment/refund changes;
- recording accesses;
- support access;
- licensing changes.

---

# 69. Logging

Logs must never contain:

```
passwords
OAuth tokens
exam answers
full payment data
private recording URLs
sensitive uploaded documents

```

Use structured logs.

---

# 70. Observability

Use OpenTelemetry-compatible:

```
traces
metrics
logs

```

Monitor infrastructure and business operations.

Infrastructure:

```
CPU
memory
GC
DB pool
latency
errors
disk

```

Business:

```
meeting joins
exam autosaves
submissions
grading backlog
recording queue
upload failures
notification failures
payment mismatches

```

---

# 71. Java performance strategy

Use modern Java facilities intelligently.

Avoid unbounded:

```
threads
queues
collections
queries
caches

```

Use virtual threads for suitable blocking I/O workflows.

Use JFR for profiling.

JDK 25 includes expanded JFR functionality including CPU-time profiling and method timing/tracing work.

---

# 72. Memory strategy

Hard rules:

```
stream large files
paginate every collection
bound every queue
bound every cache
never hold recordings in heap
never create giant JSON exports in memory

```

Large report:

```
DB cursor
 ↓
stream rows
 ↓
stream CSV/XLSX writer
 ↓
client/storage

```

---

# 73. Storage efficiency

Apply:

```
compression where suitable
deduplication within tenant
content hashes
tiered storage
retention
thumbnail generation
video bitrate policy

```

Avoid global cross-tenant deduplication by default because isolation and deletion semantics become more complicated.

---

# 74. Media efficiency

Live class policy should use:

```
adaptive bitrate
simulcast/dynacast where media provider supports it
video subscription based on visibility
speaker optimization
low-bandwidth mode
audio priority

```

Offer:

```
High Quality
Balanced
Data Saver
Audio + Slides
Audio Only

```

---

# 75. Cost-aware recording policy

Tenant administrators can specify:

```
Record never
Teacher chooses
Record automatically
Record only selected courses

```

plus:

```
resolution
retention
storage destination

```

Example economy policy:

```
720p
28-day hot cache
then Google Drive
retain 1 year

```

---

# 76. Background jobs

Use durable jobs for:

```
notifications
email
recording processing
storage migration
transcoding
certificate generation
exports
imports
analytics
automatic regrading
retention cleanup

```

API requests should not wait for these jobs.

---

# 77. Transactional outbox

Business transaction:

```
Result published

```

writes:

```
result change
+
RESULT_PUBLISHED event

```

in one DB transaction.

A worker publishes the event afterward.

This prevents successful transactions from silently losing their notifications/workflows.

---

# 78. Idempotency

Required for:

```
exam submission
payments
webhooks
notifications
certificate issuance
storage upload completion
imports
automatic regrade

```

Every externally retried operation gets an idempotency identifier.

---

# 79. Search

Initial:

```
PostgreSQL full-text search

```

Search:

```
courses
resources
questions
students where authorized
announcements
recording transcript metadata

```

Add dedicated search infrastructure only when needed.

---

# 80. Analytics

Initial analytics should derive from explicit events.

Examples:

### Teacher

```
attendance
completion
exam performance
question difficulty
grading backlog

```

### Student

```
progress
attendance
assessment trends
learning outcomes

```

### Administrator

```
enrollment
teacher workload
attendance
storage
recording hours
assessment performance
financial status
usage/licensing

```

---

# 81. Question-quality analytics

Measure:

```
correct rate
response time
skip rate
challenge rate
manual override rate
option distribution
discrimination

```

Flag suspicious questions for human review.

---

# 82. Workflow engine

Add a generic rules system later:

```
WHEN event
IF conditions
THEN actions

```

Example:

```
WHEN attendance < 70%
THEN notify learner and mentor

```

or:

```
WHEN score < 40%
THEN assign remedial content

```

---

# 83. Education interoperability

The internal architecture should permit adapters for:

```
OneRoster
QTI
LTI
CASE
xAPI
Caliper
Open Badges
CLR
SCORM

```

Do not copy those schemas directly into internal tables.

Create adapters around a stable canonical domain model.

---

# 84. AI architecture — optional and later

No core operation may depend on AI.

Potential capabilities:

```
teacher content assistant
question drafting
rubric suggestions
student tutoring
class summaries
transcription summaries
semantic search
translation
grading suggestions
academic-review summarization

```

Rules:

```
AI proposes
Human approves

```

for high-impact academic operations.

AI never silently changes final grades.

---

# 85. White-labeling

Tenant customization:

```
name
logo
branding
domain
email templates
certificate templates
invoice templates
support details
login page

```

Avoid client-specific source forks.

---

# 86. Public APIs

Enterprise later:

```
REST API
webhooks
service accounts
scoped API credentials

```

Webhook delivery supports:

```
signature
timestamp
retry
event ID
delivery history

```

---

# 87. Backup strategy

Production:

```
PostgreSQL continuous backup/PITR
daily snapshots
object versions
configuration backup
credential/key recovery

```

Test restoration regularly.

Backups that have never been restored are not a reliability mechanism; they are optimism in file format.

---

# 88. Disaster-recovery targets

Define measurable objectives.

Suggested mature SaaS target:

```
core RPO ≤ 5 minutes
core RTO ≤ 1 hour

```

Enterprise plans can provide stronger targets.

---

# 89. Graceful degradation

If transcription fails:

```
recording still works

```

If analytics fails:

```
classes/exams still work

```

If email fails:

```
result publication still succeeds
notification retries later

```

If recording fails:

```
live class remains operational

```

Critical workflows must not depend on noncritical services.

---

# 90. Testing pyramid

Every module:

```
unit tests
domain tests
property tests
integration tests
database tests
API contract tests
security tests

```

System:

```
browser E2E
load
fault injection
upgrade/migration
backup restore
accessibility
cross-browser

```

---

# 91. Critical invariants

Examples:

```
No cross-tenant data leakage.

Published assessment references immutable question versions.

Final result is reproducible.

Every grade revision is auditable.

Every financial state transition is traceable.

Expired entitlement cannot create unauthorized new usage.

Existing student answers cannot disappear.

Recording object cannot be AVAILABLE without integrity metadata.

```

Automate checks.

---

# 92. Load testing

Test realistic spikes.

Example:

```
08:55
10,000 logins

09:00
4,000 meeting joins

10:00
3,000 exam starts

10:59
2,700 simultaneous submissions

```

Do not benchmark only `/health`.

---

# 93. Performance targets

Initial engineering budgets:

```
ordinary cached API p95     <100 ms target
ordinary DB API p95         <300 ms target
answer autosave p95         <300 ms target
notification enqueue        <200 ms target
simple page interaction     <100 ms perceived response

```

Targets should be measured under documented load rather than claimed universally.

---

# 94. Database-query gates

CI/performance review should catch:

```
N+1 queries
unbounded reads
missing pagination
unexpected table scans
excessive query counts

```

High-volume endpoints receive query-budget tests.

---

# 95. Frontend performance

Use:

```
route-level code splitting
lazy-loaded heavy editors
virtualized large tables
optimized image delivery
request deduplication
server state cache
background prefetch

```

Do not ship the exam builder, video player, admin analytics and rich-text editor to the student login screen.

---

# 96. Cost model

The primary cost centers will be:

```
compute
database
video bandwidth
recording/transcoding
storage
email/SMS
AI if enabled

```

Business CRUD is comparatively inexpensive.

Design the platform around reducing the media/storage components.

---

# 97. Cost-reduction strategy

## First

Use multi-tenant SaaS.

Many customers share:

```
application
identity
database infrastructure
cache
monitoring

```

## Second

BYOS.

Use client:

```
Google Drive
NAS
S3
cloud storage

```

## Third

Record only when needed.

## Fourth

720p default, not blindly 1080p.

## Fifth

Use cheap archival tiers.

## Sixth

Avoid Kubernetes for small installations.

## Seventh

Allow client SMTP/SMS/payment accounts.

## Eighth

Scale recording workers based on actual demand.

## Ninth

Keep analytics within PostgreSQL until dedicated infrastructure is justified.

## Tenth

Make AI entirely optional and budget-controlled.

---

# 98. Versioned implementation roadmap

Use four-component project versioning:

```
stable.major.minor.patch

```

---

## 0.1.0.0 — Engineering Foundation

Implement:

- monorepo/project structure;
- Java 25;
- Spring Boot 4.1;
- Spring Modulith;
- PostgreSQL;
- migrations;
- React/TypeScript application;
- OpenAPI;
- authentication foundation;
- tenant model;
- audit foundation;
- license/entitlement skeleton;
- CI;
- coding standards;
- observability;
- Docker development environment.

Gate:

```
application boots
tenant boundaries tested
CI clean
schema migration reversible/verified
zero critical security issues

```

---

## 0.2.0.0 — Identity and Organization

Implement:

- users;
- memberships;
- roles;
- permissions;
- branches;
- organization settings;
- custom roles;
- administrator UI;
- teacher/student identity;
- MFA/passkey integration;
- session management.

---

## 0.3.0.0 — Academic Core

Implement:

- academic periods;
- programs;
- courses;
- subjects;
- modules;
- batches;
- enrollments;
- teacher assignment;
- learner dashboard;
- teacher dashboard;
- admin dashboard.

---

## 0.4.0.0 — Scheduling and Communication

Implement:

- calendar;
- recurring schedules;
- classes;
- conflicts;
- announcements;
- in-app notifications;
- email;
- notification preferences;
- Today dashboard.

---

## 0.5.0.0 — Learning Content

Implement:

- textual notes;
- resources;
- files;
- upload pipeline;
- versions;
- download permissions;
- storage abstraction;
- local storage;
- S3-compatible storage;
- Google Drive integration;
- Shared Drive integration;
- retention.

---

## 0.6.0.0 — Assessment Core

Implement:

- question bank;
- question versions;
- MCQ;
- multi-selection;
- true/false;
- numeric;
- text questions;
- exam construction;
- batch assignment;
- scheduling;
- attempts;
- autosave;
- server-side timing;
- submissions.

---

## 0.7.0.0 — Grading and Academic Review

Implement:

- automatic objective grading;
- manual grading;
- rubrics;
- explanations;
- result publishing;
- student review;
- challenge/counterargument;
- discussion;
- answer revision;
- impact analysis;
- automatic regrading;
- immutable grade history.

This should be one of the project's major differentiators.

---

## 0.8.0.0 — Assignments and Gradebook

Implement:

- assignments;
- project submissions;
- deadlines;
- resubmission;
- rubrics;
- gradebook;
- weighted formulas;
- student progress.

---

## 0.9.0.0 — Live Learning

Implement:

- media-provider abstraction;
- LiveKit integration;
- meeting tokens;
- live classroom;
- moderation;
- screen share;
- chat;
- class presence;
- attendance policies;
- low-bandwidth operation.

---

## 0.10.0.0 — Recording Platform

Implement:

- recording;
- recording worker orchestration;
- local storage;
- Drive archival;
- object-storage archival;
- retention;
- playback authorization;
- dynamic watermarking;
- processing states;
- thumbnails;
- quality presets;
- hot/cache/archive transitions.

---

## 0.11.0.0 — Finance and Commercialization

Implement:

- institution fees;
- installments;
- invoices;
- receipts;
- payment adapter;
- SaaS subscriptions;
- entitlement enforcement;
- tenant quotas;
- usage metering;
- trials;
- grace period;
- white-labeling.

---

## 0.12.0.0 — Analytics and Operations

Implement:

- attendance analytics;
- assessment analytics;
- question analytics;
- operational dashboards;
- usage;
- storage forecasting;
- recording usage;
- administrative reports;
- exports.

---

## 0.13.0.0 — Advanced Academic Platform

Implement:

- learning outcomes;
- competencies;
- learning paths;
- certificates;
- credential verification;
- mentoring;
- surveys;
- feedback.

---

## 0.14.0.0 — Integration Platform

Implement:

- APIs;
- webhooks;
- SSO;
- enterprise federation;
- OneRoster adapters;
- QTI import/export foundation;
- external storage providers;
- external notification providers.

---

## 0.15.0.0 — Reliability and Performance Qualification

Perform:

- systematic load testing;
- memory profiling;
- DB optimization;
- JVM tuning;
- browser optimization;
- media load tests;
- backup restore;
- chaos/failure testing;
- security penetration testing;
- accessibility audit;
- migration testing;
- multi-tenant leakage testing.

No major feature expansion during this milestone.

---

## 0.16.0.0 — Production Candidate

Complete:

- documentation;
- admin manuals;
- teacher manuals;
- student help;
- installation;
- security guides;
- privacy tooling;
- SLA instrumentation;
- status page;
- release/update system;
- deployment automation;
- billing readiness.

---

## 1.0.0.0 — Production Web GA

Requirements:

```
Web application complete
No critical known defects
All P0/P1 functionality integrated
Security gates passing
Performance targets verified
Backups restored successfully
Tenant isolation verified
Assessment history reproducible
Recording/storage failover verified
Payments reconciled
Licensing enforced
Accessibility target reached
Documentation complete

```

This is the first commercially supported stable product.

---

# 99. Post-1.0 native-client roadmap

## 1.1.x.x

Desktop client protocol stabilization.

## 1.2.x.x

Windows production application.

## 1.3.x.x

macOS production application.

## 1.4.x.x

Linux production application.

## 1.5.x.x

Android application.

## 1.6.x.x

iOS/iPadOS.

Clients share:

```
identity
APIs
meeting authorization
content
exam contracts
sync contracts
licensing
notifications

```

not duplicated backend rules.

---

# 100. State-of-the-art measurement framework

“State-of-the-art” must be measurable.

Track:

## Correctness

```
test pass rate
invariant failures
data-integrity incidents
grade reproducibility

```

## Performance

```
p50/p95/p99 latency
throughput
query latency
meeting join time
autosave latency

```

## Efficiency

```
CPU/user
RAM/user
bandwidth/meeting
storage/hour recording
DB queries/request

```

## Reliability

```
availability
error rate
RPO
RTO
job retry success

```

## Security

```
ASVS controls
critical vulnerabilities
dependency CVEs
tenant-isolation tests
MFA adoption

```

## UX

```
task completion time
error rate
accessibility
Core Web Vitals
interaction latency

```

## Storage

```
dedupe savings
archive ratio
failed uploads
restore success
cost/GB

```

## Media

```
join success
packet loss
reconnect success
recording success
bandwidth adaptation

```

## Academic quality

```
question challenge rate
grading override rate
ambiguous-question rate
result revision rate

```

## Commercial efficiency

```
infrastructure cost/active learner
cost/meeting-hour
cost/recording-hour
storage cost/tenant
support burden

```

No performance claim should be published without a reproducible benchmark.

---

# 101. Product definition at completion

The completed system should no longer be accurately described as merely a Trechto alternative.

It becomes:

> A multi-tenant, commercially licensable learning and institutional operations platform that unifies academic administration, live and asynchronous learning, storage-independent class recordings, content, assessments, automatic/manual grading, evidence-based academic disputes and reconciliation, attendance, communication, finance, analytics, credentials and integrations across web and future native clients.

The web application is merely the first client.

The **platform is the product**.

---

# 102. Final architectural rule

All future development should preserve this dependency direction:

```
                 CLIENTS
                   │
                   ▼
          CONTRACTS / APIs
                   │
                   ▼
            DOMAIN PLATFORM
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
     DATA       STORAGE      MEDIA
        │          │          │
        └──────────┼──────────┘
                   ▼
        INFRASTRUCTURE ADAPTERS

```

Never:

```
Google Drive logic inside examinations

LiveKit logic inside courses

Windows logic inside licensing

React logic inside academic rules

```

The implementation remains provider-independent, client-independent and deployment-independent.

That is what makes the project capable of growing from an inexpensive web product for a small institute into a globally deployable education platform without requiring a complete rewrite.