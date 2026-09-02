package com.universalplatform.liveclass;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="live_class")
class LiveClass {
 @Id private UUID id; @Column(nullable=false) private UUID tenantId; @Column(nullable=false) private UUID classSessionId; @Column(nullable=false) private UUID batchId; @Column(nullable=false,length=160) private String roomName;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) private LiveClassStatus status;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) private AttendancePolicy attendancePolicy;
 @Column(nullable=false) private int minimumAttendanceSeconds; @Column(nullable=false) private int attendanceThresholdBasisPoints; @Column(nullable=false) private boolean lowBandwidth; @Column(nullable=false) private boolean chatEnabled;
 private Instant startedAt; private Instant endedAt; @Column(nullable=false,updatable=false) private Instant createdAt; @Version private long version;
 protected LiveClass(){}
 LiveClass(UUID id,UUID tenantId,UUID classSessionId,UUID batchId,String roomName,AttendancePolicy policy,int minimumAttendanceSeconds,int attendanceThresholdBasisPoints,boolean lowBandwidth,boolean chatEnabled,Instant createdAt){this.id=id;this.tenantId=tenantId;this.classSessionId=classSessionId;this.batchId=batchId;this.roomName=roomName;this.status=LiveClassStatus.SCHEDULED;this.attendancePolicy=policy;this.minimumAttendanceSeconds=minimumAttendanceSeconds;this.attendanceThresholdBasisPoints=attendanceThresholdBasisPoints;this.lowBandwidth=lowBandwidth;this.chatEnabled=chatEnabled;this.createdAt=createdAt;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} UUID classSessionId(){return classSessionId;} UUID batchId(){return batchId;} String roomName(){return roomName;} LiveClassStatus status(){return status;} AttendancePolicy attendancePolicy(){return attendancePolicy;} int minimumAttendanceSeconds(){return minimumAttendanceSeconds;} int attendanceThresholdBasisPoints(){return attendanceThresholdBasisPoints;} boolean lowBandwidth(){return lowBandwidth;} boolean chatEnabled(){return chatEnabled;} Instant startedAt(){return startedAt;} Instant endedAt(){return endedAt;} long version(){return version;}
 void start(Instant at){if(status!=LiveClassStatus.SCHEDULED) throw new IllegalStateException("Live class cannot start from "+status);status=LiveClassStatus.LIVE;startedAt=at;}
 void end(Instant at){if(status==LiveClassStatus.ENDED)return; if(status!=LiveClassStatus.LIVE)throw new IllegalStateException("Live class is not live");status=LiveClassStatus.ENDED;endedAt=at;}
 void cancel(){if(status==LiveClassStatus.LIVE)throw new IllegalStateException("Live class is live");status=LiveClassStatus.CANCELLED;}
}
