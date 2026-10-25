package com.universalplatform.agent;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class OpenAiCompatibleSwarmProviderTest {
    @Test void providerPostsOpenAiCompatibleRequestAndExtractsContent() throws Exception {
        var server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat/completions", exchange -> {
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(request.contains("\"model\":\"test-model\""));
            assertEquals("Bearer test-key", exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"choices\":[{\"message\":{\"content\":\"hello swarm\"}}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (var out = exchange.getResponseBody()) { out.write(response); }
        });
        server.start();
        try {
            var provider = new OpenAiCompatibleSwarmProvider(new ObjectMapper(), "http://localhost:" + server.getAddress().getPort(), "test-key", "test-model", 5);
            assertEquals("hello swarm", provider.complete("system", "user"));
        } finally { server.stop(0); }
    }

    @Test void providerRejectsMissingConfiguration() {
        var provider = new OpenAiCompatibleSwarmProvider(new ObjectMapper(), "", "", "", 1);
        assertThrows(IllegalStateException.class, () -> provider.complete("system", "user"));
    }
}
