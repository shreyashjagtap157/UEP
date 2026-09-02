package com.universalplatform.recording;

import com.universalplatform.storage.StorageProviderType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.io.InputStream;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@RestController
@RequestMapping("/api/v1")
class RecordingController {
    private final RecordingService service;
    RecordingController(RecordingService service){this.service=service;}
    @GetMapping("/recordings") RecordingService.PageResult<RecordingService.RecordingView> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="25")int size){return service.list(page,size);}
    @GetMapping("/recordings/{id}") RecordingService.RecordingView get(@PathVariable UUID id){return service.get(id);}
    @PostMapping("/live-classes/{liveClassId}/recordings") RecordingService.RecordingView request(@PathVariable UUID liveClassId,@Valid @RequestBody Request r){return service.request(liveClassId,r.qualityPreset);}
    @PostMapping("/recordings/{id}/stop") @ResponseStatus(HttpStatus.ACCEPTED) void stop(@PathVariable UUID id){service.stop(id);}
    @PostMapping("/recordings/{id}/playback") RecordingService.PlaybackView playback(@PathVariable UUID id){return service.playback(id);}
    @GetMapping("/recordings/{id}/stream") ResponseEntity<StreamingResponseBody> stream(@PathVariable UUID id,@RequestParam String token)throws Exception{InputStream in=service.openStream(id,token);StreamingResponseBody body=out->{try(in){in.transferTo(out);}};return ResponseEntity.ok().contentType(MediaType.valueOf("video/mp4")).header(HttpHeaders.CONTENT_DISPOSITION,"inline").body(body);}
    @GetMapping("/recording-storage-policy") RecordingService.StoragePolicyView getPolicy(){return service.policy();}
    @PutMapping("/recording-storage-policy") RecordingService.StoragePolicyView policy(@Valid @RequestBody Policy r){return service.updatePolicy(r.hotCacheDays,r.cacheDays,r.retentionDays,r.hotProvider,r.cacheProvider,r.archiveProvider,r.deletedObjectGraceDays,r.expectedVersion);}
    record Request(@NotNull RecordingQualityPreset qualityPreset){}
    record Policy(@Min(0) int hotCacheDays,@Min(0) int cacheDays,Integer retentionDays,@NotNull StorageProviderType hotProvider,@NotNull StorageProviderType cacheProvider,@NotNull StorageProviderType archiveProvider,@Min(0) int deletedObjectGraceDays,long expectedVersion){}
}
