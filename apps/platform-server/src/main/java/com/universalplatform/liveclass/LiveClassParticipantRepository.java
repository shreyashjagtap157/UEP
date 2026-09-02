package com.universalplatform.liveclass;

import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.*;

interface LiveClassParticipantRepository extends JpaRepository<LiveClassParticipant,UUID>{ Optional<LiveClassParticipant> findByTenantIdAndLiveClassIdAndMembershipId(UUID tenantId,UUID liveClassId,UUID membershipId); List<LiveClassParticipant> findAllByTenantIdAndLiveClassId(UUID tenantId,UUID liveClassId); Page<LiveClassParticipant> findAllByTenantIdAndLiveClassId(UUID tenantId,UUID liveClassId,Pageable pageable);}
