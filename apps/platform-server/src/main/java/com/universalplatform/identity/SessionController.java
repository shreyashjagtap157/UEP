package com.universalplatform.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/sessions")
class SessionController {
    private final SessionManagementService sessions;

    SessionController(SessionManagementService sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/me")
    IdentityPage<SessionManagementService.SessionView> mine(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return sessions.currentUserSessions(page, size);
    }

    @GetMapping("/memberships/{membershipId}")
    IdentityPage<SessionManagementService.SessionView> memberSessions(@PathVariable UUID membershipId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return sessions.memberSessions(membershipId, page, size);
    }

    @DeleteMapping("/me/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeMine(@PathVariable UUID sessionId, @Valid @RequestBody(required = false) RevokeSessionRequest request) {
        sessions.revokeOwn(sessionId, request == null ? null : request.reason());
    }

    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeForAdministrator(@PathVariable UUID sessionId, @Valid @RequestBody(required = false) RevokeSessionRequest request) {
        sessions.revokeForAdministrator(sessionId, request == null ? null : request.reason());
    }

    record RevokeSessionRequest(@Size(max = 500) String reason) {}
}
