package com.universalplatform.integration;
import jakarta.persistence.*; import java.time.Instant; import java.util.*;
@Entity @Table(name="webhook_subscription", uniqueConstraints=@UniqueConstraint(name="uq_webhook_tenant_url",columnNames={"tenant_id","callback_url"}))
class WebhookSubscription {
 @Id private UUID id; @Column(nullable=false) private UUID tenantId; @Column(nullable=false,length=1000) private String callbackUrl; @Column(nullable=false,length=120) private String eventFilter; @Column(nullable=false,length=500) private String secretCiphertext; @Column(nullable=false) private boolean enabled; @Column(nullable=false,updatable=false) private Instant createdAt; private Instant lastDeliveredAt; private Instant lastFailedAt; @Column(length=1000) private String lastError; @Version private long version;
 protected WebhookSubscription(){}
 WebhookSubscription(UUID id,UUID tenantId,String callbackUrl,String eventFilter,String secretCiphertext,Instant now){this.id=id;this.tenantId=tenantId;this.callbackUrl=callbackUrl;this.eventFilter=eventFilter;this.secretCiphertext=secretCiphertext;this.enabled=true;this.createdAt=now;}
 UUID id(){return id;} UUID tenantId(){return tenantId;} String callbackUrl(){return callbackUrl;} String eventFilter(){return eventFilter;} String secretCiphertext(){return secretCiphertext;} boolean enabled(){return enabled;} long version(){return version;}
 void disable(){enabled=false;} void delivered(Instant now){lastDeliveredAt=now;lastError=null;} void failed(Instant now,String error){lastFailedAt=now;lastError=error==null?"Webhook delivery failed":error.substring(0,Math.min(1000,error.length()));}
}
