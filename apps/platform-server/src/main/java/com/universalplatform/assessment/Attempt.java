package com.universalplatform.assessment;

import jakarta.persistence.Column; import jakarta.persistence.Entity; import jakarta.persistence.EnumType; import jakarta.persistence.Enumerated; import jakarta.persistence.Id; import jakarta.persistence.Table; import jakarta.persistence.Version;
import java.time.Instant; import java.util.UUID;

@Entity @Table(name="attempt")
class Attempt { @Id private UUID id; @Column(nullable=false) private UUID tenantId; @Column(nullable=false) private UUID assessmentVersionId; @Column(nullable=false) private UUID membershipId; @Column(nullable=false) private Instant startedAt; @Column(nullable=false) private Instant expiresAt; @Column(nullable=true) private Instant submittedAt; @Column(nullable=false,length=20) @Enumerated(EnumType.STRING) private AttemptStatus status; @Column(nullable=false) private int attemptNumber; @Version private long version;
 protected Attempt(){} Attempt(UUID id,UUID tenantId,UUID assessmentVersionId,UUID membershipId,Instant startedAt,Instant expiresAt,int attemptNumber){this.id=id;this.tenantId=tenantId;this.assessmentVersionId=assessmentVersionId;this.membershipId=membershipId;this.startedAt=startedAt;this.expiresAt=expiresAt;this.attemptNumber=attemptNumber;this.status=AttemptStatus.IN_PROGRESS;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} UUID assessmentVersionId(){return assessmentVersionId;} UUID membershipId(){return membershipId;} Instant startedAt(){return startedAt;} Instant expiresAt(){return expiresAt;} Instant submittedAt(){return submittedAt;} AttemptStatus status(){return status;} int attemptNumber(){return attemptNumber;} long version(){return version;}
 void submit(Instant now,AttemptStatus finalStatus){this.submittedAt=now;this.status=finalStatus;}
}
