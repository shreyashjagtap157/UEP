package com.universalplatform.identity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "role_permission")
class RolePermission {
    @EmbeddedId
    private RolePermissionId id;

    protected RolePermission() {}

    RolePermission(RolePermissionId id) {
        this.id = id;
    }

    RolePermissionId id() { return id; }
}
