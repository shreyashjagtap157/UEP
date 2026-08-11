package com.universalplatform.academics;

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
class AcademicCoreIntegrationTest {
    private static final UUID TENANT_A = UUID.fromString("01901234-5000-7000-8000-000000000001");
    private static final UUID TENANT_B = UUID.fromString("01901234-5000-7000-8000-000000000002");
    private static final UUID USER = UUID.fromString("01901234-5000-7000-8000-000000000003");
    private static final UUID MEMBERSHIP = UUID.fromString("01901234-5000-7000-8000-000000000004");
    private static final UUID ROLE = UUID.fromString("01901234-5000-7000-8000-000000000005");
    private static final UUID FOREIGN_PERIOD = UUID.fromString("01901234-5000-7000-8000-000000000006");
    private static final UUID FOREIGN_PROGRAM = UUID.fromString("01901234-5000-7000-8000-000000000007");
    private static final String SUBJECT = "academic-owner";

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'academic-a', 'Academic A', 'ACTIVE')", TENANT_A);
        jdbc.update("INSERT INTO tenant(id, slug, display_name, status) VALUES (?, 'academic-b', 'Academic B', 'ACTIVE')", TENANT_B);
        jdbc.update("INSERT INTO user_account(id, oidc_subject, display_name, status) VALUES (?, ?, 'Academic Owner', 'ACTIVE')", USER, SUBJECT);
        jdbc.update("INSERT INTO tenant_membership(id, tenant_id, user_id, status) VALUES (?, ?, ?, 'ACTIVE')", MEMBERSHIP, TENANT_A, USER);
        jdbc.update("INSERT INTO role_definition(id, tenant_id, system_key, name, system_managed) VALUES (?, ?, 'ORGANIZATION_OWNER', 'Organization Owner', true)", ROLE, TENANT_A);
        for (String permission : new String[]{"ACADEMICS_VIEW", "ACADEMICS_MANAGE", "CURRICULUM_VIEW", "CURRICULUM_MANAGE"}) {
            jdbc.update("INSERT INTO role_permission(role_id, permission_key) VALUES (?, ?)", ROLE, permission);
        }
        jdbc.update("INSERT INTO role_assignment(id, tenant_id, membership_id, role_id, scope_kind, assigned_by_subject) VALUES (uuidv7(), ?, ?, ?, 'TENANT', ?)", TENANT_A, MEMBERSHIP, ROLE, SUBJECT);
        jdbc.update("INSERT INTO academic_period(id, tenant_id, code, display_name, starts_on, ends_on, status) VALUES (?, ?, 'FOREIGN-2026', 'Foreign 2026', DATE '2026-01-01', DATE '2026-12-31', 'ACTIVE')", FOREIGN_PERIOD, TENANT_B);
        jdbc.update("INSERT INTO academic_program(id, tenant_id, academic_period_id, code, display_name, status) VALUES (?, ?, ?, 'FOREIGN-PROG', 'Foreign Program', 'ACTIVE')", FOREIGN_PROGRAM, TENANT_B, FOREIGN_PERIOD);
    }

    @Test
    void flexibleHierarchySupportsProgramCourseSubjectAndProgramDirectModule() throws Exception {
        String periodJson = mvc.perform(post("/api/v1/academic-periods")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"AY26\",\"displayName\":\"Academic Year 2026\",\"startsOn\":\"2026-01-01\",\"endsOn\":\"2026-12-31\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("AY26"))
                .andReturn().getResponse().getContentAsString();
        String periodId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(periodJson).get("id").asText();

        String programJson = mvc.perform(post("/api/v1/programs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"academicPeriodId\":\"" + periodId + "\",\"code\":\"BTECH\",\"displayName\":\"B.Tech\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String programId = new com.fasterxml.jackson.databind.ObjectMapper().readTree(programJson).get("id").asText();

        mvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programId\":\"" + programId + "\",\"code\":\"CS101\",\"displayName\":\"Computer Science Foundations\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").value(programId));

        mvc.perform(post("/api/v1/modules")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programId\":\"" + programId + "\",\"code\":\"ORIENT\",\"displayName\":\"Program Orientation\",\"sequenceNumber\":1}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").value(programId));

        mvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"COACH-JAVA\",\"displayName\":\"Standalone Java Course\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programId").doesNotExist());
    }

    @Test
    void foreignTenantParentIsRejectedBeforePersistence() throws Exception {
        mvc.perform(post("/api/v1/courses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"programId\":\"" + FOREIGN_PROGRAM + "\",\"code\":\"BAD\",\"displayName\":\"Cross Tenant Course\"}")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CURRICULUM_REQUEST"));

        mvc.perform(get("/api/v1/courses")
                        .with(jwt().jwt(token -> token.subject(SUBJECT).claim("tenant_id", TENANT_A.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
