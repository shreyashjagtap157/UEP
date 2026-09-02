package com.universalplatform.recording;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recording")
class Recording {
    @Id UUID id;
    @Column(nullable = false) UUID tenantId;
    @Column(nullable = false) UUID liveClassId;
    @Column(nullable = false) UUID classSessionId;
    @Column(nullable = false, length = 160) String roomName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) RecordingQualityPreset qualityPreset;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) RecordingProcessingStatus status;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) RecordingStorageTier storageTier;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) com.universalplatform.storage.StorageProviderType storageProvider;
    @Column(length = 2048) String storageLocator;
    @Column(length = 1024) String objectKey;
    @Column(length = 64) String sha256;
    Long sizeBytes;
    Long durationSeconds;
    String egressId;
    @Column(nullable = false) Instant requestedAt;
    Instant startedAt;
    Instant endedAt;
    Instant readyAt;
    Instant archivedAt;
    Instant deletedAt;
    @Column(length = 1000) String failureReason;
    @Version long version;
    protected Recording() {}
    Recording(UUID id, UUID tenantId, UUID liveClassId, UUID classSessionId, String roomName, RecordingQualityPreset qualityPreset,
              com.universalplatform.storage.StorageProviderType provider, Instant requestedAt) {
        this.id=id; this.tenantId=tenantId; this.liveClassId=liveClassId; this.classSessionId=classSessionId; this.roomName=roomName;
        this.qualityPreset=qualityPreset; this.status=RecordingProcessingStatus.REQUESTED; this.storageTier=RecordingStorageTier.HOT;
        this.storageProvider=provider; this.requestedAt=requestedAt;
    }
    UUID id(){return id;} UUID tenantId(){return tenantId;} UUID liveClassId(){return liveClassId;} UUID classSessionId(){return classSessionId;}
    String roomName(){return roomName;} RecordingQualityPreset qualityPreset(){return qualityPreset;} RecordingProcessingStatus status(){return status;}
    RecordingStorageTier storageTier(){return storageTier;} com.universalplatform.storage.StorageProviderType storageProvider(){return storageProvider;}
    String storageLocator(){return storageLocator;} String objectKey(){return objectKey;} String sha256(){return sha256;} Long sizeBytes(){return sizeBytes;}
    Long durationSeconds(){return durationSeconds;} String egressId(){return egressId;} Instant requestedAt(){return requestedAt;} Instant startedAt(){return startedAt;}
    Instant endedAt(){return endedAt;} Instant readyAt(){return readyAt;} Instant archivedAt(){return archivedAt;} long version(){return version;}
    void markStarting(Instant now){if(status!=RecordingProcessingStatus.REQUESTED)throw new IllegalStateException("Recording cannot start from "+status);status=RecordingProcessingStatus.STARTING;failureReason=null;}
    void start(String egressId, Instant now){if(status!=RecordingProcessingStatus.STARTING&&status!=RecordingProcessingStatus.FAILED)throw new IllegalStateException("Recording cannot start from "+status);this.egressId=egressId;this.status=RecordingProcessingStatus.RECORDING;this.startedAt=now;this.failureReason=null;}
    void markFinalizing(Instant now){if(status==RecordingProcessingStatus.RECORDING||status==RecordingProcessingStatus.STARTING)status=RecordingProcessingStatus.FINALIZING;endedAt=now;}
    void ready(String locator,String key,String hash,long bytes,long duration,Instant now){this.storageLocator=locator;this.objectKey=key;this.sha256=hash;this.sizeBytes=bytes;this.durationSeconds=Math.max(0,duration);this.status=RecordingProcessingStatus.READY;this.readyAt=now;}
    void cached(com.universalplatform.storage.StorageProviderType provider,String locator,String key,Instant now){this.storageProvider=provider;this.storageLocator=locator;this.objectKey=key;this.storageTier=RecordingStorageTier.CACHE;this.status=RecordingProcessingStatus.READY;}
    void archived(com.universalplatform.storage.StorageProviderType provider,String locator,String key,Instant now){this.storageProvider=provider;this.storageLocator=locator;this.objectKey=key;this.storageTier=RecordingStorageTier.ARCHIVE;this.status=RecordingProcessingStatus.ARCHIVED;this.archivedAt=now;}
    void fail(String reason){this.status=RecordingProcessingStatus.FAILED;this.failureReason=reason;}
    void markArchiving(){if(status==RecordingProcessingStatus.READY)status=RecordingProcessingStatus.ARCHIVING;}
    void restoreReady(){if(status==RecordingProcessingStatus.ARCHIVING)status=RecordingProcessingStatus.READY;}
    void delete(Instant now){this.status=RecordingProcessingStatus.DELETED;this.deletedAt=now;}
}
