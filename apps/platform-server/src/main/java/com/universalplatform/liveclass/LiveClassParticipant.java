package com.universalplatform.liveclass;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="live_class_participant", uniqueConstraints=@UniqueConstraint(name="uq_live_class_participant", columnNames={"tenant_id","live_class_id","membership_id"}))
class LiveClassParticipant {
 @Id private UUID id; @Column(name="tenant_id",nullable=false) private UUID tenantId; @Column(name="live_class_id",nullable=false) private UUID liveClassId; @Column(name="membership_id",nullable=false) private UUID membershipId;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ParticipantRole role; @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private ParticipantStatus status;
 @Column(nullable=false) private Instant invitedAt; private Instant joinedAt; private Instant leftAt; @Column(nullable=false) private long totalPresentSeconds; private String lastClientProfile; private String moderationReason; private Instant lastHeartbeatAt; @Version private long version;
 protected LiveClassParticipant(){}
 LiveClassParticipant(UUID id,UUID tenantId,UUID liveClassId,UUID membershipId,ParticipantRole role,Instant now){this.id=id;this.tenantId=tenantId;this.liveClassId=liveClassId;this.membershipId=membershipId;this.role=role;this.status=ParticipantStatus.INVITED;this.invitedAt=now;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} UUID liveClassId(){return liveClassId;} UUID membershipId(){return membershipId;} ParticipantRole role(){return role;} ParticipantStatus status(){return status;} Instant joinedAt(){return joinedAt;} Instant leftAt(){return leftAt;} long totalPresentSeconds(){return totalPresentSeconds;} String lastClientProfile(){return lastClientProfile;} String moderationReason(){return moderationReason;} Instant lastHeartbeatAt(){return lastHeartbeatAt;} long version(){return version;}
 void join(Instant now,String profile){if(status==ParticipantStatus.KICKED||status==ParticipantStatus.BLOCKED)throw new IllegalStateException("Participant is not allowed to join"); if(status==ParticipantStatus.LEFT){joinedAt=now;leftAt=null;} else if(joinedAt==null)joinedAt=now; status=ParticipantStatus.JOINED; lastClientProfile=profile; lastHeartbeatAt=now;}
 void heartbeat(Instant now){ if(status==ParticipantStatus.JOINED||status==ParticipantStatus.MUTED) lastHeartbeatAt=now; }
 void leave(Instant now){if(joinedAt!=null && leftAt==null) totalPresentSeconds+=Math.max(0,java.time.Duration.between(joinedAt,now).getSeconds()); leftAt=now; if(status!=ParticipantStatus.KICKED&&status!=ParticipantStatus.BLOCKED)status=ParticipantStatus.LEFT;}
 void moderateMute(){if(status==ParticipantStatus.JOINED)status=ParticipantStatus.MUTED;}
 void moderateKick(String reason,Instant now){if(joinedAt!=null&&leftAt==null)totalPresentSeconds+=Math.max(0,java.time.Duration.between(joinedAt,now).getSeconds());leftAt=now;status=ParticipantStatus.KICKED;moderationReason=reason;}
}
