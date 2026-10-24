package com.universalplatform.security;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ApiCredentialRepository extends JpaRepository<ApiCredential,UUID>{
    @org.springframework.data.jpa.repository.Query("select c from ApiCredential c, TenantMembership m, UserAccount u where c.keyHash=:hash and c.tenantId=m.tenantId and c.membershipId=m.id and m.userId=u.id and m.status=com.universalplatform.identity.MembershipStatus.ACTIVE and u.status=com.universalplatform.identity.UserStatus.ACTIVE")
    Optional<ApiCredential> findActiveByKeyHash(@org.springframework.data.repository.query.Param("hash") String hash);
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("""
            update ApiCredential c
            set c.lastUsedAt = :usedAt
            where c.id = :id
              and c.revokedAt is null
              and (c.expiresAt is null or c.expiresAt > :usedAt)
            """)
    int touchLastUsedAt(@org.springframework.data.repository.query.Param("id") UUID id,
                        @org.springframework.data.repository.query.Param("usedAt") java.time.Instant usedAt);

    List<ApiCredential> findAllByTenantIdAndMembershipIdOrderByCreatedAtDesc(UUID tenantId, UUID membershipId);
}
