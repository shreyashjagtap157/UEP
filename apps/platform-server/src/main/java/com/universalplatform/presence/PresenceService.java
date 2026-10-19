package com.universalplatform.presence;
import com.universalplatform.audit.AuditService; import com.universalplatform.enrollment.EnrollmentDirectory; import com.universalplatform.identity.*; import com.universalplatform.liveclass.*; import com.universalplatform.scheduling.ScheduleDirectory; import com.universalplatform.security.*; import java.time.*; import java.util.*; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional;
@Service class PresenceService {
 private final TenantContext tenant; private final ActorContext actor; private final AuthorizationService auth; private final MembershipDirectory memberships; private final EnrollmentDirectory enrollment; private final LiveClassDirectory live; private final ScheduleDirectory schedules; private final AttendanceRepository attendance; private final AuditService audit; private final Clock clock=Clock.systemUTC();
 PresenceService(TenantContext tenant,ActorContext actor,AuthorizationService auth,MembershipDirectory memberships,EnrollmentDirectory enrollment,LiveClassDirectory live,ScheduleDirectory schedules,AttendanceRepository attendance,AuditService audit){this.tenant=tenant;this.actor=actor;this.auth=auth;this.memberships=memberships;this.enrollment=enrollment;this.live=live;this.schedules=schedules;this.attendance=attendance;this.audit=audit;}
 @Transactional void heartbeat(UUID liveClassId,String profile){auth.require(PermissionKey.LIVE_CLASS_VIEW);UUID mid=memberships.current().membershipId();live.heartbeat(liveClassId,mid);}
 @Transactional void leave(UUID liveClassId){auth.require(PermissionKey.LIVE_CLASS_VIEW);live.leave(liveClassId,memberships.current().membershipId());}
 @Transactional(readOnly=true) List<AttendanceView> list(UUID liveClassId){auth.require(PermissionKey.ATTENDANCE_VIEW);UUID tid=tenant.requireTenantId();return attendance.findAllByTenantIdAndLiveClassId(tid,liveClassId).stream().map(a->new AttendanceView(a.membershipId(),a.status(),a.presentSeconds(),a.finalizedAt(),a.source(),a.notes())).toList();}
 @Transactional void finalizeAttendance(UUID liveClassId){auth.require(PermissionKey.ATTENDANCE_MANAGE);var lc=live.require(liveClassId);
  // Fairness: the percentage denominator is the ACTUAL class span (first join .. class end/finalize),
  // not the scheduled window, so waits before the first participant join never dilute attendance.
  var snapshots=live.participantSnapshots(liveClassId);
  long duration=actualSessionSpanSeconds(lc,snapshots);
  Instant now=clock.instant();
  for(var p:snapshots){AttendanceStatus status=AttendanceCalculator.calculate(lc.attendancePolicy(),p.totalPresentSeconds(),duration,lc.minimumAttendanceSeconds(),lc.attendanceThresholdBasisPoints());var existing=attendance.findByTenantIdAndLiveClassIdAndMembershipId(tenant.requireTenantId(),liveClassId,p.membershipId());if(existing.isPresent())existing.get().revise(status,p.totalPresentSeconds(),now,"AUTO",null);else attendance.save(new AttendanceRecord(UUID.randomUUID(),tenant.requireTenantId(),liveClassId,p.membershipId(),status,p.totalPresentSeconds(),now,"AUTO"));}
  audit.record(tenant.requireTenantId(),actor.requireSubject(),"ATTENDANCE_FINALIZED","live_class",liveClassId.toString());}

 /** Actual session span in seconds: earliest participant join .. class end (or now when still running). Falls back to the live-class start, then the scheduled window. */
 private long actualSessionSpanSeconds(LiveClassDirectory.LiveClassReference lc,List<LiveClassDirectory.ParticipantSnapshot> snapshots){
  Instant now=clock.instant();
  Instant start=snapshots.stream().map(LiveClassDirectory.ParticipantSnapshot::joinedAt).filter(Objects::nonNull).min(Instant::compareTo).orElse(null);
  if(start==null)start=lc.startedAt();
  if(start==null){
   try{var session=schedules.requireClassSession(lc.classSessionId());start=session.startsAt();}catch(Exception ignored){/* no scheduled window available */}
  }
  if(start==null)return 0;
  Instant end=lc.endedAt()!=null&&lc.endedAt().isAfter(start)?lc.endedAt():now;
  if(!end.isAfter(start))return 0;
  return Math.max(1,Duration.between(start,end).getSeconds());
 }
 @Transactional void set(UUID liveClassId,UUID membershipId,AttendanceStatus status,String notes){auth.require(PermissionKey.ATTENDANCE_MANAGE);live.require(liveClassId);
  // Members who never joined (EXCUSED/ABSENT by moderator decision) are markable: the participant
  // prerequisite is relaxed; the unique (tenant, liveClass, membership) record keeps integrity.
  long presentSeconds=live.participantSnapshots(liveClassId).stream().filter(x->x.membershipId().equals(membershipId)).findFirst().map(LiveClassDirectory.ParticipantSnapshot::totalPresentSeconds).orElse(0L);
  var existing=attendance.findByTenantIdAndLiveClassIdAndMembershipId(tenant.requireTenantId(),liveClassId,membershipId);
  if(existing.isPresent())existing.get().revise(status,presentSeconds,clock.instant(),"MANUAL",notes);else attendance.save(new AttendanceRecord(UUID.randomUUID(),tenant.requireTenantId(),liveClassId,membershipId,status,presentSeconds,clock.instant(),"MANUAL"));
  audit.record(tenant.requireTenantId(),actor.requireSubject(),"ATTENDANCE_UPDATED","live_class",liveClassId.toString());}
 record AttendanceView(UUID membershipId,AttendanceStatus status,long presentSeconds,Instant finalizedAt,String source,String notes){} }
