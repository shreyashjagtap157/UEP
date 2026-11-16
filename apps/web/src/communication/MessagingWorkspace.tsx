import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  blockMessageMembership,
  createMessageConversation,
  deleteMessage,
  editMessage,
  fetchMembers,
  fetchMessageBlocks,
  fetchMessageConversations,
  fetchMessageDelivery,
  fetchMessagePolicy,
  fetchMessageReports,
  fetchMessages,
  markConversationRead,
  reportMessage,
  resolveMessageReport,
  sendMessage,
  unblockMessageMembership,
  updateMessagePolicy,
} from '../platform/api'
import type { CurrentIdentity, MessagePolicyView, MessageReportStatus, MessageReportView, MessageView } from '../platform/api'

type Status = { tone: 'info' | 'error'; message: string }

const resolveStatuses: Array<Exclude<MessageReportStatus, 'OPEN'>> = ['REVIEWED', 'DISMISSED', 'ACTIONED']

export function MessagingWorkspace({ me }: { me: CurrentIdentity }) {
  const client = useQueryClient()
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [draft, setDraft] = useState('')
  const [status, setStatus] = useState<Status | null>(null)
  const [editing, setEditing] = useState<{ id: string; body: string; version: number } | null>(null)
  const [reportDrafts, setReportDrafts] = useState<Record<string, string>>({})
  const [openReceipts, setOpenReceipts] = useState<Record<string, boolean>>({})
  const [olderItems, setOlderItems] = useState<MessageView[]>([])
  const [olderPage, setOlderPage] = useState(0)
  const canModerate = me.permissions.includes('MESSAGING_MODERATE')
  const canBrowseDirectory = me.permissions.includes('USERS_VIEW')

  const conversations = useQuery({ queryKey: ['messaging-conversations'], queryFn: () => fetchMessageConversations() })
  const blocks = useQuery({ queryKey: ['messaging-blocks'], queryFn: fetchMessageBlocks })
  const selected = conversations.data?.items.find(item => item.id === selectedId) ?? null
  const messages = useQuery({
    queryKey: ['messaging-messages', selectedId],
    queryFn: () => fetchMessages(selectedId ?? ''),
    enabled: Boolean(selectedId),
  })
  const loadOlder = useMutation({
    mutationFn: ({ conversationId, page }: { conversationId: string; page: number }) => fetchMessages(conversationId, page, 50),
    onSuccess: (page, variables) => {
      if (variables.conversationId !== selectedId) return
      setOlderItems(current => [...page.items, ...current])
      setOlderPage(page.page)
    },
    onError: error => setStatus({ tone: 'error', message: error instanceof Error ? error.message : 'Older messages could not be loaded' }),
  })

  function refreshConversations() {
    void client.invalidateQueries({ queryKey: ['messaging-conversations'] })
    void client.invalidateQueries({ queryKey: ['today'] })
  }

  const openConversation = useMutation({
    mutationFn: (conversationId: string) => markConversationRead(conversationId),
    onSuccess: (_result, conversationId) => {
      void client.invalidateQueries({ queryKey: ['messaging-messages', conversationId] })
      refreshConversations()
    },
    onError: error => setStatus({ tone: 'error', message: error instanceof Error ? error.message : 'The conversation could not be marked as read' }),
  })

  const send = useMutation({
    mutationFn: ({ conversationId, body }: { conversationId: string; body: string }) => sendMessage(conversationId, body),
    onSuccess: (_result, variables) => {
      setDraft('')
      setStatus({ tone: 'info', message: 'Message sent.' })
      void client.invalidateQueries({ queryKey: ['messaging-messages', variables.conversationId] })
      refreshConversations()
    },
    onError: error => setStatus({ tone: 'error', message: error instanceof Error ? error.message : 'The message was not sent' }),
  })

  const saveEdit = useMutation({
    mutationFn: (input: { id: string; body: string; version: number }) => editMessage(input.id, input.body, input.version),
    onSuccess: () => {
      setEditing(null)
      setStatus({ tone: 'info', message: 'Message updated.' })
      void client.invalidateQueries({ queryKey: ['messaging-messages', selectedId] })
    },
    onError: error => setStatus({ tone: 'error', message: error instanceof Error ? error.message : 'The message could not be updated' }),
  })

  const remove = useMutation({
    mutationFn: (input: { id: string; version: number }) => deleteMessage(input.id, input.version),
    onSuccess: () => {
      setStatus({ tone: 'info', message: 'Message deleted. It remains in the moderation record if it was reported.' })
      void client.invalidateQueries({ queryKey: ['messaging-messages', selectedId] })
    },
    onError: error => setStatus({ tone: 'error', message: error instanceof Error ? error.message : 'The message could not be deleted' }),
  })

  const report = useMutation({
    mutationFn: (input: { messageId: string; reason: string }) => reportMessage(input.messageId, input.reason),
    onSuccess: (_result, variables) => {
      setReportDrafts(current => ({ ...current, [variables.messageId]: '' }))
      setStatus({ tone: 'info', message: 'Report submitted to the institution moderators.' })
      if (canModerate) void client.invalidateQueries({ queryKey: ['messaging-reports'] })
    },
    onError: error => setStatus({ tone: 'error', message: error instanceof Error ? error.message : 'The report could not be submitted' }),
  })

  const block = useMutation({
    mutationFn: (membershipId: string) => blockMessageMembership(membershipId),
    onSuccess: () => {
      setStatus({ tone: 'info', message: 'Member blocked. Their messages will not reach you until you unblock them.' })
      void client.invalidateQueries({ queryKey: ['messaging-blocks'] })
      refreshConversations()
    },
    onError: error => setStatus({ tone: 'error', message: error instanceof Error ? error.message : 'The member could not be blocked' }),
  })

  const unblock = useMutation({
    mutationFn: (membershipId: string) => unblockMessageMembership(membershipId),
    onSuccess: () => {
      setStatus({ tone: 'info', message: 'Member unblocked.' })
      void client.invalidateQueries({ queryKey: ['messaging-blocks'] })
    },
    onError: error => setStatus({ tone: 'error', message: error instanceof Error ? error.message : 'The member could not be unblocked' }),
  })

  function selectConversation(conversationId: string) {
    setSelectedId(conversationId)
    setEditing(null)
    setDraft('')
    setOlderItems([])
    setOlderPage(0)
    loadOlder.reset()
    openConversation.mutate(conversationId)
  }

  // The server orders messages newest-first; the thread reads chronologically.
  const orderedMessages = Array.from(
    new Map<string, MessageView>([...olderItems, ...(messages.data?.items ?? [])].map(message => [message.id, message])).values(),
  ).reverse()

  function submitDraft() {
    if (!selectedId || !draft.trim()) return
    send.mutate({ conversationId: selectedId, body: draft.trim() })
  }

  const unreadTotal = conversations.data?.items.reduce((total, conversation) => total + conversation.unreadCount, 0) ?? 0

  return (
    <div className="content-stack">
      <section className="hero-card">
        <p className="eyebrow">Communication</p>
        <h3>Institution messaging</h3>
        <p>Conversations are tenant-scoped and server-authoritative. Delivery and read receipts describe platform access only; they are not proof that an external email or push provider delivered a notification.</p>
        <p className="muted small">{unreadTotal === 0 ? 'No unread conversations.' : `${unreadTotal} unread message${unreadTotal === 1 ? '' : 's'} across your conversations.`}</p>
        {status && <p className={status.tone === 'error' ? 'error-text' : 'muted'} role={status.tone === 'error' ? 'alert' : 'status'}>{status.message}</p>}
      </section>

      <div className="messaging-layout">
        <section className="panel" aria-label="Conversations">
          <div className="panel-heading">
            <div><p className="eyebrow">Inbox</p><h3>Conversations</h3></div>
            <div className="button-row">
              <span className="count-badge">{conversations.data?.totalElements ?? 0}</span>
              <button className="text-button" onClick={refreshConversations}>Refresh</button>
            </div>
          </div>
          {conversations.isPending ? <p className="muted" aria-live="polite">Loading conversations…</p> : conversations.isError ? (
            <div>
              <p className="error-text" role="alert">{conversations.error.message}</p>
              <p className="muted small">Institution messaging can be disabled by an administrator. If it is disabled for this tenant, no conversation can be read or sent.</p>
            </div>
          ) : conversations.data.items.length === 0 ? (
            <p className="muted">No conversations yet. {canBrowseDirectory ? 'Start one from the directory below.' : 'A member of staff can start a conversation with you.'}</p>
          ) : (
            <ul className="conversation-list">
              {conversations.data.items.map(conversation => (
                <li key={conversation.id}>
                  <button
                    className={conversation.id === selectedId ? 'conversation-button active' : 'conversation-button'}
                    aria-current={conversation.id === selectedId ? 'page' : undefined}
                    onClick={() => selectConversation(conversation.id)}
                  >
                    <span className="conversation-subject">{conversation.subject?.trim() || 'Untitled conversation'}</span>
                    <span className="conversation-meta">
                      {conversation.participantCount} participant{conversation.participantCount === 1 ? '' : 's'} · {new Date(conversation.updatedAt).toLocaleString()}
                    </span>
                    {conversation.unreadCount > 0 && <span className="chip-primary">{conversation.unreadCount} unread</span>}
                  </button>
                </li>
              ))}
            </ul>
          )}
          <NewConversation
            canBrowseDirectory={canBrowseDirectory}
            onCreated={conversationId => {
              setStatus({ tone: 'info', message: 'Conversation created.' })
              void client.invalidateQueries({ queryKey: ['messaging-conversations'] })
              setSelectedId(conversationId)
              setOlderItems([])
              setOlderPage(0)
            }}
            onError={message => setStatus({ tone: 'error', message })}
          />
        </section>

        <section className="panel" aria-label="Conversation">
          {!selected ? (
            <div>
              <div className="panel-heading"><div><p className="eyebrow">Thread</p><h3>Select a conversation</h3></div></div>
              <p className="muted">Choose a conversation to read its messages. Selecting a conversation records your read state on the server.</p>
            </div>
          ) : (
            <div className="content-stack">
              <div className="panel-heading">
                <div><p className="eyebrow">Thread</p><h3>{selected.subject?.trim() || 'Untitled conversation'}</h3></div>
                <span className="count-badge">{selected.participantCount} participant{selected.participantCount === 1 ? '' : 's'}</span>
              </div>
              {messages.isPending ? <p className="muted" aria-live="polite">Loading messages…</p> : messages.isError ? <p className="error-text" role="alert">{messages.error.message}</p> : messages.data.items.length === 0 ? (
                <p className="muted">No messages in this conversation yet.</p>
              ) : (
                <div className="content-stack">
                  {messages.data.totalPages > olderPage + 1 && (
                    <button
                      className="text-button"
                      disabled={loadOlder.isPending}
                      onClick={() => selectedId && loadOlder.mutate({ conversationId: selectedId, page: olderPage + 1 })}
                    >{loadOlder.isPending ? 'Loading older messages…' : 'Load older messages'}</button>
                  )}
                <ol className="message-thread">
                  {orderedMessages.map(message => {
                    const own = message.senderMembershipId === me.membershipId
                    return (
                      <li key={message.id} className={own ? 'message-entry own' : 'message-entry'}>
                        <article className="message-bubble">
                          <header className="message-meta">
                            <strong>{own ? 'You' : `Member ${shortId(message.senderMembershipId)}`}</strong>
                            <span>{new Date(message.createdAt).toLocaleString()}</span>
                            {message.editedAt && <span>edited</span>}
                            {message.deletedAt && <span className="danger-chip">Deleted</span>}
                          </header>
                          {editing?.id === message.id ? (
                            <form
                              className="message-composer"
                              onSubmit={event => {
                                event.preventDefault()
                                if (!editing.body.trim()) return
                                saveEdit.mutate({ id: editing.id, body: editing.body.trim(), version: editing.version })
                              }}
                            >
                              <label>Edit message<textarea rows={3} maxLength={4000} value={editing.body} onChange={event => setEditing({ ...editing, body: event.target.value })} /></label>
                              <div className="button-row">
                                <button className="primary-button" disabled={saveEdit.isPending || !editing.body.trim()}>Save edit</button>
                                <button type="button" className="secondary-button" onClick={() => setEditing(null)}>Cancel</button>
                              </div>
                            </form>
                          ) : (
                            <p className="message-body">{message.deletedAt ? 'This message was deleted by its sender.' : message.body}</p>
                          )}
                          <div className="message-actions">
                            {own && !message.deletedAt && (
                              <>
                                <button className="text-button" onClick={() => setEditing({ id: message.id, body: message.body, version: message.version })}>Edit</button>
                                <button className="text-button" disabled={remove.isPending} onClick={() => {
                                  if (window.confirm('Delete this message for every participant?')) remove.mutate({ id: message.id, version: message.version })
                                }}>Delete</button>
                                <button className="text-button" aria-expanded={Boolean(openReceipts[message.id])} onClick={() => setOpenReceipts(current => ({ ...current, [message.id]: !current[message.id] }))}>
                                  {openReceipts[message.id] ? 'Hide receipts' : 'Receipts'}
                                </button>
                              </>
                            )}
                            {!own && !message.deletedAt && (
                              <>
                                <button className="text-button" disabled={block.isPending} onClick={() => block.mutate(message.senderMembershipId)}>Block sender</button>
                                <details className="message-report">
                                  <summary>Report</summary>
                                  <form
                                    className="message-composer"
                                    onSubmit={event => {
                                      event.preventDefault()
                                      const reason = (reportDrafts[message.id] ?? '').trim()
                                      if (!reason) return
                                      report.mutate({ messageId: message.id, reason })
                                    }}
                                  >
                                    <label>Reason for report<textarea rows={2} maxLength={2000} required value={reportDrafts[message.id] ?? ''} onChange={event => setReportDrafts(current => ({ ...current, [message.id]: event.target.value }))} /></label>
                                    <p className="muted small">Reports capture an immutable evidence snapshot of this message for moderators.</p>
                                    <button className="secondary-button" disabled={report.isPending || !(reportDrafts[message.id] ?? '').trim()}>Submit report</button>
                                  </form>
                                </details>
                              </>
                            )}
                          </div>
                          {own && openReceipts[message.id] && <DeliveryReceipts messageId={message.id} />}
                        </article>
                      </li>
                    )
                  })}
                </ol>
                </div>
              )}
              <form
                className="message-composer"
                onSubmit={event => { event.preventDefault(); submitDraft() }}
              >
                <label>Message<textarea name="message" rows={3} maxLength={4000} value={draft} onChange={event => setDraft(event.target.value)} placeholder="Write a message to this conversation" /></label>
                <div className="button-row">
                  <button className="primary-button" disabled={send.isPending || !draft.trim()}>Send message</button>
                  <span className="muted small">Delivery and read state is recorded by the server when recipients next use the messaging API.</span>
                </div>
              </form>
            </div>
          )}
        </section>
      </div>

      <section className="panel" aria-label="Blocked members">
        <div className="panel-heading"><div><p className="eyebrow">Privacy</p><h3>Blocked members</h3></div><span className="count-badge">{blocks.data?.length ?? 0}</span></div>
        {blocks.isPending ? <p className="muted" aria-live="polite">Loading blocked members…</p> : blocks.isError ? <p className="error-text" role="alert">{blocks.error.message}</p> : blocks.data.length === 0 ? (
          <p className="muted">No members are blocked.</p>
        ) : (
          <div className="chip-row">
            {blocks.data.map(blocked => (
              <span className="chip" key={blocked.membershipId}>
                Member {shortId(blocked.membershipId)}
                <button type="button" className="text-button" disabled={unblock.isPending} onClick={() => unblock.mutate(blocked.membershipId)}>Unblock</button>
              </span>
            ))}
          </div>
        )}
      </section>

      {canModerate && <ModerationPanel />}
    </div>
  )
}

function NewConversation({ canBrowseDirectory, onCreated, onError }: { canBrowseDirectory: boolean; onCreated: (conversationId: string) => void; onError: (message: string) => void }) {
  const members = useQuery({ queryKey: ['members'], queryFn: fetchMembers, enabled: canBrowseDirectory })
  const [subject, setSubject] = useState('')
  const [directoryParticipantIds, setDirectoryParticipantIds] = useState<string[]>([])
  const [manualParticipants, setManualParticipants] = useState('')
  const manualParticipantIds = manualParticipants.split(/[,\s;]+/).map(value => value.trim()).filter(Boolean)
  const participantIds = canBrowseDirectory ? directoryParticipantIds : manualParticipantIds
  const create = useMutation({
    mutationFn: (input: { participantMembershipIds: string[]; subject?: string }) => createMessageConversation(input.participantMembershipIds, input.subject),
    onSuccess: conversation => {
      setDirectoryParticipantIds([])
      setManualParticipants('')
      setSubject('')
      onCreated(conversation.id)
    },
    onError: error => onError(error instanceof Error ? error.message : 'The conversation could not be created'),
  })

  return (
    <details className="new-conversation">
      <summary>Start a conversation</summary>
      <form
        className="form-grid"
        onSubmit={event => {
          event.preventDefault()
          if (participantIds.length === 0) {
            onError(canBrowseDirectory ? 'Select at least one participant before creating a conversation.' : 'Enter at least one participant membership ID before creating a conversation.')
            return
          }
          create.mutate({ participantMembershipIds: participantIds, subject: subject.trim() || undefined })
        }}
      >
        <label className="wide">Subject<input maxLength={240} value={subject} onChange={event => setSubject(event.target.value)} placeholder="Optional conversation subject" /></label>
        {canBrowseDirectory ? (
          <label className="wide">
            Participants
            <select
              multiple
              size={6}
              value={directoryParticipantIds}
              onChange={event => setDirectoryParticipantIds(Array.from(event.target.selectedOptions, option => option.value))}
            >
              {(members.data?.items ?? [])
                .filter(member => member.membershipId !== '' && member.membershipStatus === 'ACTIVE' && member.userStatus === 'ACTIVE')
                .map(member => (
                  <option key={member.membershipId} value={member.membershipId}>{member.displayName}{member.email ? ` · ${member.email}` : ''}</option>
                ))}
            </select>
          </label>
        ) : (
          <label className="wide">Participant membership IDs (comma-separated)<input value={manualParticipants} onChange={event => setManualParticipants(event.target.value)} placeholder="Membership UUIDs from your institution" /></label>
        )}
        {canBrowseDirectory && members.isPending && <p className="muted small" aria-live="polite">Loading tenant people…</p>}
        {canBrowseDirectory && members.isError && <p className="error-text" role="alert">{members.error.message}</p>}
        {!canBrowseDirectory && <p className="muted small wide">The tenant people directory requires Users View. Enter the membership identifiers an administrator gave you.</p>}
        <div className="form-actions">
          <button className="primary-button" disabled={create.isPending || participantIds.length === 0}>Create conversation</button>
        </div>
      </form>
    </details>
  )
}

function DeliveryReceipts({ messageId }: { messageId: string }) {
  const receipts = useQuery({
    queryKey: ['messaging-delivery', messageId],
    queryFn: () => fetchMessageDelivery(messageId),
    refetchInterval: 30_000,
  })
  if (receipts.isPending) return <p className="muted small" aria-live="polite">Loading receipts…</p>
  if (receipts.isError) return <p className="error-text" role="alert">{receipts.error.message}</p>
  if (receipts.data.length === 0) return <p className="muted small">No recipients recorded for this message.</p>
  return (
    <div className="receipt-list">
      {receipts.data.map(receipt => (
        <div className="receipt-row" key={receipt.recipientMembershipId}>
          <span>Member {shortId(receipt.recipientMembershipId)}</span>
          <span className={receipt.status === 'READ' ? 'chip-success' : receipt.status === 'DELIVERED' ? 'chip' : 'chip-warning'}>{receipt.status}</span>
          <span className="muted small">{receipt.readAt ? `Read ${new Date(receipt.readAt).toLocaleString()}` : receipt.deliveredAt ? `Delivered ${new Date(receipt.deliveredAt).toLocaleString()}` : 'Not yet fetched by the recipient'}</span>
        </div>
      ))}
    </div>
  )
}

function ModerationPanel() {
  const client = useQueryClient()
  const [resolution, setResolution] = useState<Record<string, string>>({})
  const [reportStatus, setReportStatus] = useState<Record<string, Exclude<MessageReportStatus, 'OPEN'>>>({})
  const [policyDraft, setPolicyDraft] = useState<MessagePolicyView | null>(null)
  const [status, setStatus] = useState<Status | null>(null)
  const reports = useQuery({ queryKey: ['messaging-reports'], queryFn: () => fetchMessageReports() })
  const policy = useQuery({ queryKey: ['messaging-policy'], queryFn: fetchMessagePolicy })
  const resolve = useMutation({
    mutationFn: (report: MessageReportView) => resolveMessageReport(
      report.id,
      reportStatus[report.id] ?? 'REVIEWED',
      (resolution[report.id] ?? '').trim(),
      report.version,
    ),
    onSuccess: () => {
      setStatus({ tone: 'info', message: 'Report resolution recorded.' })
      void client.invalidateQueries({ queryKey: ['messaging-reports'] })
    },
    onError: error => setStatus({ tone: 'error', message: error instanceof Error ? error.message : 'The report could not be resolved' }),
  })
  const savePolicy = useMutation({
    mutationFn: (draft: MessagePolicyView) => updateMessagePolicy({
      messagingEnabled: draft.messagingEnabled,
      allowStudentToStudent: draft.allowStudentToStudent,
      messageRetentionDays: draft.messageRetentionDays,
      reportRetentionDays: draft.reportRetentionDays,
      maxMessagesPerMinute: draft.maxMessagesPerMinute,
      maxReportsPerHour: draft.maxReportsPerHour,
      expectedVersion: draft.version,
    }),
    onSuccess: updated => {
      setPolicyDraft(updated)
      setStatus({ tone: 'info', message: 'Messaging policy saved.' })
      void client.invalidateQueries({ queryKey: ['messaging-policy'] })
    },
    onError: error => {
      setStatus({ tone: 'error', message: error instanceof Error ? error.message : 'The messaging policy could not be saved' })
      void client.invalidateQueries({ queryKey: ['messaging-policy'] })
    },
  })
  const currentPolicy = policyDraft ?? policy.data ?? null

  return (
    <section className="panel" aria-label="Messaging moderation">
      <div className="panel-heading"><div><p className="eyebrow">Governance</p><h3>Moderation and policy</h3></div><span className="chip">MESSAGING_MODERATE</span></div>
      {status && <p className={status.tone === 'error' ? 'error-text' : 'muted'} role={status.tone === 'error' ? 'alert' : 'status'}>{status.message}</p>}
      <div className="content-stack">
        <div>
          <p className="eyebrow">Reports</p>
          {reports.isPending ? <p className="muted" aria-live="polite">Loading reports…</p> : reports.isError ? <p className="error-text" role="alert">{reports.error.message}</p> : reports.data.items.length === 0 ? (
            <p className="muted">No message reports have been submitted.</p>
          ) : (
            <div className="table-wrap">
              <table aria-label="Message moderation reports">
                <thead><tr><th>Reported message</th><th>Reason</th><th>Status</th><th>Resolution</th><th /></tr></thead>
                <tbody>
                  {reports.data.items.map(report => (
                    <tr key={report.id}>
                      <td><strong>Member {shortId(report.messageSenderMembershipId)}</strong><span>{report.reportedMessageBody}</span></td>
                      <td>{report.reason}</td>
                      <td>{report.status}</td>
                      <td>
                        {report.status === 'OPEN' ? (
                          <div className="content-stack">
                            <label>Outcome<select value={reportStatus[report.id] ?? 'REVIEWED'} onChange={event => setReportStatus(current => ({ ...current, [report.id]: event.target.value as Exclude<MessageReportStatus, 'OPEN'> }))}>
                              {resolveStatuses.map(value => <option key={value} value={value}>{humanize(value)}</option>)}
                            </select></label>
                            <label>Resolution note<textarea rows={2} maxLength={2000} value={resolution[report.id] ?? ''} onChange={event => setResolution(current => ({ ...current, [report.id]: event.target.value }))} /></label>
                          </div>
                        ) : <span>{report.resolution ?? '—'}</span>}
                      </td>
                      <td>
                        {report.status === 'OPEN' && (
                          <button
                            className="secondary-button"
                            disabled={resolve.isPending || !(resolution[report.id] ?? '').trim()}
                            onClick={() => resolve.mutate(report)}
                          >Resolve report</button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div>
          <p className="eyebrow">Tenant policy</p>
          {policy.isPending ? <p className="muted" aria-live="polite">Loading messaging policy…</p> : policy.isError ? <p className="error-text" role="alert">{policy.error.message}</p> : currentPolicy && (
            <form className="form-grid" onSubmit={event => { event.preventDefault(); savePolicy.mutate(currentPolicy) }}>
              <label className="check-row wide"><input type="checkbox" checked={currentPolicy.messagingEnabled} onChange={event => setPolicyDraft({ ...currentPolicy, messagingEnabled: event.target.checked })} />Messaging enabled for this tenant</label>
              <label className="check-row wide"><input type="checkbox" checked={currentPolicy.allowStudentToStudent} onChange={event => setPolicyDraft({ ...currentPolicy, allowStudentToStudent: event.target.checked })} />Allow student-to-student conversations</label>
              <label>Message retention (days)<input type="number" min={1} max={3650} value={currentPolicy.messageRetentionDays} onChange={event => setPolicyDraft({ ...currentPolicy, messageRetentionDays: Number(event.target.value) })} /></label>
              <label>Report retention (days)<input type="number" min={1} max={7300} value={currentPolicy.reportRetentionDays} onChange={event => setPolicyDraft({ ...currentPolicy, reportRetentionDays: Number(event.target.value) })} /></label>
              <label>Messages per minute<input type="number" min={1} max={120} value={currentPolicy.maxMessagesPerMinute} onChange={event => setPolicyDraft({ ...currentPolicy, maxMessagesPerMinute: Number(event.target.value) })} /></label>
              <label>Reports per hour<input type="number" min={1} max={20} value={currentPolicy.maxReportsPerHour} onChange={event => setPolicyDraft({ ...currentPolicy, maxReportsPerHour: Number(event.target.value) })} /></label>
              <p className="muted small wide">Retention removes ordinary messages after the configured window; reported messages are kept for the longer report-retention period because they are moderation evidence. Resolving a report as Actioned redacts the message body while keeping the evidence snapshot for moderators.</p>
              <div className="form-actions"><button className="primary-button" disabled={savePolicy.isPending}>Save messaging policy</button></div>
            </form>
          )}
        </div>
      </div>
    </section>
  )
}

function shortId(value: string) { return value.slice(0, 8) }
function humanize(value: string) { return value.toLowerCase().replaceAll('_', ' ').replace(/\b\w/g, letter => letter.toUpperCase()) }
