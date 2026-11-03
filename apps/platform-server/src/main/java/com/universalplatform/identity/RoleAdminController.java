package com.universalplatform.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
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

@Tag(name = "Role Administration", description = "Endpoints for managing custom roles, permissions, and role assignments")
@RestController
@RequestMapping("/api/v1/identity/roles")
class RoleAdminController {
    private final RoleManagementService roles;

    RoleAdminController(RoleManagementService roles) {
        this.roles = roles;
    }

    @Operation(
            summary = "List roles",
            description = "Retrieves a paginated list of defined roles. Accepts optional page and size query parameters."
    )
    @GetMapping
    IdentityPage<RoleManagementService.RoleView> roles(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return roles.list(page, size);
    }

    @Operation(
            summary = "Create custom role",
            description = "Creates a new custom role with the specified permissions. Requires a RoleRequest body containing a role name and permissions set."
    )
    @PostMapping
    RoleManagementService.RoleView create(@Valid @RequestBody RoleRequest request) {
        return roles.createCustom(request.name(), request.description(), request.permissions());
    }

    @Operation(
            summary = "Update custom role",
            description = "Updates an existing custom role's details and permissions. Requires the role ID in the path and a RoleRequest body including expectedVersion for optimistic locking."
    )
    @PutMapping("/{roleId}")
    RoleManagementService.RoleView update(@PathVariable UUID roleId, @Valid @RequestBody RoleRequest request) {
        return roles.updateCustom(roleId, request.name(), request.description(), request.permissions(), request.expectedVersion());
    }

    @Operation(
            summary = "Delete custom role",
            description = "Deletes a custom role by its unique identifier passed in the path variable."
    )
    @DeleteMapping("/{roleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable UUID roleId) {
        roles.deleteCustom(roleId);
    }

    @Operation(
            summary = "List role assignments for member",
            description = "Retrieves a paginated list of role assignments for a given membership ID specified in the path variable, with optional page and size parameters."
    )
    @GetMapping("/memberships/{membershipId}")
    IdentityPage<RoleManagementService.AssignmentView> assignments(@PathVariable UUID membershipId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return roles.assignmentsForMember(membershipId, page, size);
    }

    @Operation(
            summary = "Assign role to member",
            description = "Assigns a role to a member with optional scope kind and scope ID. Requires an AssignmentRequest body."
    )
    @PostMapping("/assignments")
    RoleManagementService.AssignmentView assign(@Valid @RequestBody AssignmentRequest request) {
        return roles.assign(request.membershipId(), request.roleId(), request.scopeKind(), request.scopeId());
    }

    @Operation(
            summary = "Unassign role",
            description = "Removes an assigned role by its assignment ID passed in the path variable."
    )
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
