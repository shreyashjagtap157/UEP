package com.universalplatform.security;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
interface ApiCredentialRepository extends JpaRepository<ApiCredential,UUID>{
    @org.springframework.data.jpa.repository.Query("select c from ApiCredential c, TenantMembership m, UserAccount u where c.keyHash=:hash and c.tenantId=m.tenantId and c.membershipId=m.id and m.userId=u.id and m.status=com.universalplatform.identity.MembershipStatus.ACTIVE and u.status=com.universalplatform.identity.UserStatus.ACTIVE")
    Optional<ApiCredential> findActiveByKeyHash(@org.springframework.data.repository.query.Param("hash") String hash);
    List<ApiCredential> findAllByTenantIdAndMembershipIdOrderByCreatedAtDesc(UUID tenantId, UUID membershipId);
}
