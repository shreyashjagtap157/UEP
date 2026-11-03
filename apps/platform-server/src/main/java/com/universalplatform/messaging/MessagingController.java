package com.universalplatform.messaging;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

@Tag(name = "Messaging", description = "Endpoints for direct/group conversations, message dispatch, reports, moderation policies, and member blocking")
@RestController
@RequestMapping("/api/v1/messaging")
class MessagingController {
    private final MessagingService service;

    MessagingController(MessagingService service) { this.service = service; }

    @Operation(
        summary = "Report message",
        description = "Submits a moderation report against a specific message. Requires message ID path variable and ReportMessageRequest body containing the reason."
    )
    @PostMapping("/messages/{messageId}/reports")
    MessagingService.MessageReportView reportMessage(@PathVariable UUID messageId,
                                                       @Valid @RequestBody ReportMessageRequest request) {
        return service.reportMessage(messageId, request.reason());
    }

    @Operation(
        summary = "List message reports",
        description = "Retrieves a paginated list of moderation reports for administrators. Accepts optional page (default 0) and size (default 25) query parameters."
    )
    @GetMapping("/reports")
    MessagingService.MessageReportPage reports(@RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "25") int size) {
        return service.listReports(page, size);
    }

    @Operation(
        summary = "Resolve message report",
        description = "Resolves or dismisses a message moderation report. Requires report ID path variable and ResolveMessageReportRequest body containing status, resolution note, and expectedVersion."
    )
    @PostMapping("/reports/{reportId}/resolve")
    MessagingService.MessageReportView resolveReport(@PathVariable UUID reportId,
                                                       @Valid @RequestBody ResolveMessageReportRequest request) {
        return service.resolveReport(reportId, request.status(), request.resolution(), request.expectedVersion());
    }

    @Operation(
        summary = "Get messaging policy",
        description = "Retrieves the workspace messaging policy including rate limits and retention settings."
    )
    @GetMapping("/policy")
    MessagingService.MessagePolicyView messagingPolicy() {
        return service.getPolicy();
    }

    @Operation(
        summary = "Update messaging policy",
        description = "Updates the workspace messaging policy. Requires UpdateMessagingPolicyRequest body containing messagingEnabled, allowStudentToStudent, messageRetentionDays, reportRetentionDays, maxMessagesPerMinute, maxReportsPerHour, and expectedVersion."
    )
    @PutMapping("/policy")
    MessagingService.MessagePolicyView updateMessagingPolicy(@Valid @RequestBody UpdateMessagingPolicyRequest request) {
        return service.updatePolicy(request.messagingEnabled(), request.allowStudentToStudent(),
                request.messageRetentionDays(), request.reportRetentionDays(), request.maxMessagesPerMinute(),
                request.maxReportsPerHour(), request.expectedVersion());
    }

    @Operation(
        summary = "List blocked memberships",
        description = "Retrieves a list of member memberships blocked by the authenticated user."
    )
    @GetMapping("/blocks")
    List<MessagingService.BlockView> blockedMemberships() {
        return service.blockedMemberships();
    }

    @Operation(
        summary = "Block membership",
        description = "Blocks messaging interactions from a specific member. Requires membership ID path variable."
    )
    @PutMapping("/blocks/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void blockMembership(@PathVariable UUID membershipId) {
        service.blockMembership(membershipId);
    }

    @Operation(
        summary = "Unblock membership",
        description = "Unblocks messaging interactions from a previously blocked member. Requires membership ID path variable."
    )
    @DeleteMapping("/blocks/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unblockMembership(@PathVariable UUID membershipId) {
        service.unblockMembership(membershipId);
    }

    @Operation(
        summary = "List user conversations",
        description = "Retrieves a paginated list of conversations for the authenticated user. Accepts optional page (default 0) and size (default 25) query parameters."
    )
    @GetMapping("/conversations")
    MessagingService.MessageConversationPage conversations(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "25") int size) {
        return service.list(page, size);
    }

    @Operation(
        summary = "Create conversation",
        description = "Initiates a new direct or group conversation. Requires CreateConversationRequest body containing participantMembershipIds and optional subject."
    )
    @PostMapping("/conversations")
    MessagingService.ConversationView create(@Valid @RequestBody CreateConversationRequest request) {
        return service.create(request.subject(), request.participantMembershipIds());
    }

    @Operation(
        summary = "List conversation messages",
        description = "Retrieves a paginated list of messages in a conversation. Requires conversation ID path variable and accepts optional page (default 0) and size (default 50) query parameters."
    )
    @GetMapping("/conversations/{conversationId}/messages")
    MessagingService.MessagePage messages(@PathVariable UUID conversationId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return service.messages(conversationId, page, size);
    }

    @Operation(
        summary = "Send message in conversation",
        description = "Sends a new message to a conversation. Requires conversation ID path variable and MessageRequest body containing body text."
    )
    @PostMapping("/conversations/{conversationId}/messages")
    MessagingService.MessageView send(@PathVariable UUID conversationId, @Valid @RequestBody MessageRequest request) {
        return service.send(conversationId, request.body());
    }

    @Operation(
        summary = "Get message delivery status",
        description = "Retrieves per-participant delivery and read status receipts for a specific message. Requires message ID path variable."
    )
    @GetMapping("/messages/{messageId}/delivery")
    List<MessagingService.DeliveryView> delivery(@PathVariable UUID messageId) {
        return service.delivery(messageId);
    }

    @Operation(
        summary = "Mark conversation messages as read",
        description = "Marks all unread messages within a conversation as read for the authenticated user. Requires conversation ID path variable."
    )
    @PostMapping("/conversations/{conversationId}/read")
    void read(@PathVariable UUID conversationId) { service.markRead(conversationId); }

    @Operation(
        summary = "Edit message",
        description = "Edits the body text of a previously sent message. Requires message ID path variable and EditMessageRequest body containing updated body and expectedVersion."
    )
    @PutMapping("/messages/{messageId}")
    MessagingService.MessageView edit(@PathVariable UUID messageId, @Valid @RequestBody EditMessageRequest request) {
        return service.edit(messageId, request.body(), request.expectedVersion());
    }

    @Operation(
        summary = "Delete message",
        description = "Deletes a message. Requires message ID path variable and expectedVersion query parameter."
    )
    @DeleteMapping("/messages/{messageId}")
    void delete(@PathVariable UUID messageId, @RequestParam long expectedVersion) {
        service.delete(messageId, expectedVersion);
    }

    record UpdateMessagingPolicyRequest(
            @NotNull Boolean messagingEnabled,
            @NotNull Boolean allowStudentToStudent,
            @NotNull @Min(1) @jakarta.validation.constraints.Max(3650) Integer messageRetentionDays,
            @NotNull @Min(1) @jakarta.validation.constraints.Max(7300) Integer reportRetentionDays,
            @NotNull @Min(1) @jakarta.validation.constraints.Max(120) Integer maxMessagesPerMinute,
            @NotNull @Min(1) @jakarta.validation.constraints.Max(20) Integer maxReportsPerHour,
            @NotNull @Min(0) Long expectedVersion) {}

    record ReportMessageRequest(@NotBlank @Size(max = 2000) String reason) {}
    record ResolveMessageReportRequest(@NotNull MessageReportStatus status,
                                       @NotBlank @Size(max = 2000) String resolution,
                                       @NotNull @Min(0) Long expectedVersion) {}

    record CreateConversationRequest(@Size(max = 240) String subject,
                                      @NotEmpty @Size(max = 49) List<UUID> participantMembershipIds) {}
    record MessageRequest(@NotBlank @Size(max = 20000) String body) {}
    record EditMessageRequest(@NotBlank @Size(max = 20000) String body, @NotNull @Min(0) Long expectedVersion) {}
}
