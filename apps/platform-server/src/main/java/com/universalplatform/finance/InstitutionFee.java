package com.universalplatform.finance;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="institution_fee")
class InstitutionFee {
 @Id UUID id; @Column(nullable=false) UUID tenantId; @Column(nullable=false,length=120) String name;
 @Column(nullable=false,precision=19,scale=2) BigDecimal amount; @Column(nullable=false,length=3) String currency;
 @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) FeeStatus status; @Version long version; Instant createdAt;
 protected InstitutionFee(){}
 InstitutionFee(UUID id,UUID tenantId,String name,BigDecimal amount,String currency,Instant at){this.id=id;this.tenantId=tenantId;this.name=name;this.amount=amount;this.currency=currency;this.status=FeeStatus.ACTIVE;this.createdAt=at;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} String name(){return name;} BigDecimal amount(){return amount;} String currency(){return currency;} FeeStatus status(){return status;} long version(){return version;}
 void update(String n,BigDecimal a,String c,FeeStatus s){name=n;amount=a;currency=c;status=s;}
}
