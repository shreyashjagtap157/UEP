package com.universalplatform.licensing;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import java.util.UUID;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface UsageCounterRepository extends JpaRepository<UsageCounter, UsageCounterId> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from UsageCounter u where u.id.tenantId=:t and u.id.limitKey=:k and u.id.periodStart=:s")
    Optional<UsageCounter> findForUpdate(@Param("t") UUID t, @Param("k") LimitKey k, @Param("s") Instant s);
}
