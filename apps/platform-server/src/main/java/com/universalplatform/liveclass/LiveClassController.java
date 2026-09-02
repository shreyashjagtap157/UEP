package com.universalplatform.liveclass;

import jakarta.validation.constraints.*; import java.util.*; import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/live-classes") class LiveClassController {
 private final LiveClassService service; LiveClassController(LiveClassService service){this.service=service;}
 @GetMapping LiveClassService.PageResult<LiveClassService.LiveClassView> list(@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="25") int size){return service.list(page,size);}
 @PostMapping LiveClassService.LiveClassView create(@RequestBody CreateRequest r){return service.create(r.classSessionId(),r.attendancePolicy(),r.minimumAttendanceSeconds(),r.attendanceThresholdBasisPoints(),r.lowBandwidth(),r.chatEnabled());}
 @PostMapping("/{id}/start") LiveClassService.LiveClassView start(@PathVariable UUID id){return service.start(id);}
 @PostMapping("/{id}/end") LiveClassService.LiveClassView end(@PathVariable UUID id){return service.end(id);}
 @PostMapping("/{id}/join") LiveClassService.TokenView join(@PathVariable UUID id,@RequestBody(required=false) JoinRequest r){return service.join(id,r==null?"BALANCED":r.clientProfile());}
 @PostMapping("/{id}/leave") void leave(@PathVariable UUID id){service.leave(id);}
 @GetMapping("/{id}/participants") LiveClassService.PageResult<LiveClassService.ParticipantView> participants(@PathVariable UUID id,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="50") int size){return service.participants(id,page,size);}
 @PostMapping("/{id}/participants/{membershipId}/mute") void mute(@PathVariable UUID id,@PathVariable UUID membershipId){service.mute(id,membershipId);}
 @PostMapping("/{id}/participants/{membershipId}/kick") void kick(@PathVariable UUID id,@PathVariable UUID membershipId,@RequestBody(required=false) ReasonRequest r){service.kick(id,membershipId,r==null?null:r.reason());}
 record CreateRequest(UUID classSessionId,AttendancePolicy attendancePolicy,@Min(0) @Max(86400) Integer minimumAttendanceSeconds,@Min(0) @Max(10000) Integer attendanceThresholdBasisPoints,boolean lowBandwidth,boolean chatEnabled){}
 record JoinRequest(String clientProfile){} record ReasonRequest(@Size(max=500) String reason){}
}
