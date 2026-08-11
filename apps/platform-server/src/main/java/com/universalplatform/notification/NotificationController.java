package com.universalplatform.notification;

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

@RestController
@RequestMapping("/api/v1/notifications")
class NotificationController {
    private final NotificationService service;

    NotificationController(NotificationService service) { this.service = service; }

    @GetMapping
    NotificationPage<NotificationService.NotificationView> mine(@RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "25") int size) {
        return service.mine(page, size);
    }

    @GetMapping("/unread-count")
    NotificationService.UnreadCount unread() { return service.unreadCount(); }

    @PostMapping("/{id}/read")
    NotificationService.NotificationView markRead(@PathVariable UUID id) { return service.markRead(id); }

    @GetMapping("/preferences")
    List<NotificationService.PreferenceView> preferences() { return service.preferences(); }

    @PutMapping("/preferences/{eventType}")
    NotificationService.PreferenceView preference(@PathVariable NotificationEventType eventType,
                                                   @Valid @RequestBody PreferenceRequest request) {
        return service.updatePreference(eventType, request.inAppEnabled(), request.emailEnabled(), request.expectedVersion());
    }

    @GetMapping("/operations")
    NotificationService.OperationsView operations() { return service.operations(); }

    record PreferenceRequest(boolean inAppEnabled, boolean emailEnabled, Long expectedVersion) {}
}
