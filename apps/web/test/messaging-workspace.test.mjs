import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import test from 'node:test'

const apiMessaging = readFileSync(fileURLToPath(new URL('../src/platform/api-messaging.ts', import.meta.url)), 'utf8')
const workspace = readFileSync(fileURLToPath(new URL('../src/communication/MessagingWorkspace.tsx', import.meta.url)), 'utf8')
const appSource = readFileSync(fileURLToPath(new URL('../src/app/App.tsx', import.meta.url)), 'utf8')
const controller = readFileSync(
  fileURLToPath(new URL('../../platform-server/src/main/java/com/universalplatform/messaging/MessagingController.java', import.meta.url)),
  'utf8',
)

test('the messaging client targets the server-authoritative messaging endpoints', () => {
  assert.match(controller, /@RequestMapping\("\/api\/v1\/messaging"\)/)
  for (const mapping of [
    '@GetMapping("/conversations")',
    '@PostMapping("/conversations")',
    '@GetMapping("/conversations/{conversationId}/messages")',
    '@PostMapping("/conversations/{conversationId}/messages")',
    '@PostMapping("/conversations/{conversationId}/read")',
    '@GetMapping("/messages/{messageId}/delivery")',
    '@PutMapping("/messages/{messageId}")',
    '@DeleteMapping("/messages/{messageId}")',
    '@GetMapping("/blocks")',
    '@PutMapping("/blocks/{membershipId}")',
    '@DeleteMapping("/blocks/{membershipId}")',
    '@GetMapping("/reports")',
    '@PostMapping("/reports/{reportId}/resolve")',
    '@PostMapping("/messages/{messageId}/reports")',
    '@GetMapping("/policy")',
    '@PutMapping("/policy")',
  ]) {
    assert.ok(controller.includes(mapping), `backend must keep the messaging mapping ${mapping}`)
  }
  for (const clientPath of [
    '/api/v1/messaging/conversations?page=',
    "request<MessageConversationView>('/api/v1/messaging/conversations'",
    '/api/v1/messaging/conversations/${encodeURIComponent(conversationId)}/messages',
    '/api/v1/messaging/conversations/${encodeURIComponent(conversationId)}/read',
    '/api/v1/messaging/messages/${encodeURIComponent(messageId)}/delivery',
    '/api/v1/messaging/messages/${encodeURIComponent(messageId)}`',
    "request<MessageBlockView[]>('/api/v1/messaging/blocks')",
    '/api/v1/messaging/blocks/${encodeURIComponent(membershipId)}',
    '/api/v1/messaging/reports?page=',
    '/api/v1/messaging/reports/${encodeURIComponent(reportId)}/resolve',
    '/api/v1/messaging/messages/${encodeURIComponent(messageId)}/reports',
    "request<MessagePolicyView>('/api/v1/messaging/policy')",
  ]) {
    assert.ok(apiMessaging.includes(clientPath), `messaging API client must keep ${clientPath}`)
  }
  assert.doesNotMatch(apiMessaging, /\bfetch\s*\(/, 'messaging client must use the shared authenticated transport')
  assert.match(apiMessaging, /import \{ request \} from '\.\/api-core'/)
})

test('the messaging workspace reads, sends and acknowledges through the platform API', () => {
  for (const required of [
    'fetchMessageConversations',
    'fetchMessages',
    'sendMessage',
    'markConversationRead',
    'fetchMessageDelivery',
    'reportMessage',
    'blockMessageMembership',
    'unblockMessageMembership',
    'createMessageConversation',
    'fetchMessageBlocks',
  ]) {
    assert.ok(workspace.includes(required), `messaging workspace must use ${required}`)
  }
  assert.ok(workspace.includes('openConversation.mutate(conversationId)'), 'selecting a conversation must record read state')
  assert.ok(workspace.includes("mutationFn: (conversationId: string) => markConversationRead(conversationId)"))
  assert.ok(workspace.includes("send.mutate({ conversationId: selectedId, body: draft.trim() })"))
  assert.ok(workspace.includes('openReceipts[message.id]'), 'own messages must expose delivery/read receipts')
  assert.ok(workspace.includes("message.senderMembershipId === me.membershipId"), 'receipt and edit actions must be sender-scoped in the UI')
})

test('moderation and policy stay behind MESSAGING_MODERATE while messaging itself is membership-scoped', () => {
  assert.ok(workspace.includes("me.permissions.includes('MESSAGING_MODERATE')"))
  assert.ok(workspace.includes('{canModerate && <ModerationPanel />}'))
  assert.ok(workspace.includes("const canBrowseDirectory = me.permissions.includes('USERS_VIEW')"))
  assert.ok(workspace.includes('<NewConversation'), 'the workspace must offer conversation creation')
  assert.ok(workspace.includes('canBrowseDirectory={canBrowseDirectory}'))
  assert.ok(workspace.includes('resolveMessageReport'), 'moderators must resolve reports through the server API')
  assert.ok(workspace.includes('updateMessagePolicy'), 'moderators must update the tenant policy through the server API')
  assert.ok(workspace.includes('expectedVersion: draft.version'), 'policy writes must send the current version')
  assert.ok(workspace.includes('report.version'), 'report resolution must send the current version')
  assert.ok(workspace.includes("me.permissions.includes('MESSAGING_MODERATE')") && !workspace.includes("canModerate = true"))
})

test('the Real navigation exposes Messages to every signed-in member', () => {
  assert.ok(appSource.includes("import { MessagingWorkspace } from '../communication/MessagingWorkspace'"))
  assert.ok(appSource.includes("{ id: 'messages', label: 'Messages', visible: true }"))
  assert.ok(appSource.includes("{section === 'messages' && <MessagingWorkspace me={me} />}"))
  assert.ok(appSource.includes("messages: 'Messages'"))
  assert.ok(appSource.includes("'notifications'"), 'existing notification navigation must be preserved')
})

test('messaging surfaces loading, empty, error and disabled states', () => {
  assert.ok(workspace.includes('Loading conversations…'))
  assert.ok(workspace.includes('No conversations yet.'))
  assert.ok(workspace.includes('No messages in this conversation yet.'))
  assert.ok(workspace.includes('No members are blocked.'))
  assert.ok(workspace.includes('No message reports have been submitted.'))
  assert.ok(workspace.includes('Loading messages…'))
  assert.ok(workspace.includes('Loading receipts…'))
  assert.ok(workspace.includes('can be disabled by an administrator'))
  assert.ok(workspace.includes('role={status.tone === \'error\' ? \'alert\' : \'status\'}'))
  assert.ok(workspace.includes('aria-current={conversation.id === selectedId ? \'page\' : undefined}'))
  assert.ok(workspace.includes('aria-expanded={Boolean(openReceipts[message.id])}'))
})

test('receipt semantics are described as platform access, not provider delivery', () => {
  assert.ok(workspace.includes('not proof that an external email or push provider delivered a notification'))
  assert.ok(workspace.includes('Delivery and read state is recorded by the server when recipients next use the messaging API.'))
  assert.ok(workspace.includes('immutable evidence snapshot'))
})
