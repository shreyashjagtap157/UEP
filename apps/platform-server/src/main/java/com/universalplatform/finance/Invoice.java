package com.universalplatform.finance;
import jakarta.persistence.*; import java.math.BigDecimal; import java.time.*; import java.util.UUID;
@Entity @Table(name="invoice")
class Invoice { @Id UUID id; @Column(nullable=false) UUID tenantId; @Column(nullable=false) UUID membershipId; @Column(nullable=false,unique=true,length=80) String invoiceNumber; @Column(nullable=false,precision=19,scale=2) BigDecimal totalAmount; @Column(nullable=false,precision=19,scale=2) BigDecimal paidAmount; @Column(nullable=false,length=3) String currency; @Enumerated(EnumType.STRING) @Column(nullable=false,length=24) InvoiceStatus status; LocalDate issuedOn; LocalDate dueOn; Instant createdAt; @Version long version;
 protected Invoice(){} Invoice(UUID id,UUID t,UUID m,String n,BigDecimal total,String c,LocalDate due,Instant at){this.id=id;tenantId=t;membershipId=m;invoiceNumber=n;totalAmount=total;paidAmount=BigDecimal.ZERO;currency=c;status=InvoiceStatus.DRAFT;dueOn=due;createdAt=at;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} UUID membershipId(){return membershipId;} String invoiceNumber(){return invoiceNumber;} BigDecimal totalAmount(){return totalAmount;} BigDecimal paidAmount(){return paidAmount;} String currency(){return currency;} InvoiceStatus status(){return status;} LocalDate issuedOn(){return issuedOn;} LocalDate dueOn(){return dueOn;} long version(){return version;}
 void issue(LocalDate d){if(status!=InvoiceStatus.DRAFT)throw new IllegalStateException("Invoice is not draft");issuedOn=d;status=InvoiceStatus.ISSUED;}
 void applyPayment(BigDecimal amount,LocalDate on){paidAmount=paidAmount.add(amount); if(paidAmount.compareTo(totalAmount)>=0){paidAmount=totalAmount;status=InvoiceStatus.PAID;} else status=InvoiceStatus.PARTIALLY_PAID;}
 void overdue(LocalDate today){if((status==InvoiceStatus.ISSUED||status==InvoiceStatus.PARTIALLY_PAID)&&dueOn.isBefore(today))status=InvoiceStatus.OVERDUE;}
 void voidInvoice(){if(status==InvoiceStatus.PAID)throw new IllegalStateException("Paid invoice cannot be voided");status=InvoiceStatus.VOID;}
}
