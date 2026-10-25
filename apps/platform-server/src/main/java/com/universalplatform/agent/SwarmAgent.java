package com.universalplatform.agent;

public record SwarmAgent(String id, String role, String systemPrompt) {
    public SwarmAgent {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role is required");
        if (systemPrompt == null || systemPrompt.isBlank()) throw new IllegalArgumentException("systemPrompt is required");
    }
}
