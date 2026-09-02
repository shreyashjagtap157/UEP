package com.universalplatform.finance;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.LocalDate; import java.util.UUID;
@Entity @Table(name="fee_installment")
class Installment { @Id UUID id; @Column(nullable=false) UUID tenantId; @Column(nullable=false) UUID invoiceId; @Column(nullable=false) int sequenceNo; @Column(nullable=false,precision=19,scale=2) BigDecimal amount; @Column(nullable=false) LocalDate dueOn; @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) InstallmentStatus status; @Version long version;
 protected Installment(){} Installment(UUID id,UUID t,UUID i,int seq,BigDecimal a,LocalDate d){this.id=id;tenantId=t;invoiceId=i;sequenceNo=seq;amount=a;dueOn=d;status=InstallmentStatus.SCHEDULED;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} UUID invoiceId(){return invoiceId;} int sequenceNo(){return sequenceNo;} BigDecimal amount(){return amount;} LocalDate dueOn(){return dueOn;} InstallmentStatus status(){return status;} long version(){return version;} void paid(){status=InstallmentStatus.PAID;} void waived(){status=InstallmentStatus.WAIVED;}
}
