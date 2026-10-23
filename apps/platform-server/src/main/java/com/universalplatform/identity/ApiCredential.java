package com.universalplatform.identity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name="api_credential", uniqueConstraints=@UniqueConstraint(name="uq_api_credential_hash", columnNames="key_hash"))
public class ApiCredential {
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
    public ApiCredential(UUID id,UUID tenantId,UUID membershipId,String name,String keyHash,String scopes,Instant createdAt,Instant expiresAt){this.id=id;this.tenantId=tenantId;this.membershipId=membershipId;this.name=name;this.keyHash=keyHash;this.scopes=scopes;this.createdAt=createdAt;this.expiresAt=expiresAt;}
    public UUID id(){return id;} public UUID tenantId(){return tenantId;} public UUID membershipId(){return membershipId;} public String name(){return name;} public String keyHash(){return keyHash;} public String scopes(){return scopes;} public Instant expiresAt(){return expiresAt;} public Instant revokedAt(){return revokedAt;} public long version(){return version;}
    public void used(Instant now){lastUsedAt=now;}
    public void revoke(Instant now){revokedAt=now;}
    public boolean active(Instant now){return revokedAt==null && (expiresAt==null || expiresAt.isAfter(now));}
}
