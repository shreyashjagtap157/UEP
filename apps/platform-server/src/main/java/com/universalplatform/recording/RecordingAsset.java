package com.universalplatform.recording;

import com.universalplatform.storage.StorageProviderType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recording_asset", uniqueConstraints = @UniqueConstraint(name="uq_recording_asset_kind", columnNames={"tenant_id","recording_id","kind"}))
class RecordingAsset {
    @Id UUID id; @Column(nullable=false) UUID tenantId; @Column(nullable=false) UUID recordingId;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=20) RecordingAssetKind kind;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=32) StorageProviderType provider;
    @Column(nullable=false,length=2048) String locator; @Column(nullable=false,length=1024) String objectKey;
    @Column(nullable=false) String mimeType; @Column(nullable=false) long sizeBytes; @Column(nullable=false,length=64) String sha256;
    @Column(nullable=false) Instant createdAt;
    protected RecordingAsset() {}
    RecordingAsset(UUID id,UUID tenantId,UUID recordingId,RecordingAssetKind kind,StorageProviderType provider,String locator,String objectKey,String mimeType,long sizeBytes,String sha256,Instant createdAt){
        this.id=id;this.tenantId=tenantId;this.recordingId=recordingId;this.kind=kind;this.provider=provider;this.locator=locator;this.objectKey=objectKey;this.mimeType=mimeType;this.sizeBytes=sizeBytes;this.sha256=sha256;this.createdAt=createdAt;
    }
    RecordingAssetKind kind(){return kind;} StorageProviderType provider(){return provider;} String locator(){return locator;} String objectKey(){return objectKey;} String mimeType(){return mimeType;} long sizeBytes(){return sizeBytes;} String sha256(){return sha256;}
    void migrateTo(StorageProviderType provider,String locator){this.provider=provider;this.locator=locator;}
}
