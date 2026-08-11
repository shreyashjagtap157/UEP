package com.universalplatform.identity;

import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class RolePermissionId implements Serializable {
    private UUID roleId;
    private String permissionKey;

    protected RolePermissionId() {}

    RolePermissionId(UUID roleId, PermissionKey permissionKey) {
        this.roleId = roleId;
        this.permissionKey = permissionKey.name();
    }

    UUID roleId() { return roleId; }
    String permissionKey() { return permissionKey; }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RolePermissionId that)) return false;
        return Objects.equals(roleId, that.roleId) && Objects.equals(permissionKey, that.permissionKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(roleId, permissionKey);
    }
}
