package com.universalplatform.liveclass;

import java.util.*;

public interface LiveClassDirectory {
 LiveClassReference require(UUID id);
 LiveClassReference requireForClassSession(UUID classSessionId);
 void heartbeat(UUID liveClassId, UUID membershipId);
 void leave(UUID liveClassId, UUID membershipId);
 java.util.List<ParticipantSnapshot> participantSnapshots(UUID liveClassId);
 record ParticipantSnapshot(UUID membershipId, ParticipantRole role, ParticipantStatus status, java.time.Instant joinedAt, java.time.Instant leftAt, long totalPresentSeconds, java.time.Instant lastHeartbeatAt) {}
 record LiveClassReference(UUID id,UUID classSessionId,UUID batchId,String roomName,LiveClassStatus status,AttendancePolicy attendancePolicy,int minimumAttendanceSeconds,int attendanceThresholdBasisPoints,boolean lowBandwidth,boolean chatEnabled,java.time.Instant startedAt,java.time.Instant endedAt){}
}
