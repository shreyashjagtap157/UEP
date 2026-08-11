package com.universalplatform.enrollment;

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
class EnrollmentIntegrationTest {
    private static final UUID TENANT = UUID.fromString("01901234-6000-7000-8000-000000000001");
    private static final UUID ADMIN_USER = UUID.fromString("01901234-6000-7000-8000-000000000002");
    private static final UUID ADMIN_MEMBERSHIP = UUID.fromString("01901234-6000-7000-8000-000000000003");
    private static final UUID LEARNER_USER = UUID.fromString("01901234-6000-7000-8000-000000000004");
    private static final UUID LEARNER_MEMBERSHIP = UUID.fromString("01901234-6000-7000-8000-000000000005");
    private static final UUID TEACHER_USER = UUID.fromString("01901234-6000-7000-8000-000000000006");
    private static final UUID TEACHER_MEMBERSHIP = UUID.fromString("01901234-6000-7000-8000-000000000007");
    private static final UUID ADMIN_ROLE = UUID.fromString("01901234-6000-7000-8000-000000000008");
    private static final UUID COURSE = UUID.fromString("01901234-6000-7000-8000-000000000009");
    private static final UUID BATCH = UUID.fromString("01901234-6000-7000-8000-000000000010");
    private static final String ADMIN_SUBJECT = "academic-admin";
    private static final String LEARNER_SUBJECT = "learner";
    private static final String TEACHER_SUBJECT = "teacher";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'enrollment-test', 'Enrollment Test', 'ACTIVE')", TENANT);
        seedUser(ADMIN_USER, ADMIN_MEMBERSHIP, ADMIN_SUBJECT, "Academic Admin");
        seedUser(LEARNER_USER, LEARNER_MEMBERSHIP, LEARNER_SUBJECT, "Learner");
        seedUser(TEACHER_USER, TEACHER_MEMBERSHIP, TEACHER_SUBJECT, "Teacher");
        jdbc.update("INSERT INTO role_definition(id, tenant_id, system_key, name, system_managed) VALUES (?, ?, 'ACADEMIC_ADMINISTRATOR', 'Academic Administrator', true)", ADMIN_ROLE, TENANT);
        for (String permission : new String[]{"ACADEMICS_VIEW", "ENROLLMENTS_VIEW", "ENROLLMENTS_MANAGE", "TEACHING_ASSIGNMENTS_VIEW", "TEACHING_ASSIGNMENTS_MANAGE"}) {
            jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, ?)", ADMIN_ROLE, permission);
        }
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)", TENANT, ADMIN_MEMBERSHIP, ADMIN_ROLE, ADMIN_SUBJECT);
        jdbc.update("INSERT INTO course(id, tenant_id, code, display_name, status) VALUES (?, ?, 'JAVA', 'Java', 'ACTIVE')", COURSE, TENANT);
        jdbc.update("INSERT INTO batch(id, tenant_id, course_id, code, display_name, capacity, status) VALUES (?, ?, ?, 'JAVA-A', 'Java Batch A', 1, 'ACTIVE')", BATCH, TENANT, COURSE);
    }

    @Test
    void learnerDashboardIsSelfScopedAndBatchCapacityIsEnforced() throws Exception {
        mvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membershipId\":\"" + LEARNER_MEMBERSHIP + "\",\"batchId\":\"" + BATCH + "\"}")
                        .with(jwt().jwt(token -> token.subject(ADMIN_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Learner"));

        mvc.perform(get("/api/v1/enrollments/me")
                        .with(jwt().jwt(token -> token.subject(LEARNER_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].membershipId").value(LEARNER_MEMBERSHIP.toString()));

        mvc.perform(post("/api/v1/enrollments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membershipId\":\"" + TEACHER_MEMBERSHIP + "\",\"batchId\":\"" + BATCH + "\"}")
                        .with(jwt().jwt(token -> token.subject(ADMIN_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ENROLLMENT_CONFLICT"));
    }

    @Test
    void learnerCannotUseTenantWideBatchDirectory() throws Exception {
        mvc.perform(get("/api/v1/batches")
                        .with(jwt().jwt(token -> token.subject(LEARNER_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isForbidden());
    }

    @Test
    void teacherDashboardReturnsOnlyCurrentTeachersAssignments() throws Exception {
        mvc.perform(post("/api/v1/teacher-assignments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"membershipId\":\"" + TEACHER_MEMBERSHIP + "\",\"batchId\":\"" + BATCH + "\",\"assignmentRole\":\"LEAD_TEACHER\"}")
                        .with(jwt().jwt(token -> token.subject(ADMIN_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Teacher"));

        mvc.perform(get("/api/v1/teacher-assignments/me")
                        .with(jwt().jwt(token -> token.subject(TEACHER_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].assignmentRole").value("LEAD_TEACHER"));

        mvc.perform(get("/api/v1/teacher-assignments/me")
                        .with(jwt().jwt(token -> token.subject(LEARNER_SUBJECT).claim("tenant_id", TENANT.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    private void seedUser(UUID userId, UUID membershipId, String subject, String name) {
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, ?, ?, 'ACTIVE')", userId, subject, name);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", membershipId, TENANT, userId);
    }
}
