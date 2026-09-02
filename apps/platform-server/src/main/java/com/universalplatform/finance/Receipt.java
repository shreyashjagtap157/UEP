package com.universalplatform.finance;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.*; import java.util.UUID;
@Entity @Table(name="payment_receipt")
class Receipt { @Id UUID id; @Column(nullable=false) UUID tenantId; @Column(nullable=false) UUID paymentId; @Column(nullable=false,unique=true,length=80) String receiptNumber; @Column(nullable=false,precision=19,scale=2) BigDecimal amount; @Column(nullable=false,length=3) String currency; Instant issuedAt;
 protected Receipt(){} Receipt(UUID id,UUID t,UUID p,String n,BigDecimal a,String c,Instant at){this.id=id;tenantId=t;paymentId=p;receiptNumber=n;amount=a;currency=c;issuedAt=at;} UUID id(){return id;} UUID tenantId(){return tenantId;} UUID paymentId(){return paymentId;} String receiptNumber(){return receiptNumber;} BigDecimal amount(){return amount;} String currency(){return currency;} Instant issuedAt(){return issuedAt;}
}
