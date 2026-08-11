package com.universalplatform.scheduling;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SchedulingIntegrationTest {
    private static final UUID TENANT = UUID.fromString("01901234-7000-7000-8000-000000000001");
    private static final UUID BRANCH_A = UUID.fromString("01901234-7000-7000-8000-000000000002");
    private static final UUID BRANCH_B = UUID.fromString("01901234-7000-7000-8000-000000000003");
    private static final UUID COURSE = UUID.fromString("01901234-7000-7000-8000-000000000004");
    private static final UUID BATCH_A = UUID.fromString("01901234-7000-7000-8000-000000000005");
    private static final UUID BATCH_B = UUID.fromString("01901234-7000-7000-8000-000000000014");
    private static final UUID ADMIN_USER = UUID.fromString("01901234-7000-7000-8000-000000000006");
    private static final UUID ADMIN_MEMBER = UUID.fromString("01901234-7000-7000-8000-000000000007");
    private static final UUID BRANCH_USER = UUID.fromString("01901234-7000-7000-8000-000000000008");
    private static final UUID BRANCH_MEMBER = UUID.fromString("01901234-7000-7000-8000-000000000009");
    private static final UUID TEACHER_USER = UUID.fromString("01901234-7000-7000-8000-000000000010");
    private static final UUID TEACHER_MEMBER = UUID.fromString("01901234-7000-7000-8000-000000000011");
    private static final UUID ADMIN_ROLE = UUID.fromString("01901234-7000-7000-8000-000000000012");
    private static final UUID BRANCH_ROLE = UUID.fromString("01901234-7000-7000-8000-000000000013");
    private static final UUID TEACHER_ROLE = UUID.fromString("01901234-7000-7000-8000-000000000015");
    private static final String ADMIN_SUBJECT = "schedule-admin";
    private static final String BRANCH_SUBJECT = "branch-admin";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'schedule-test', 'Schedule Test', 'ACTIVE')", TENANT);
        jdbc.update("INSERT INTO branch(id, tenant_id, code, display_name, timezone, status) VALUES (?, ?, 'A', 'Branch A', 'UTC', 'ACTIVE')", BRANCH_A, TENANT);
        jdbc.update("INSERT INTO branch(id, tenant_id, code, display_name, timezone, status) VALUES (?, ?, 'B', 'Branch B', 'UTC', 'ACTIVE')", BRANCH_B, TENANT);
        jdbc.update("INSERT INTO course(id, tenant_id, code, display_name, status) VALUES (?, ?, 'JAVA', 'Java', 'ACTIVE')", COURSE, TENANT);
        jdbc.update("INSERT INTO batch(id, tenant_id, course_id, branch_id, code, display_name, status) VALUES (?, ?, ?, ?, 'JAVA-A', 'Java A', 'ACTIVE')", BATCH_A, TENANT, COURSE, BRANCH_A);
        jdbc.update("INSERT INTO batch(id, tenant_id, course_id, branch_id, code, display_name, status) VALUES (?, ?, ?, ?, 'JAVA-B', 'Java B', 'ACTIVE')", BATCH_B, TENANT, COURSE, BRANCH_B);
        seedUser(ADMIN_USER, ADMIN_MEMBER, ADMIN_SUBJECT, "Schedule Admin", null);
        seedUser(BRANCH_USER, BRANCH_MEMBER, BRANCH_SUBJECT, "Branch Admin", BRANCH_A);
        seedUser(TEACHER_USER, TEACHER_MEMBER, "schedule-teacher", "Teacher", BRANCH_A);
        jdbc.update("INSERT INTO role_definition(id, tenant_id, name, system_managed) VALUES (?, ?, 'Schedule Admin', false)", ADMIN_ROLE, TENANT);
        jdbc.update("INSERT INTO role_definition(id, tenant_id, name, system_managed) VALUES (?, ?, 'Branch Schedule Admin', false)", BRANCH_ROLE, TENANT);
        jdbc.update("INSERT INTO role_definition(id, tenant_id, name, system_managed) VALUES (?, ?, 'Teacher Schedule Viewer', false)", TEACHER_ROLE, TENANT);
        jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, 'SCHEDULE_VIEW')", TEACHER_ROLE);
        for (String permission : new String[]{"SCHEDULE_VIEW", "SCHEDULE_MANAGE", "SCHEDULE_CONFLICT_OVERRIDE"}) {
            jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, ?)", ADMIN_ROLE, permission);
            jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, ?)", BRANCH_ROLE, permission);
        }
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)", TENANT, ADMIN_MEMBER, ADMIN_ROLE, ADMIN_SUBJECT);
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, scope_id, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'BRANCH', ?, ?)", TENANT, BRANCH_MEMBER, BRANCH_ROLE, BRANCH_A, ADMIN_SUBJECT);
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)", TENANT, TEACHER_MEMBER, TEACHER_ROLE, ADMIN_SUBJECT);
        jdbc.update("INSERT INTO teacher_assignment(id, tenant_id, membership_id, batch_id, assignment_role, status, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TEACHER', 'ACTIVE', ?)", TENANT, TEACHER_MEMBER, BATCH_A, ADMIN_SUBJECT);
    }

    @Test
    void conflictsRequireExplicitPrivilegedReasonAndArePersisted() throws Exception {
        mvc.perform(post("/api/v1/schedule/series").contentType(MediaType.APPLICATION_JSON).content(seriesJson(BRANCH_A, BATCH_A, false, null, "Java Class"))
                        .with(jwt().jwt(token -> token.subject(ADMIN_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.occurrences", hasSize(1)));

        mvc.perform(post("/api/v1/schedule/series").contentType(MediaType.APPLICATION_JSON).content(seriesJson(BRANCH_A, BATCH_A, false, null, "Conflicting Class"))
                        .with(jwt().jwt(token -> token.subject(ADMIN_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SCHEDULE_CONFLICT"))
                .andExpect(jsonPath("$.conflicts.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        mvc.perform(post("/api/v1/schedule/series").contentType(MediaType.APPLICATION_JSON).content(seriesJson(BRANCH_A, BATCH_A, true, "Approved shared-room exception", "Approved Conflict"))
                        .with(jwt().jwt(token -> token.subject(ADMIN_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.overriddenConflicts.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        Integer overrideCount = jdbc.queryForObject("SELECT count(*) FROM schedule_conflict_override WHERE tenant_id = ? AND reason = 'Approved shared-room exception'", Integer.class, TENANT);
        org.assertj.core.api.Assertions.assertThat(overrideCount).isEqualTo(1);
    }

    @Test
    void branchScopedAdministratorListsAndMutatesOnlyAuthorizedBranch() throws Exception {
        mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token.subject(BRANCH_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.permissions", hasSize(0)))
                .andExpect(jsonPath("$.primaryBranchPermissions", org.hamcrest.Matchers.hasItem("SCHEDULE_MANAGE")));

        mvc.perform(post("/api/v1/schedule/series").contentType(MediaType.APPLICATION_JSON).content(seriesJson(BRANCH_A, BATCH_A, false, null, "Branch A Class"))
                        .with(jwt().jwt(token -> token.subject(BRANCH_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk());

        mvc.perform(post("/api/v1/schedule/series").contentType(MediaType.APPLICATION_JSON).content(seriesJson(BRANCH_B, BATCH_B, false, null, "Branch B Class"))
                        .with(jwt().jwt(token -> token.subject(BRANCH_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden());

        mvc.perform(post("/api/v1/schedule/series").contentType(MediaType.APPLICATION_JSON).content(seriesJson(BRANCH_B, BATCH_B, false, null, "Branch B Class"))
                        .with(jwt().jwt(token -> token.subject(ADMIN_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/schedule/series?size=100").with(jwt().jwt(token -> token.subject(BRANCH_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].branchId").value(BRANCH_A.toString()));
    }

    @Test
    void teacherCalendarIncludesBatchAssignmentWithoutExplicitPrimaryTeacher() throws Exception {
        String body = "{" +
                "\"kind\":\"CLASS\"," +
                "\"title\":\"Assigned Batch Class\"," +
                "\"deliveryMode\":\"OFFLINE\"," +
                "\"branchId\":\"" + BRANCH_A + "\"," +
                "\"batchId\":\"" + BATCH_A + "\"," +
                "\"roomCode\":\"A-102\"," +
                "\"startLocal\":\"2026-09-02T10:00:00\"," +
                "\"durationMinutes\":60," +
                "\"recurrenceFrequency\":\"NONE\"," +
                "\"allowConflicts\":false}";

        mvc.perform(post("/api/v1/schedule/series").contentType(MediaType.APPLICATION_JSON).content(body)
                        .with(jwt().jwt(token -> token.subject(ADMIN_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk());

        mvc.perform(get("/api/v1/schedule/occurrences?from=2026-09-02T00:00:00Z&to=2026-09-03T00:00:00Z")
                        .with(jwt().jwt(token -> token.subject("schedule-teacher").claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Assigned Batch Class"));
    }

    private String seriesJson(UUID branchId, UUID batchId, boolean allow, String reason, String title) {
        return "{" +
                "\"kind\":\"CLASS\"," +
                "\"title\":\"" + title + "\"," +
                "\"deliveryMode\":\"OFFLINE\"," +
                "\"branchId\":\"" + branchId + "\"," +
                (batchId == null ? "" : "\"batchId\":\"" + batchId + "\",") +
                "\"primaryTeacherMembershipId\":\"" + TEACHER_MEMBER + "\"," +
                "\"roomCode\":\"A-101\"," +
                "\"startLocal\":\"2026-09-01T10:00:00\"," +
                "\"durationMinutes\":60," +
                "\"recurrenceFrequency\":\"NONE\"," +
                "\"allowConflicts\":" + allow +
                (reason == null ? "" : ",\"conflictOverrideReason\":\"" + reason + "\"") + "}";
    }

    private void seedUser(UUID userId, UUID membershipId, String subject, String name, UUID branchId) {
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, ?, ?, 'ACTIVE')", userId, subject, name);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, primary_branch_id, status) VALUES (?, ?, ?, ?, 'ACTIVE')", membershipId, TENANT, userId, branchId);
    }
}
