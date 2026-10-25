package com.universalplatform.agent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
class SwarmService {
    private static final List<SwarmAgent> DEFAULT_AGENTS = List.of(
            new SwarmAgent("planner", "planner", "Break the task into concrete, verifiable subproblems. Identify assumptions and acceptance criteria."),
            new SwarmAgent("researcher", "researcher", "Explore the task independently. Produce factual findings, alternatives, and explicit uncertainty."),
            new SwarmAgent("critic", "critic", "Challenge likely solutions. Find omissions, contradictions, risks, and failure modes."),
            new SwarmAgent("synthesizer", "synthesizer", "Synthesize the supplied specialist findings into one actionable answer. Do not invent evidence."));

    private final SwarmProvider provider;
    private final Executor executor;
    private final int maxAgents;

    SwarmService(SwarmProvider provider, @Value("${uep.agent.max-agents:4}") int maxAgents) {
        this.provider = provider;
        this.maxAgents = Math.max(1, Math.min(8, maxAgents));
        this.executor = Executors.newVirtualThreadPerTaskExecutor();
    }

    SwarmResult run(String task, Integer requestedAgents) {
        if (task == null || task.isBlank()) throw new IllegalArgumentException("task is required");
        String normalized = task.trim();
        if (normalized.length() > 12_000) throw new IllegalArgumentException("task exceeds 12000 characters");
        int count = requestedAgents == null ? maxAgents : requestedAgents;
        if (count < 1 || count > maxAgents) throw new IllegalArgumentException("agents must be between 1 and " + maxAgents);
        List<SwarmAgent> agents = new ArrayList<>(DEFAULT_AGENTS.subList(0, Math.min(count, DEFAULT_AGENTS.size())));
        while (agents.size() < count) {
            int n = agents.size() + 1;
            agents.add(new SwarmAgent("specialist-" + n, "specialist", "Analyze the task from a distinct technical perspective and provide testable recommendations."));
        }
        List<CompletableFuture<AgentResult>> futures = agents.stream().map(agent -> CompletableFuture.supplyAsync(() ->
                new AgentResult(agent.id(), agent.role(), provider.complete(agent.systemPrompt(), normalized)), executor)).toList();
        List<AgentResult> findings = futures.stream().map(CompletableFuture::join).toList();
        String synthesisInput = findings.stream().map(x -> "[" + x.agentId() + "/" + x.role() + "]\n" + x.output()).reduce((a, b) -> a + "\n\n" + b).orElse("");
        String finalAnswer = provider.complete(DEFAULT_AGENTS.get(3).systemPrompt(),
                "Original task:\n" + normalized + "\n\nSpecialist findings:\n" + synthesisInput);
        return new SwarmResult(normalized, findings, finalAnswer);
    }

    record AgentResult(String agentId, String role, String output) {
        AgentResult {
            Objects.requireNonNull(agentId); Objects.requireNonNull(role); Objects.requireNonNull(output);
        }
    }
    record SwarmResult(String task, List<AgentResult> findings, String finalAnswer) {}
}
