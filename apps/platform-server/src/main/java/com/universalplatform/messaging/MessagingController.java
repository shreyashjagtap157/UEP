package com.universalplatform.messaging;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

@RestController
@RequestMapping("/api/v1/messaging")
class MessagingController {
    private final MessagingService service;

    MessagingController(MessagingService service) { this.service = service; }

    @GetMapping("/blocks")
    List<MessagingService.BlockView> blockedMemberships() {
        return service.blockedMemberships();
    }

    @PutMapping("/blocks/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void blockMembership(@PathVariable UUID membershipId) {
        service.blockMembership(membershipId);
    }

    @DeleteMapping("/blocks/{membershipId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unblockMembership(@PathVariable UUID membershipId) {
        service.unblockMembership(membershipId);
    }

    @GetMapping("/conversations")
    List<MessagingService.ConversationView> conversations(@RequestParam(defaultValue = "0") int page,
                                                          @RequestParam(defaultValue = "25") int size) {
        return service.list(page, size);
    }

    @PostMapping("/conversations")
    MessagingService.ConversationView create(@Valid @RequestBody CreateConversationRequest request) {
        return service.create(request.subject(), request.participantMembershipIds());
    }

    @GetMapping("/conversations/{conversationId}/messages")
    MessagingService.MessagePage messages(@PathVariable UUID conversationId,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "50") int size) {
        return service.messages(conversationId, page, size);
    }

    @PostMapping("/conversations/{conversationId}/messages")
    MessagingService.MessageView send(@PathVariable UUID conversationId, @Valid @RequestBody MessageRequest request) {
        return service.send(conversationId, request.body());
    }

    @PostMapping("/conversations/{conversationId}/read")
    void read(@PathVariable UUID conversationId) { service.markRead(conversationId); }

    @PutMapping("/messages/{messageId}")
    MessagingService.MessageView edit(@PathVariable UUID messageId, @Valid @RequestBody EditMessageRequest request) {
        return service.edit(messageId, request.body(), request.expectedVersion());
    }

    @DeleteMapping("/messages/{messageId}")
    void delete(@PathVariable UUID messageId, @RequestParam long expectedVersion) {
        service.delete(messageId, expectedVersion);
    }

    record CreateConversationRequest(@Size(max = 240) String subject,
                                      @NotEmpty @Size(max = 49) List<UUID> participantMembershipIds) {}
    record MessageRequest(@NotBlank @Size(max = 20000) String body) {}
    record EditMessageRequest(@NotBlank @Size(max = 20000) String body, long expectedVersion) {}
}
