package com.universalplatform.notification;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Notifications", description = "Endpoints for reading member notifications, unread counts, delivery preferences, and operations metrics")
@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {
    private final NotificationService service;

    NotificationController(NotificationService service) { this.service = service; }

    @Operation(
        summary = "List current user notifications",
        description = "Retrieves a paginated list of notifications for the authenticated user. Accepts optional page (default 0) and size (default 25) query parameters."
    )
    @GetMapping
    NotificationPage<NotificationService.NotificationView> mine(@RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "25") int size) {
        return service.mine(page, size);
    }

    @Operation(
        summary = "Get unread notification count",
        description = "Retrieves the count of unread notifications for the currently authenticated user."
    )
    @GetMapping("/unread-count")
    NotificationService.UnreadCount unread() { return service.unreadCount(); }

    @Operation(
        summary = "Mark notification as read",
        description = "Marks a specific notification as read. Requires the notification ID path variable."
    )
    @PostMapping("/{id}/read")
    NotificationService.NotificationView markRead(@PathVariable UUID id) { return service.markRead(id); }

    @Operation(
        summary = "List notification preferences",
        description = "Retrieves current notification channel delivery preferences (in-app, email) for each event type."
    )
    @GetMapping("/preferences")
    List<NotificationService.PreferenceView> preferences() { return service.preferences(); }

    @Operation(
        summary = "Update notification preference",
        description = "Updates channel delivery preferences for a specified event type. Requires eventType path variable and PreferenceRequest body containing inAppEnabled, emailEnabled, and optional expectedVersion."
    )
    @PutMapping("/preferences/{eventType}")
    NotificationService.PreferenceView preference(@PathVariable NotificationEventType eventType,
                                                   @Valid @RequestBody PreferenceRequest request) {
        return service.updatePreference(eventType, request.inAppEnabled(), request.emailEnabled(), request.expectedVersion());
    }

    @Operation(
        summary = "Get notification operations metrics",
        description = "Retrieves operational metrics and delivery statistics for the notification subsystem."
    )
    @GetMapping("/operations")
    NotificationService.OperationsView operations() { return service.operations(); }

    record PreferenceRequest(boolean inAppEnabled, boolean emailEnabled, Long expectedVersion) {}
}
