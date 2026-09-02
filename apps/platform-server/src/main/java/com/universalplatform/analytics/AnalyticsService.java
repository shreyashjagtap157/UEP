package com.universalplatform.analytics;

import com.universalplatform.identity.AuthorizationService;
import com.universalplatform.identity.PermissionKey;
import com.universalplatform.security.TenantContext;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AnalyticsService {
    private final TenantContext tenant;
    private final AuthorizationService authorization;
    private final JdbcTemplate jdbc;
    private final Clock clock = Clock.systemUTC();

    AnalyticsService(TenantContext tenant, AuthorizationService authorization, JdbcTemplate jdbc) {
        this.tenant = tenant; this.authorization = authorization; this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    Overview overview(Instant from, Instant to) {
        requireView(); UUID tid = tenant.requireTenantId(); window(from, to);
        Long learners = jdbc.queryForObject("select count(*) from tenant_membership where tenant_id=? and status='ACTIVE'", Long.class, tid);
        Long enrollments = jdbc.queryForObject("select count(*) from enrollment where tenant_id=? and enrolled_at < ? and (ended_at is null or ended_at >= ?)", Long.class, tid, to, from);
        Attendance attendance = attendanceInternal(tid, from, to);
        Assessment assessment = assessmentInternal(tid, from, to);
        Recording recording = recordingInternal(tid, from, to);
        Finance finance = authorization.has(PermissionKey.FINANCE_VIEW) ? financeInternal(tid, from, to) : Finance.zero();
        Usage usage = usageInternal(tid);
        return new Overview(from, to, learners, enrollments, attendance, assessment, recording, finance, usage);
    }

    @Transactional(readOnly = true) AttendanceReport attendance(Instant from, Instant to) {
        requireView(); UUID tid=tenant.requireTenantId(); window(from,to); return new AttendanceReport(from,to,attendanceInternal(tid,from,to));
    }
    @Transactional(readOnly = true) AssessmentReport assessments(Instant from, Instant to) {
        requireView(); UUID tid=tenant.requireTenantId(); window(from,to); return new AssessmentReport(from,to,assessmentInternal(tid,from,to));
    }
    @Transactional(readOnly = true) QuestionReport questions(Instant from, Instant to) {
        requireView(); UUID tid=tenant.requireTenantId(); window(from,to);
        List<QuestionMetric> rows=jdbc.query("select q.id,q.title,qv.difficulty,count(distinct gi.id),coalesce(avg(case when gi.max_marks=0 then null else gi.final_score::numeric/gi.max_marks end),0),coalesce(sum(case when gi.final_score=gi.max_marks then 1 else 0 end),0) from question q join question_version qv on qv.question_id=q.id and qv.tenant_id=q.tenant_id left join assessment_question aq on aq.question_version_id=qv.id and aq.tenant_id=q.tenant_id left join grade_item gi on gi.assessment_question_id=aq.id and gi.tenant_id=aq.tenant_id join grade_revision gr on gr.id=gi.grade_revision_id and gr.tenant_id=gi.tenant_id and gr.status='PUBLISHED' where q.tenant_id=? and q.created_at < ? and qv.created_at < ? group by q.id,q.title,qv.difficulty order by q.title limit 500",
            (rs,n)->new QuestionMetric(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),rs.getLong(4),rs.getBigDecimal(5),rs.getLong(6)),tid,to,to);
        return new QuestionReport(from,to,rows);
    }
    @Transactional(readOnly = true) OperationsReport operations(Instant from, Instant to) {
        requirePermission(PermissionKey.OPERATIONS_VIEW); UUID tid=tenant.requireTenantId(); window(from,to);
        long auditEvents=n("select count(*) from audit_event where tenant_id=? and occurred_at>=? and occurred_at<?",tid,from,to);
        long notifications=n("select count(*) from notification where tenant_id=? and created_at>=? and created_at<?",tid,from,to);
        long deadletters=n("select count(*) from notification_outbox where tenant_id=? and status='DEAD_LETTER'",tid);
        long activeSessions=n("select count(*) from platform_session where tenant_id=? and revoked_at is null and (expires_at is null or expires_at>?)",tid,clock.instant());
        return new OperationsReport(from,to,auditEvents,notifications,deadletters,activeSessions);
    }
    @Transactional(readOnly = true) UsageReport usage() {
        requirePermission(PermissionKey.ANALYTICS_VIEW); UUID tid=tenant.requireTenantId(); return new UsageReport(usageInternal(tid));
    }
    @Transactional(readOnly = true) ForecastReport forecast() {
        requirePermission(PermissionKey.ANALYTICS_VIEW); UUID tid=tenant.requireTenantId();
        Instant now=clock.instant(); Instant start=now.minus(Duration.ofDays(30));
        long bytes30=n("select coalesce(sum(size_bytes),0) from recording where tenant_id=? and status in ('READY','ARCHIVED') and ready_at>=?",tid,start);
        long seconds30=n("select coalesce(sum(duration_seconds),0) from recording where tenant_id=? and status in ('READY','ARCHIVED') and ready_at>=?",tid,start);
        double dailyBytes=bytes30/30.0, dailyMinutes=seconds30/60.0/30.0;
        return new ForecastReport(now,30,bytes30,seconds30,dailyBytes,dailyMinutes,Math.round(dailyBytes*30),Math.round(dailyBytes*365));
    }
    @Transactional(readOnly = true) RecordingReport recordings(Instant from, Instant to) {
        requireView(); UUID tid=tenant.requireTenantId(); window(from,to); return new RecordingReport(from,to,recordingInternal(tid,from,to));
    }
    @Transactional(readOnly = true) FinanceReport finance(Instant from, Instant to) {
        requirePermission(PermissionKey.FINANCE_VIEW); UUID tid=tenant.requireTenantId(); window(from,to); return new FinanceReport(from,to,financeInternal(tid,from,to));
    }

    @Transactional(readOnly = true)
    String export(String report, Instant from, Instant to) {
        requirePermission(PermissionKey.REPORTS_EXPORT); window(from,to);
        StringBuilder out=new StringBuilder(); out.append("report,from,to\n"); out.append(csv(report)).append(',').append(from).append(',').append(to).append('\n');
        switch(report.toLowerCase(Locale.ROOT)) {
            case "attendance" -> { Attendance a=attendance(from,to).attendance(); out.append("present,partial,absent,excused,present_seconds\n").append(a.present()).append(',').append(a.partial()).append(',').append(a.absent()).append(',').append(a.excused()).append(',').append(a.presentSeconds()).append('\n'); }
            case "assessments" -> { Assessment a=assessments(from,to).assessment(); out.append("attempts,submitted,published,passed,average_percent\n").append(a.attempts()).append(',').append(a.submitted()).append(',').append(a.published()).append(',').append(a.passed()).append(',').append(a.averagePercent()).append('\n'); }
            case "recordings" -> { Recording r=recordings(from,to).recording(); out.append("count,total_seconds,total_bytes,ready,archived,failed\n").append(r.count()).append(',').append(r.totalSeconds()).append(',').append(r.totalBytes()).append(',').append(r.ready()).append(',').append(r.archived()).append(',').append(r.failed()).append('\n'); }
            case "finance" -> { Finance f=finance(from,to).finance(); out.append("invoiced,paid,outstanding,payment_count\n").append(f.invoiced()).append(',').append(f.paid()).append(',').append(f.outstanding()).append(',').append(f.paymentCount()).append('\n'); }
            default -> throw new IllegalArgumentException("Unsupported report: "+report);
        }
        return out.toString();
    }

    private Attendance attendanceInternal(UUID tid,Instant from,Instant to){
        Object[] c=jdbc.queryForObject("select coalesce(sum(case when status='PRESENT' then 1 else 0 end),0),coalesce(sum(case when status='PARTIAL' then 1 else 0 end),0),coalesce(sum(case when status='ABSENT' then 1 else 0 end),0),coalesce(sum(case when status='EXCUSED' then 1 else 0 end),0),coalesce(sum(present_seconds),0) from attendance_record where tenant_id=? and finalized_at>=? and finalized_at<?",(rs,n)->new Object[]{rs.getLong(1),rs.getLong(2),rs.getLong(3),rs.getLong(4),rs.getLong(5)},tid,from,to);
        long total=(long)c[0]+(long)c[1]+(long)c[2]+(long)c[3]; double rate=total==0?0:(double)(long)c[0]/total*100.0; return new Attendance((long)c[0],(long)c[1],(long)c[2],(long)c[3],(long)c[4],rate);
    }
    private Assessment assessmentInternal(UUID tid,Instant from,Instant to){
        long attempts=n("select count(*) from attempt where tenant_id=? and started_at>=? and started_at<?",tid,from,to);
        long submitted=n("select count(*) from attempt where tenant_id=? and submitted_at>=? and submitted_at<?",tid,from,to);
        long published=n("select count(distinct gr.attempt_id) from grade_revision gr where gr.tenant_id=? and gr.status='PUBLISHED' and gr.created_at>=? and gr.created_at<?",tid,from,to);
        long passed=n("select count(*) from grade_revision gr join attempt a on a.id=gr.attempt_id and a.tenant_id=gr.tenant_id where gr.tenant_id=? and gr.status='PUBLISHED' and gr.awarded_marks>=gr.max_marks*0.5 and gr.created_at>=? and gr.created_at<?",tid,from,to);
        BigDecimal avg=jdbc.queryForObject("select coalesce(avg(case when max_marks=0 then null else awarded_marks::numeric/max_marks*100 end),0) from grade_revision where tenant_id=? and status='PUBLISHED' and created_at>=? and created_at<?",BigDecimal.class,tid,from,to);
        return new Assessment(attempts,submitted,published,passed,avg==null?BigDecimal.ZERO:avg);
    }
    private Recording recordingInternal(UUID tid,Instant from,Instant to){
        long count=n("select count(*) from recording where tenant_id=? and requested_at>=? and requested_at<?",tid,from,to);
        long seconds=n("select coalesce(sum(duration_seconds),0) from recording where tenant_id=? and requested_at>=? and requested_at<?",tid,from,to);
        long bytes=n("select coalesce(sum(size_bytes),0) from recording where tenant_id=? and requested_at>=? and requested_at<?",tid,from,to);
        long ready=n("select count(*) from recording where tenant_id=? and status='READY' and requested_at>=? and requested_at<?",tid,from,to);
        long archived=n("select count(*) from recording where tenant_id=? and status='ARCHIVED' and requested_at>=? and requested_at<?",tid,from,to);
        long failed=n("select count(*) from recording where tenant_id=? and status='FAILED' and requested_at>=? and requested_at<?",tid,from,to);
        return new Recording(count,seconds,bytes,ready,archived,failed);
    }
    private Finance financeInternal(UUID tid,Instant from,Instant to){
        BigDecimal invoiced=jdbc.queryForObject("select coalesce(sum(total_amount),0) from invoice where tenant_id=? and created_at>=? and created_at<?",BigDecimal.class,tid,from,to); BigDecimal paid=jdbc.queryForObject("select coalesce(sum(paid_amount),0) from invoice where tenant_id=? and created_at>=? and created_at<?",BigDecimal.class,tid,from,to); long pc=n("select count(*) from payment where tenant_id=? and created_at>=? and created_at<?",tid,from,to); return new Finance(invoiced,paid,invoiced.subtract(paid),pc);
    }
    private Usage usageInternal(UUID tid){
        List<UsageItem> rows=jdbc.query("select ec.limit_key,ec.hard_limit,coalesce(uc.consumed,0) from entitlement_limit ec left join lateral (select consumed from usage_counter u where u.tenant_id=ec.tenant_id and u.limit_key=ec.limit_key order by period_start desc limit 1) uc on true where ec.tenant_id=? order by ec.limit_key",(rs,n)->new UsageItem(rs.getString(1),rs.getLong(2),rs.getLong(3)),tid); return new Usage(List.copyOf(rows));
    }
    private static String csv(String value){ return "\""+value.replace("\"", "\"\"")+"\""; }
    private long n(String sql,Object...args){Long v=jdbc.queryForObject(sql,Long.class,args); return v==null?0:v;}
    private void requireView(){ requirePermission(PermissionKey.ANALYTICS_VIEW); }
    private void requirePermission(PermissionKey p){ authorization.require(p); }
    private static void window(Instant from,Instant to){if(from==null||to==null||!from.isBefore(to))throw new IllegalArgumentException("from must be before to");if(Duration.between(from,to).compareTo(Duration.ofDays(366))>0)throw new IllegalArgumentException("Analytics window cannot exceed 366 days");}

    record Overview(Instant from,Instant to,long activeMemberships,long enrollments,Attendance attendance,Assessment assessment,Recording recording,Finance finance,Usage usage){}
    record AttendanceReport(Instant from,Instant to,Attendance attendance){}
    record AssessmentReport(Instant from,Instant to,Assessment assessment){}
    record QuestionReport(Instant from,Instant to,List<QuestionMetric> questions){}
    record OperationsReport(Instant from,Instant to,long auditEvents,long notifications,long notificationDeadLetters,long activeSessions){}
    record UsageReport(Usage usage){}
    record ForecastReport(Instant generatedAt,int historyDays,long recentBytes,long recentSeconds,double dailyBytes,double dailyMinutes,long projected30DayBytes,long projected365DayBytes){}
    record RecordingReport(Instant from,Instant to,Recording recording){}
    record FinanceReport(Instant from,Instant to,Finance finance){}
    record Attendance(long present,long partial,long absent,long excused,long presentSeconds,double presentRatePercent){}
    record Assessment(long attempts,long submitted,long published,long passed,BigDecimal averagePercent){}
    record Recording(long count,long totalSeconds,long totalBytes,long ready,long archived,long failed){}
    record Finance(BigDecimal invoiced,BigDecimal paid,BigDecimal outstanding,long paymentCount){ static Finance zero(){ return new Finance(BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,0); }}
    record Usage(List<UsageItem> items){}
    record UsageItem(String limitKey,long hardLimit,long consumed){}
    record QuestionMetric(UUID questionId,String title,String difficulty,long gradedAttempts,BigDecimal averageScoreRatio,long fullScoreCount){}
}
