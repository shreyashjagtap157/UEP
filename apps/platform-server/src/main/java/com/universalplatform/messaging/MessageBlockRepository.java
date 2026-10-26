package com.universalplatform.messaging;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface MessageBlockRepository extends JpaRepository<MessageBlock, UUID> {
    Optional<MessageBlock> findByTenantIdAndBlockerMembershipIdAndBlockedMembershipId(
            UUID tenantId, UUID blockerMembershipId, UUID blockedMembershipId);

    List<MessageBlock> findAllByTenantIdAndBlockerMembershipIdOrderByCreatedAtDesc(
            UUID tenantId, UUID blockerMembershipId);

    @Query("""
            select case when count(b) > 0 then true else false end
            from MessageBlock b
            where b.tenantId = :tenantId
              and ((b.blockerMembershipId = :firstMembershipId and b.blockedMembershipId = :secondMembershipId)
                or (b.blockerMembershipId = :secondMembershipId and b.blockedMembershipId = :firstMembershipId))
            """)
    boolean existsBetween(
            @Param("tenantId") UUID tenantId,
            @Param("firstMembershipId") UUID firstMembershipId,
            @Param("secondMembershipId") UUID secondMembershipId);

    @Query("""
            select case when count(b) > 0 then true else false end
            from MessageBlock b
            where b.tenantId = :tenantId
              and b.blockerMembershipId in :membershipIds
              and b.blockedMembershipId in :membershipIds
            """)
    boolean existsBlockBetweenParticipants(
            @Param("tenantId") UUID tenantId,
            @Param("membershipIds") Collection<UUID> membershipIds);
}
