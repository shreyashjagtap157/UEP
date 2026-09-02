package com.universalplatform.security;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="api_credential", uniqueConstraints=@UniqueConstraint(name="uq_api_credential_hash", columnNames="key_hash"))
class ApiCredential {
    @Id private UUID id;
    @Column(nullable=false) private UUID tenantId;
    @Column(nullable=false) private UUID membershipId;
    @Column(nullable=false,length=120) private String name;
    @Column(nullable=false,length=128) private String keyHash;
    @Column(nullable=false,length=2000) private String scopes;
    @Column(nullable=false,updatable=false) private Instant createdAt;
    private Instant lastUsedAt;
    private Instant expiresAt;
    private Instant revokedAt;
    @Version private long version;
    protected ApiCredential() {}
    ApiCredential(UUID id,UUID tenantId,UUID membershipId,String name,String keyHash,String scopes,Instant createdAt,Instant expiresAt){this.id=id;this.tenantId=tenantId;this.membershipId=membershipId;this.name=name;this.keyHash=keyHash;this.scopes=scopes;this.createdAt=createdAt;this.expiresAt=expiresAt;}
    UUID id(){return id;} UUID tenantId(){return tenantId;} UUID membershipId(){return membershipId;} String name(){return name;} String keyHash(){return keyHash;} String scopes(){return scopes;} Instant expiresAt(){return expiresAt;} Instant revokedAt(){return revokedAt;} long version(){return version;}
    void used(Instant now){lastUsedAt=now;}
    void revoke(Instant now){revokedAt=now;}
    boolean active(Instant now){return revokedAt==null && (expiresAt==null || expiresAt.isAfter(now));}
}
