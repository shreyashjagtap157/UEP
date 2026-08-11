package com.universalplatform.content;
import com.universalplatform.storage.StorageObjectStatus; import com.universalplatform.storage.StorageProviderType;
import jakarta.persistence.*; import java.time.Instant; import java.util.UUID;
@Entity @Table(name="storage_object")
class StorageObject { @Id UUID id; @Column(nullable=false) UUID tenantId; @Enumerated(EnumType.STRING) @Column(nullable=false) StorageProviderType provider; @Column(nullable=false,length=2048) String locator; @Column(nullable=false,length=1024) String objectKey; @Column(nullable=false) String contentType; @Column(nullable=false) long sizeBytes; @Column(nullable=false,length=64) String sha256; @Enumerated(EnumType.STRING) @Column(nullable=false) StorageObjectStatus status; @Enumerated(EnumType.STRING) @Column(nullable=false) ScanStatus scanStatus; @Column(nullable=false) Instant createdAt; Instant deletedAt;
 void markDeleted(Instant now){status=StorageObjectStatus.DELETED;deletedAt=now;}
 protected StorageObject(){} StorageObject(UUID id,UUID tenantId,StorageProviderType provider,String locator,String objectKey,String contentType,long sizeBytes,String sha256,ScanStatus scanStatus,Instant now){this.id=id;this.tenantId=tenantId;this.provider=provider;this.locator=locator;this.objectKey=objectKey;this.contentType=contentType;this.sizeBytes=sizeBytes;this.sha256=sha256;this.status=StorageObjectStatus.AVAILABLE;this.scanStatus=scanStatus;this.createdAt=now;}
}
