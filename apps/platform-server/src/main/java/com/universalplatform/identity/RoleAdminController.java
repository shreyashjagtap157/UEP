package com.universalplatform.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/identity/roles")
class RoleAdminController {
    private final RoleManagementService roles;

    RoleAdminController(RoleManagementService roles) {
        this.roles = roles;
    }

    @GetMapping
    IdentityPage<RoleManagementService.RoleView> roles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return roles.list(page, size);
    }

    @PostMapping
    RoleManagementService.RoleView create(@Valid @RequestBody RoleRequest request) {
        return roles.createCustom(request.name(), request.description(), request.permissions());
    }

    @PutMapping("/{roleId}")
    RoleManagementService.RoleView update(@PathVariable UUID roleId, @Valid @RequestBody RoleRequest request) {
        return roles.updateCustom(roleId, request.name(), request.description(), request.permissions(), request.expectedVersion());
    }

    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID roleId) {
        roles.deleteCustom(roleId);
    }

    @GetMapping("/memberships/{membershipId}")
    List<RoleManagementService.AssignmentView> assignments(@PathVariable UUID membershipId) {
        return roles.assignmentsForMember(membershipId);
    }

    @PostMapping("/assignments")
    RoleManagementService.AssignmentView assign(@Valid @RequestBody AssignmentRequest request) {
        return roles.assign(request.membershipId(), request.roleId(), request.scopeKind(), request.scopeId());
    }

    @DeleteMapping("/assignments/{assignmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unassign(@PathVariable UUID assignmentId) {
        roles.unassign(assignmentId);
    }

    record RoleRequest(
            @NotBlank @Size(max = 120) String name,
            @Size(max = 500) String description,
            Set<PermissionKey> permissions,
            long expectedVersion) {}

    record AssignmentRequest(
            @NotNull UUID membershipId,
            @NotNull UUID roleId,
            @NotNull AssignmentScopeKind scopeKind,
            UUID scopeId) {}
}
