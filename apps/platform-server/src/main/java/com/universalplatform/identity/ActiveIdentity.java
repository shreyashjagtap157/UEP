package com.universalplatform.identity;

import java.util.UUID;

record ActiveIdentity(UserAccount user, TenantMembership membership) {
    UUID userId() { return user.id(); }
    UUID membershipId() { return membership.id(); }
}
