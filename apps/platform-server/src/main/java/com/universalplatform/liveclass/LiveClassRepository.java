package com.universalplatform.liveclass;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;

interface LiveClassRepository extends JpaRepository<LiveClass,UUID>{ Optional<LiveClass> findByTenantIdAndId(UUID tenantId,UUID id); Optional<LiveClass> findByTenantIdAndClassSessionId(UUID tenantId,UUID classSessionId); Page<LiveClass> findAllByTenantId(UUID tenantId,Pageable pageable); Page<LiveClass> findAllByTenantIdAndBatchIdIn(UUID tenantId,Collection<UUID> batchIds,Pageable pageable);}
