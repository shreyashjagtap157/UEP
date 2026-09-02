package com.universalplatform.recording;

import com.universalplatform.storage.StorageProviderType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "recording_storage_policy")
class RecordingStoragePolicy {
    @Id UUID tenantId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) StorageProviderType hotProvider;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) StorageProviderType cacheProvider;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 32) StorageProviderType archiveProvider;
    @Column(nullable = false) int hotCacheDays;
    @Column(nullable = false) int cacheDays;
    Integer retentionDays;
    @Column(nullable = false) int deletedObjectGraceDays;
    @Column(nullable = false) Instant updatedAt;
    @Version @Column(name = "row_version") long version;
    UUID tenantId(){return tenantId;} StorageProviderType hotProvider(){return hotProvider;} StorageProviderType cacheProvider(){return cacheProvider;} StorageProviderType archiveProvider(){return archiveProvider;} int hotCacheDays(){return hotCacheDays;} int cacheDays(){return cacheDays;} Integer retentionDays(){return retentionDays;} int deletedObjectGraceDays(){return deletedObjectGraceDays;} long version(){return version;}
    void update(int hotCacheDays,int cacheDays,Integer retentionDays,StorageProviderType hotProvider,StorageProviderType cacheProvider,StorageProviderType archiveProvider,int deletedObjectGraceDays,Instant now){this.hotCacheDays=hotCacheDays;this.cacheDays=cacheDays;this.retentionDays=retentionDays;this.hotProvider=hotProvider;this.cacheProvider=cacheProvider;this.archiveProvider=archiveProvider;this.deletedObjectGraceDays=deletedObjectGraceDays;this.updatedAt=now;}
    protected RecordingStoragePolicy() {}
    RecordingStoragePolicy(UUID tenantId, Instant now) {
        this.tenantId = tenantId; this.hotProvider = StorageProviderType.LOCAL; this.cacheProvider = StorageProviderType.LOCAL; this.archiveProvider = StorageProviderType.LOCAL;
        this.hotCacheDays = 14; this.cacheDays = 30; this.deletedObjectGraceDays = 30; this.updatedAt = now;
    }
}
