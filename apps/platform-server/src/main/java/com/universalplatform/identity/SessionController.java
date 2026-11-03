package com.universalplatform.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Session Management", description = "Endpoints for managing and revoking user sessions")
@RestController
@RequestMapping("/api/v1/identity/sessions")
class SessionController {
    private final SessionManagementService sessions;

    SessionController(SessionManagementService sessions) {
        this.sessions = sessions;
    }

    @Operation(
            summary = "List current user sessions",
            description = "Retrieves a paginated list of active sessions for the currently authenticated user. Accepts optional page and size query parameters."
    )
    @GetMapping("/me")
    IdentityPage<SessionManagementService.SessionView> mine(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return sessions.currentUserSessions(page, size);
    }

    @Operation(
            summary = "List sessions for membership",
            description = "Retrieves a paginated list of sessions for a specific membership ID passed in the path variable, with optional page and size query parameters."
    )
    @GetMapping("/memberships/{membershipId}")
    IdentityPage<SessionManagementService.SessionView> memberSessions(@PathVariable UUID membershipId, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return sessions.memberSessions(membershipId, page, size);
    }

    @Operation(
            summary = "Revoke own session",
            description = "Revokes a session belonging to the currently authenticated user by session ID. Accepts an optional RevokeSessionRequest body containing the revocation reason."
    )
    @DeleteMapping("/me/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeMine(@PathVariable UUID sessionId, @Valid @RequestBody(required = false) RevokeSessionRequest request) {
        sessions.revokeOwn(sessionId, request == null ? null : request.reason());
    }

    @Operation(
            summary = "Revoke session as administrator",
            description = "Revokes any user's session by session ID with administrative privileges. Accepts an optional RevokeSessionRequest body containing the revocation reason."
    )
    @DeleteMapping("/{sessionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revokeForAdministrator(@PathVariable UUID sessionId, @Valid @RequestBody(required = false) RevokeSessionRequest request) {
        sessions.revokeForAdministrator(sessionId, request == null ? null : request.reason());
    }

    record RevokeSessionRequest(@Size(max = 500) String reason) {}
}
