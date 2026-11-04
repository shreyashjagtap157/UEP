package com.universalplatform.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * PostgreSQL integration qualification for the atomic, per-membership messaging
 * quotas. The requests use independent servlet/test threads and transactions;
 * this class intentionally does not use a test-managed transaction.
 */
@SpringBootTest
@AutoConfigureMockMvc
class MessagingRateLimitConcurrencyIntegrationTest {
    private static final int CONCURRENT_REQUESTS = 8;

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void concurrentMessageSendsNeverExceedThePerMinuteQuota() throws Exception {
        Fixture fixture = createFixture(1, 5);
        UUID conversationId = createConversation(fixture);

        List<Integer> statuses = runTogether(CONCURRENT_REQUESTS, index ->
                sendMessage(fixture, conversationId, "Concurrent send " + index));

        assertOnlySuccessOrRateLimited(statuses);
        List<Integer> persistedWindows = jdbc.queryForList("""
                select message_count
                from message_rate_window
                where tenant_id = ? and membership_id = ? and window_type = 'MINUTE'
                order by window_start
                """, Integer.class, fixture.tenantId(), fixture.senderMembershipId());

        // Requests can straddle a wall-clock minute boundary. Each database
        // bucket must independently admit exactly one request, regardless of
        // whether the burst occupies one bucket or two.
        assertThat(persistedWindows).isNotEmpty().allSatisfy(count -> assertThat(count).isEqualTo(1));
        long accepted = statuses.stream().filter(status -> status == 200).count();
        assertThat(accepted).isEqualTo(persistedWindows.size());

        Integer persistedMessages = jdbc.queryForObject("""
                select count(*) from message_record
                where tenant_id = ? and conversation_id = ?
                """, Integer.class, fixture.tenantId(), conversationId);
        Integer persistedDeliveries = jdbc.queryForObject("""
                select count(*) from message_delivery
                where tenant_id = ? and conversation_id = ? and recipient_membership_id = ?
                """, Integer.class, fixture.tenantId(), conversationId, fixture.peerMembershipId());

        assertThat(persistedMessages).isEqualTo(Math.toIntExact(accepted));
        assertThat(persistedDeliveries).isEqualTo(Math.toIntExact(accepted));
    }

    @Test
    void concurrentReportsNeverExceedThePerHourQuotaForDistinctMessages() throws Exception {
        Fixture fixture = createFixture(30, 1);
        UUID conversationId = createConversation(fixture);

        // Use the peer as the message author so the reporting membership is
        // allowed to report every distinct message in the burst.
        List<UUID> messageIds = new ArrayList<>();
        for (int index = 0; index < CONCURRENT_REQUESTS; index++) {
            messageIds.add(sendAndReadMessageId(fixture, conversationId, "Report target " + index));
        }

        List<Integer> statuses = runTogether(CONCURRENT_REQUESTS, index ->
                reportMessage(fixture, messageIds.get(index), "Concurrent report " + index));

        assertOnlySuccessOrRateLimited(statuses);
        List<Integer> persistedWindows = jdbc.queryForList("""
                select report_count
                from message_rate_window
                where tenant_id = ? and membership_id = ? and window_type = 'HOUR'
                order by window_start
                """, Integer.class, fixture.tenantId(), fixture.senderMembershipId());

        // As with minute buckets, this remains deterministic at an hour
        // boundary while still proving the quota is atomic within each bucket.
        assertThat(persistedWindows).isNotEmpty().allSatisfy(count -> assertThat(count).isEqualTo(1));
        long accepted = statuses.stream().filter(status -> status == 200).count();
        assertThat(accepted).isEqualTo(persistedWindows.size());

        Integer persistedReports = jdbc.queryForObject("""
                select count(*) from message_report
                where tenant_id = ? and reporter_membership_id = ?
                """, Integer.class, fixture.tenantId(), fixture.senderMembershipId());
        assertThat(persistedReports).isEqualTo(Math.toIntExact(accepted));
    }

    @Test
    void messageQuotasAreIsolatedForOneIdentityAcrossTenantMemberships() throws Exception {
        UUID tenantA = UUID.randomUUID();
        UUID tenantB = UUID.randomUUID();
        UUID senderUserId = UUID.randomUUID();
        UUID peerAUserId = UUID.randomUUID();
        UUID peerBUserId = UUID.randomUUID();
        UUID senderMembershipA = UUID.randomUUID();
        UUID senderMembershipB = UUID.randomUUID();
        UUID peerMembershipA = UUID.randomUUID();
        UUID peerMembershipB = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String senderSubject = "multi-tenant-rate-owner-" + suffix;
        String peerASubject = "multi-tenant-rate-peer-a-" + suffix;
        String peerBSubject = "multi-tenant-rate-peer-b-" + suffix;

        jdbc.update("""
                insert into tenant(id, slug, display_name, status)
                values (?, ?, 'Messaging Quota Tenant A', 'ACTIVE')
                """, tenantA, "message-rate-a-" + suffix);
        jdbc.update("""
                insert into tenant(id, slug, display_name, status)
                values (?, ?, 'Messaging Quota Tenant B', 'ACTIVE')
                """, tenantB, "message-rate-b-" + suffix);
        jdbc.update("""
                insert into user_account(id, oidc_subject, email, display_name, status)
                values (?, ?, ?, 'Multi-tenant Sender', 'ACTIVE')
                """, senderUserId, senderSubject, "multi-tenant-sender-" + suffix + "@example.test");
        jdbc.update("""
                insert into user_account(id, oidc_subject, email, display_name, status)
                values (?, ?, ?, 'Tenant A Peer', 'ACTIVE')
                """, peerAUserId, peerASubject, "tenant-a-peer-" + suffix + "@example.test");
        jdbc.update("""
                insert into user_account(id, oidc_subject, email, display_name, status)
                values (?, ?, ?, 'Tenant B Peer', 'ACTIVE')
                """, peerBUserId, peerBSubject, "tenant-b-peer-" + suffix + "@example.test");

        jdbc.update("""
                insert into tenant_membership(id, tenant_id, user_id, status)
                values (?, ?, ?, 'ACTIVE')
                """, senderMembershipA, tenantA, senderUserId);
        jdbc.update("""
                insert into tenant_membership(id, tenant_id, user_id, status)
                values (?, ?, ?, 'ACTIVE')
                """, senderMembershipB, tenantB, senderUserId);
        jdbc.update("""
                insert into tenant_membership(id, tenant_id, user_id, status)
                values (?, ?, ?, 'ACTIVE')
                """, peerMembershipA, tenantA, peerAUserId);
        jdbc.update("""
                insert into tenant_membership(id, tenant_id, user_id, status)
                values (?, ?, ?, 'ACTIVE')
                """, peerMembershipB, tenantB, peerBUserId);
        // V041 creates both default policy rows as each tenant is inserted.
        // Set the restrictive test policy on those rows without duplicating their primary keys.
        assertThat(jdbc.update("""
                update message_tenant_policy
                set max_messages_per_minute = 1, max_reports_per_hour = 1
                where tenant_id in (?, ?)
                """, tenantA, tenantB)).isEqualTo(2);

        Fixture fixtureA = new Fixture(tenantA, senderMembershipA, peerMembershipA, senderSubject, peerASubject);
        Fixture fixtureB = new Fixture(tenantB, senderMembershipB, peerMembershipB, senderSubject, peerBSubject);
        UUID conversationA = createConversation(fixtureA);
        UUID conversationB = createConversation(fixtureB);

        int tenantAStatus = sendMessage(fixtureA, conversationA, "Tenant A quota");
        int tenantBStatus = sendMessage(fixtureB, conversationB, "Tenant B quota");

        assertThat(tenantAStatus).isEqualTo(200);
        assertThat(tenantBStatus).isEqualTo(200);
        assertThat(jdbc.queryForObject("""
                select message_count from message_rate_window
                where tenant_id = ? and membership_id = ? and window_type = 'MINUTE'
                """, Integer.class, tenantA, senderMembershipA)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select message_count from message_rate_window
                where tenant_id = ? and membership_id = ? and window_type = 'MINUTE'
                """, Integer.class, tenantB, senderMembershipB)).isEqualTo(1);
    }

    private Fixture createFixture(int maxMessagesPerMinute, int maxReportsPerHour) {
        UUID tenantId = UUID.randomUUID();
        UUID senderUserId = UUID.randomUUID();
        UUID peerUserId = UUID.randomUUID();
        UUID senderMembershipId = UUID.randomUUID();
        UUID peerMembershipId = UUID.randomUUID();
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String senderSubject = "message-rate-owner-" + suffix;
        String peerSubject = "message-rate-peer-" + suffix;

        jdbc.update("""
                insert into tenant(id, slug, display_name, status)
                values (?, ?, 'Messaging Rate Qualification', 'ACTIVE')
                """, tenantId, "message-rate-" + suffix);
        jdbc.update("""
                insert into user_account(id, oidc_subject, email, display_name, status)
                values (?, ?, ?, 'Rate Limit Sender', 'ACTIVE')
                """, senderUserId, senderSubject, "sender-" + suffix + "@example.test");
        jdbc.update("""
                insert into user_account(id, oidc_subject, email, display_name, status)
                values (?, ?, ?, 'Rate Limit Peer', 'ACTIVE')
                """, peerUserId, peerSubject, "peer-" + suffix + "@example.test");
        jdbc.update("""
                insert into tenant_membership(id, tenant_id, user_id, status)
                values (?, ?, ?, 'ACTIVE')
                """, senderMembershipId, tenantId, senderUserId);
        jdbc.update("""
                insert into tenant_membership(id, tenant_id, user_id, status)
                values (?, ?, ?, 'ACTIVE')
                """, peerMembershipId, tenantId, peerUserId);
        // V041 creates the default policy row from the tenant INSERT trigger.
        // Update that row rather than inserting a duplicate tenant_id.
        assertThat(jdbc.update("""
                update message_tenant_policy
                set max_messages_per_minute = ?, max_reports_per_hour = ?
                where tenant_id = ?
                """, maxMessagesPerMinute, maxReportsPerHour, tenantId)).isEqualTo(1);

        return new Fixture(tenantId, senderMembershipId, peerMembershipId, senderSubject, peerSubject);
    }

    private UUID createConversation(Fixture fixture) throws Exception {
        String response = mvc.perform(post("/api/v1/messaging/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"subject":"Concurrent messaging quota","participantMembershipIds":["%s"]}
                                """.formatted(fixture.peerMembershipId()))
                        .with(jwt().jwt(token -> token.subject(fixture.senderSubject())
                                .claim("tenant_id", fixture.tenantId().toString()))))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private int sendMessage(Fixture fixture, UUID conversationId, String body) {
        try {
            return mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new MessageBody(body)))
                            .with(jwt().jwt(token -> token.subject(fixture.senderSubject())
                                    .claim("tenant_id", fixture.tenantId().toString()))))
                    .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent message request failed", exception);
        }
    }

    private UUID sendAndReadMessageId(Fixture fixture, UUID conversationId, String body) throws Exception {
        String response = mvc.perform(post("/api/v1/messaging/conversations/" + conversationId + "/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MessageBody(body)))
                        .with(jwt().jwt(token -> token.subject(fixture.peerSubject())
                                .claim("tenant_id", fixture.tenantId().toString()))))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private int reportMessage(Fixture fixture, UUID messageId, String reason) {
        try {
            return mvc.perform(post("/api/v1/messaging/messages/" + messageId + "/reports")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(new ReportBody(reason)))
                            .with(jwt().jwt(token -> token.subject(fixture.senderSubject())
                                    .claim("tenant_id", fixture.tenantId().toString()))))
                    .andReturn().getResponse().getStatus();
        } catch (Exception exception) {
            throw new IllegalStateException("Concurrent report request failed", exception);
        }
    }

    private List<Integer> runTogether(int requestCount, IntFunction<Integer> request) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch ready = new CountDownLatch(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        try {
            for (int index = 0; index < requestCount; index++) {
                int requestIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(10, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting for concurrent request barrier");
                    }
                    return request.apply(requestIndex);
                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<Integer> results = new ArrayList<>();
            for (Future<Integer> future : futures) {
                results.add(future.get(45, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private static void assertOnlySuccessOrRateLimited(List<Integer> statuses) {
        assertThat(statuses).hasSize(CONCURRENT_REQUESTS)
                .allSatisfy(status -> assertThat(status).isIn(200, 429));
        assertThat(statuses).contains(200);
        assertThat(statuses).contains(429);
    }

    private record Fixture(UUID tenantId, UUID senderMembershipId, UUID peerMembershipId,
                           String senderSubject, String peerSubject) {}
    private record MessageBody(String body) {}
    private record ReportBody(String reason) {}
}
