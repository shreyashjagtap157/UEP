package com.universalplatform.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
class OpenAiCompatibleSwarmProvider implements SwarmProvider {
    private final HttpClient client;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final Duration timeout;

    OpenAiCompatibleSwarmProvider(
            ObjectMapper mapper,
            @Value("${uep.agent.base-url:}") String baseUrl,
            @Value("${uep.agent.api-key:}") String apiKey,
            @Value("${uep.agent.model:}") String model,
            @Value("${uep.agent.timeout-seconds:60}") long timeoutSeconds) {
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(Math.max(1, timeoutSeconds))).build();
        this.mapper = mapper;
        this.baseUrl = normalize(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.model = model == null ? "" : model.trim();
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (baseUrl.isBlank() || model.isBlank()) {
            throw new IllegalStateException("Swarm provider is not configured: set UEP_AGENT_BASE_URL and UEP_AGENT_MODEL");
        }
        try {
            var body = mapper.createObjectNode();
            body.put("model", model);
            var messages = body.putArray("messages");
            messages.addObject().put("role", "system").put("content", systemPrompt);
            messages.addObject().put("role", "user").put("content", userPrompt);
            var builder = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (!apiKey.isBlank()) builder.header("Authorization", "Bearer " + apiKey);
            var response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalStateException("Swarm provider returned HTTP " + response.statusCode());
            }
            JsonNode root = mapper.readTree(response.body());
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            if (!content.isTextual() || content.asText().isBlank()) throw new IllegalStateException("Swarm provider returned no message content");
            return content.asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Swarm provider request interrupted", e);
        } catch (Exception e) {
            if (e instanceof IllegalStateException state) throw state;
            throw new IllegalStateException("Swarm provider request failed", e);
        }
    }

    private static String normalize(String value) {
        if (value == null) return "";
        String result = value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        if (result.endsWith("/v1")) return result;
        return result + "/v1";
    }
}
