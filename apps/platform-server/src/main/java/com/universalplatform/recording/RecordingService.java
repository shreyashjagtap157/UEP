package com.universalplatform.recording;

import com.universalplatform.audit.AuditService;
import com.universalplatform.identity.*;
import com.universalplatform.enrollment.EnrollmentDirectory;
import com.universalplatform.liveclass.LiveClassDirectory;
import com.universalplatform.notification.NotificationEventType;
import com.universalplatform.notification.NotificationPublisher;
import com.universalplatform.security.*;
import com.universalplatform.storage.*;
import java.io.InputStream;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class RecordingService {
    private final TenantContext tenants; private final ActorContext actors; private final AuthorizationService auth; private final MembershipDirectory memberships; private final EnrollmentDirectory enrollment;
    private final LiveClassDirectory liveClasses; private final RecordingRepository recordings; private final RecordingAssetRepository assets; private final RecordingStoragePolicyRepository policies;
    private final RecordingMediaProvider media; private final RecordingThumbnailService thumbnails; private final StorageAdapterRegistry adapters; private final AuditService audit; private final NotificationPublisher notifications;
    private final Path processingRoot; private final byte[] playbackSecret; private final Clock clock=Clock.systemUTC();
    RecordingService(TenantContext tenants,ActorContext actors,AuthorizationService auth,MembershipDirectory memberships,EnrollmentDirectory enrollment,LiveClassDirectory liveClasses,RecordingRepository recordings,
                     RecordingAssetRepository assets,RecordingStoragePolicyRepository policies,RecordingMediaProvider media,RecordingThumbnailService thumbnails,StorageAdapterRegistry adapters,
                     AuditService audit,NotificationPublisher notifications,@Value("${platform.recording.processing-root:./var/recordings}")String processingRoot,@Value("${platform.recording.playback-secret:change-me-in-production}")String playbackSecret){this.tenants=tenants;this.actors=actors;this.auth=auth;this.memberships=memberships;this.liveClasses=liveClasses;this.recordings=recordings;this.assets=assets;this.policies=policies;this.media=media;this.thumbnails=thumbnails;this.adapters=adapters;this.audit=audit;this.notifications=notifications;this.processingRoot=Path.of(processingRoot).toAbsolutePath().normalize();this.playbackSecret=playbackSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8);}

    @Transactional
    RecordingView request(UUID liveClassId,RecordingQualityPreset quality){auth.require(PermissionKey.RECORDINGS_MANAGE);UUID t=tenants.requireTenantId();var lc=liveClasses.require(liveClassId);
        if(lc.status()!=com.universalplatform.liveclass.LiveClassStatus.LIVE)throw new IllegalStateException("Recording can only be requested while the live classroom is live");
        if(recordings.findByTenantIdAndLiveClassId(t,liveClassId).filter(r->r.status()!=RecordingProcessingStatus.DELETED).isPresent())throw new IllegalStateException("A recording already exists for this live class");
        var policy=policies.findById(t).orElseGet(()->new RecordingStoragePolicy(t,clock.instant()));
        Recording r=recordings.save(new Recording(UUID.randomUUID(),t,liveClassId,lc.classSessionId(),lc.roomName(),quality==null?RecordingQualityPreset.BALANCED:quality,policy.hotProvider,clock.instant()));
        audit.record(t,actors.requireSubject(),"RECORDING_REQUESTED","recording",r.id().toString()); return view(r);
    }

    @Transactional(readOnly=true)
    PageResult<RecordingView> list(int page,int size){auth.require(PermissionKey.RECORDINGS_VIEW);UUID t=tenants.requireTenantId();Pageable p=PageRequest.of(Math.max(0,page),Math.min(100,Math.max(1,size)),Sort.by(Sort.Direction.DESC,"requestedAt","id"));Page<Recording> rows=recordings.findAllByTenantId(t,p);return new PageResult<>(rows.getContent().stream().map(this::view).toList(),rows.getNumber(),rows.getSize(),rows.getTotalElements(),rows.getTotalPages());}

    @Transactional(readOnly=true)
    RecordingView get(UUID id){auth.require(PermissionKey.RECORDINGS_VIEW);return view(require(id));}

    @Transactional
    void stop(UUID id){auth.require(PermissionKey.RECORDINGS_MANAGE);Recording r=require(id);if(r.egressId()!=null&&(r.status()==RecordingProcessingStatus.RECORDING||r.status()==RecordingProcessingStatus.STARTING)){media.stop(r.egressId());audit.record(r.tenantId(),actors.requireSubject(),"RECORDING_STOP_REQUESTED","recording",r.id().toString());}}

    @Transactional
    PlaybackView playback(UUID id){auth.require(PermissionKey.RECORDINGS_VIEW);Recording r=require(id);if(!(r.status()==RecordingProcessingStatus.READY||r.status()==RecordingProcessingStatus.ARCHIVED))throw new IllegalStateException("Recording is not ready for playback");
        UUID mid=memberships.current().membershipId();String name=memberships.current().displayName();Instant expires=clock.instant().plusSeconds(300);return new PlaybackView("/api/v1/recordings/"+id+"/stream?token="+opaque(id,mid,expires.toEpochMilli()),expires,name,mid.toString(),clock.instant());}

    @Transactional(readOnly=true)
    InputStream openStream(UUID id,String token) throws java.io.IOException {Recording r=require(id);if(!(r.status()==RecordingProcessingStatus.READY||r.status()==RecordingProcessingStatus.ARCHIVED))throw new IllegalStateException("Recording is not ready");PlaybackToken parsed=parseToken(token);if(!parsed.recordingId().equals(id)||!parsed.membershipId().equals(memberships.current().membershipId())||parsed.expiresAt()<clock.instant().toEpochMilli())throw new SecurityException("Playback token is invalid or expired");if(!auth.has(PermissionKey.RECORDINGS_VIEW))throw new SecurityException("Playback access denied");return adapters.require(r.storageProvider()).open(r.storageLocator());
    }

    @Transactional
    StoragePolicyView policy(){auth.require(PermissionKey.RECORDINGS_VIEW);UUID t=tenants.requireTenantId();RecordingStoragePolicy p=policies.findById(t).orElseGet(()->new RecordingStoragePolicy(t,clock.instant()));return policyView(p);}

    @Transactional
    StoragePolicyView updatePolicy(int hotDays,int cacheDays,Integer retentionDays,StorageProviderType hotProvider,StorageProviderType cacheProvider,StorageProviderType archiveProvider,int grace,long expectedVersion){auth.require(PermissionKey.RECORDINGS_MANAGE);UUID t=tenants.requireTenantId();RecordingStoragePolicy p=policies.findById(t).orElseGet(()->new RecordingStoragePolicy(t,clock.instant()));if(p.version()!=expectedVersion&&policies.existsById(t))throw new IllegalStateException("Recording storage policy was modified");if(hotDays<0||cacheDays<0||retentionDays!=null&&retentionDays<1||grace<0)throw new IllegalArgumentException("Invalid recording retention policy");adapters.require(hotProvider);adapters.require(cacheProvider);adapters.require(archiveProvider);p.update(hotDays,cacheDays,retentionDays,hotProvider,cacheProvider,archiveProvider,grace,clock.instant());policies.save(p);audit.record(t,actors.requireSubject(),"RECORDING_STORAGE_POLICY_UPDATED","recording-storage-policy",t.toString());return policyView(p);}

    private StoragePolicyView policyView(RecordingStoragePolicy p){return new StoragePolicyView(p.hotCacheDays(),p.cacheDays(),p.retentionDays(),p.hotProvider(),p.cacheProvider(),p.archiveProvider(),p.deletedObjectGraceDays(),p.version());}

    Recording processRequested(Recording r){if(r.status()!=RecordingProcessingStatus.REQUESTED)return r;try{Files.createDirectories(processingRoot.resolve(r.tenantId().toString()).resolve(r.id().toString()));r.markStarting(clock.instant());String path="/recordings/"+r.tenantId()+"/"+r.id()+"/recording.mp4";var started=media.startRoomComposite(r.roomName(),r.qualityPreset(),path);r.start(started.egressId(),clock.instant());}catch(Exception e){r.fail(trim(e.getMessage()));}return r;}
    @Transactional void processOrReconcile(Recording r){if(r.status()==RecordingProcessingStatus.REQUESTED)processRequested(r);else if(r.status()==RecordingProcessingStatus.RECORDING||r.status()==RecordingProcessingStatus.STARTING)reconcile(r);}

    Recording reconcile(Recording r){if(r.egressId()==null)return r;try{var state=media.status(r.egressId());if(state.status().contains("ACTIVE"))return r;if(state.status().contains("STARTING")){return r;}
            if(state.status().contains("FAILED")||state.status().contains("ABORTED")||state.status().contains("LIMIT")){r.fail(state.error()==null?"LiveKit egress failed":state.error());return r;}
            if(state.status().contains("COMPLETE")){r.markFinalizing(clock.instant());Path video=localPath(r);if(!Files.exists(video)){r.fail("LiveKit completed without the expected recording file");return r;}finalizeFile(r,video,Math.max(0,state.durationNanos()/1_000_000_000L));}
        }catch(Exception e){r.fail(trim(e.getMessage()));}return r;}

    void archiveDue(Recording r){if(r.status()!=RecordingProcessingStatus.READY&&r.status()!=RecordingProcessingStatus.ARCHIVING)return;RecordingStoragePolicy p=policies.findById(r.tenantId()).orElse(null);if(p==null||r.readyAt()==null)return;Instant now=clock.instant();Instant cacheAt=r.readyAt().plus(Duration.ofDays(p.hotCacheDays()));Instant archiveAt=cacheAt.plus(Duration.ofDays(p.cacheDays()));try{if(r.storageTier()==RecordingStorageTier.HOT){if(now.isBefore(cacheAt))return;if(r.storageProvider()!=p.cacheProvider()){migrateAssets(r,p.cacheProvider());migrateMaster(r,p.cacheProvider());}else{migrateAssets(r,p.cacheProvider());}r.cached(p.cacheProvider(),r.storageLocator(),r.objectKey(),now);audit.record(r.tenantId(),"system","RECORDING_MOVED_TO_CACHE","recording",r.id().toString());return;}if(r.storageTier()==RecordingStorageTier.CACHE&&now.isBefore(archiveAt))return;if(r.status()==RecordingProcessingStatus.READY)r.markArchiving();if(r.storageProvider()!=p.archiveProvider()){migrateAssets(r,p.archiveProvider());migrateMaster(r,p.archiveProvider());}r.archived(p.archiveProvider(),r.storageLocator(),r.objectKey(),now);audit.record(r.tenantId(),"system","RECORDING_ARCHIVED","recording",r.id().toString());}catch(Exception e){r.restoreReady();}}

    private void migrateMaster(Recording r,StorageProviderType targetType)throws java.io.IOException{StorageAdapter src=adapters.require(r.storageProvider());StorageAdapter dst=adapters.require(targetType);if(r.storageProvider()==targetType)return;try(InputStream in=src.open(r.storageLocator())){String loc=dst.put(r.objectKey(),in,r.sizeBytes()==null?0:r.sizeBytes(),"video/mp4");String old=r.storageLocator();r.cached(targetType,loc,r.objectKey(),clock.instant());src.delete(old);}}
    private void migrateAssets(Recording r,StorageProviderType targetType)throws java.io.IOException{for(RecordingAsset a:assets.findAllByTenantIdAndRecordingId(r.tenantId(),r.id())){if(a.provider()==targetType)continue;StorageAdapter src=adapters.require(a.provider());StorageAdapter dst=adapters.require(targetType);try(InputStream in=src.open(a.locator())){String loc=dst.put(a.objectKey(),in,a.sizeBytes(),a.mimeType());String old=a.locator();a.migrateTo(targetType,loc);src.delete(old);}}}

    void purgeDue(Recording r){RecordingStoragePolicy p=policies.findById(r.tenantId()).orElse(null);if(p==null||p.retentionDays()==null||r.readyAt()==null||r.readyAt().plus(Duration.ofDays((long)p.retentionDays()+p.deletedObjectGraceDays())).isAfter(clock.instant()))return;try{adapters.require(r.storageProvider()).delete(r.storageLocator());for(RecordingAsset a:assets.findAllByTenantIdAndRecordingId(r.tenantId(),r.id())){try{adapters.require(a.provider()).delete(a.locator());}catch(Exception ignored){}}r.delete(clock.instant());audit.record(r.tenantId(),"system","RECORDING_PURGED","recording",r.id().toString());}catch(Exception ignored){}}

    private void finalizeFile(Recording r,Path video,long duration){try{long bytes=Files.size(video);String hash=RecordingThumbnailService.sha256(video);String key=r.tenantId()+"/recordings/"+r.id()+"/master.mp4";StorageAdapter target=adapters.require(r.storageProvider());try(InputStream in=Files.newInputStream(video)){String loc=target.put(key,in,bytes,"video/mp4");r.ready(loc,key,hash,bytes,duration,clock.instant());assets.save(new RecordingAsset(UUID.randomUUID(),r.tenantId(),r.id(),RecordingAssetKind.MASTER,r.storageProvider(),loc,key,"video/mp4",bytes,hash,clock.instant()));createThumbnail(r,video,target,key);}Files.deleteIfExists(video);Files.deleteIfExists(video.getParent());notifyReady(r);}catch(Exception e){r.fail(trim(e.getMessage()));}}
    private void createThumbnail(Recording r,Path video,StorageAdapter target,String masterKey){Path thumb=video.resolveSibling("thumbnail.jpg");try{thumbnails.create(video,thumb);long b=Files.size(thumb);String h=RecordingThumbnailService.sha256(thumb);String key=r.tenantId()+"/recordings/"+r.id()+"/thumbnail.jpg";try(InputStream in=Files.newInputStream(thumb)){String loc=target.put(key,in,b,"image/jpeg");assets.save(new RecordingAsset(UUID.randomUUID(),r.tenantId(),r.id(),RecordingAssetKind.THUMBNAIL,r.storageProvider(),loc,key,"image/jpeg",b,h,clock.instant()));}Files.deleteIfExists(thumb);}catch(Exception ignored){}}
    private void notifyReady(Recording r){try{Set<UUID> recipients=enrollment.activeMembershipIdsForBatch(liveClasses.require(r.liveClassId()).batchId());List<NotificationPublisher.Recipient> target=recipients.stream().limit(5000).map(id->new NotificationPublisher.Recipient(id,null)).toList();notifications.enqueue(new NotificationPublisher.NotificationRequest(r.tenantId(),NotificationEventType.RECORDING_READY,"recording",r.id(),"Class recording ready","The recording is ready for playback.",com.universalplatform.notification.NotificationPriority.NORMAL,"recording",r.id(),"recording-ready-"+r.id(),clock.instant(),target));}catch(Exception ignored){}}
    private Recording require(UUID id){return recordings.findByTenantIdAndId(tenants.requireTenantId(),id).orElseThrow(()->new IllegalArgumentException("Recording not found"));}
    private RecordingView view(Recording r){return new RecordingView(r.id(),r.liveClassId(),r.classSessionId(),r.qualityPreset(),r.status(),r.storageTier(),r.storageProvider(),r.sizeBytes(),r.durationSeconds(),r.requestedAt(),r.startedAt(),r.endedAt(),r.readyAt(),r.archivedAt(),r.failureReason,r.version());}
    private Path localPath(Recording r){return processingRoot.resolve(r.tenantId().toString()).resolve(r.id().toString()).resolve("recording.mp4");}
    private String opaque(UUID id,UUID membership,long exp){String payload=id+":"+membership+":"+exp;String sig=Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payload));return Base64.getUrlEncoder().withoutPadding().encodeToString((payload+":"+sig).getBytes(java.nio.charset.StandardCharsets.UTF_8));}
    private PlaybackToken parseToken(String token){try{String[] p=new String(Base64.getUrlDecoder().decode(token),java.nio.charset.StandardCharsets.UTF_8).split(":",4);String payload=p[0]+":"+p[1]+":"+p[2];String expected=Base64.getUrlEncoder().withoutPadding().encodeToString(hmac(payload));if(!MessageDigest.isEqual(expected.getBytes(java.nio.charset.StandardCharsets.UTF_8),p[3].getBytes(java.nio.charset.StandardCharsets.UTF_8)))throw new SecurityException("Playback token is invalid");return new PlaybackToken(UUID.fromString(p[0]),UUID.fromString(p[1]),Long.parseLong(p[2]));}catch(SecurityException e){throw e;}catch(Exception e){throw new SecurityException("Playback token is invalid");}}
    private byte[] hmac(String text){try{var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(playbackSecret,"HmacSHA256"));return mac.doFinal(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));}catch(Exception e){throw new IllegalStateException("Unable to sign playback token",e);}}
    private static String trim(String s){return s==null?"Recording processing failed":s.length()>900?s.substring(0,900):s;}
    record RecordingView(UUID id,UUID liveClassId,UUID classSessionId,RecordingQualityPreset qualityPreset,RecordingProcessingStatus status,RecordingStorageTier storageTier,StorageProviderType storageProvider,Long sizeBytes,Long durationSeconds,Instant requestedAt,Instant startedAt,Instant endedAt,Instant readyAt,Instant archivedAt,String failureReason,long version){}
    record PlaybackView(String streamUrl,Instant expiresAt,String watermarkName,String watermarkId,Instant issuedAt){}
    record StreamHandle(StorageProviderType provider,String locator,String fileName,Long sizeBytes){}
    record StoragePolicyView(int hotCacheDays,int cacheDays,Integer retentionDays,StorageProviderType hotProvider,StorageProviderType cacheProvider,StorageProviderType archiveProvider,int deletedObjectGraceDays,long version){}
    record PageResult<T>(List<T> items,int page,int size,long totalElements,int totalPages){}
    private record PlaybackToken(UUID recordingId,UUID membershipId,long expiresAt){}
}
