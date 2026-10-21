package com.nexus.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nexus.core.model.ai.AiAgentRun;
import com.nexus.core.service.CoreClock;
import com.nexus.core.service.SequenceService;
import com.nexus.core.service.SystemPropertyService;
import jakarta.persistence.EntityManager;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs an agent (doc 25, section 2): the question goes to the model with the agent's tools; each tool the model calls
 * runs against the live OMS and its result goes back, masked; this repeats until the model answers or the step limit
 * is reached. An answer with no tool result behind it is marked not verified. Every run is an {@code ai_agent_run}
 * row with the masked question, the tools called, the answer, tokens and cost; a daily token budget per agent
 * (system property {@code nexus.core / ai.dailyTokens}, default 2,000,000) stops runaway spend.
 */
@Component("coreAgentRunner")
public class AgentRunner {

    static final ObjectMapper JSON = new ObjectMapper();

    /** A tool the agent offers: its definition for the model and the code that answers it. */
    public record AgentTool(LlmProvider.Tool spec, Handler handler) {
    }

    @FunctionalInterface
    public interface Handler {
        /** Answers a tool call from live data; mark personal names with {@code masker.name(...)}. */
        JsonNode call(JsonNode input, Masker masker);
    }

    public record Agent(String name, String model, String systemPrompt, List<AgentTool> tools, int maxSteps, int maxTokens) {
    }

    public record ToolCall(String name, JsonNode input, String output, boolean error) {
    }

    public record Result(String runId, String answer, boolean grounded, List<ToolCall> toolCalls, LlmProvider.Usage usage, long costMicros) {
    }

    private final EntityManager em;
    private final SequenceService sequences;
    private final SystemPropertyService properties;
    private final Environment env;
    private final TransactionTemplate tx;

    public AgentRunner(EntityManager em, SequenceService sequences, SystemPropertyService properties, Environment env, PlatformTransactionManager tm) {
        this.em = em;
        this.sequences = sequences;
        this.properties = properties;
        this.env = env;
        this.tx = new TransactionTemplate(tm);
        this.tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Result run(Agent agent, String question, String userId, LlmProvider provider) {
        if (question == null || question.isBlank()) throw new IllegalArgumentException("Ask a question");
        if (!provider.available()) throw new IllegalStateException("AI is not set up: ANTHROPIC_API_KEY is missing");
        long budget = properties.getLong("nexus.core", "ai.dailyTokens", 2_000_000);
        long used = tokensToday(agent.name());
        if (used >= budget) throw new IllegalStateException("The " + agent.name() + " agent has used its daily budget of " + budget + " tokens");

        LocalDateTime started = CoreClock.now();
        Masker masker = new Masker();
        ArrayNode messages = JSON.createArrayNode();
        messages.addObject().put("role", "user").put("content", masker.mask(question));
        List<LlmProvider.Tool> specs = agent.tools().stream().map(AgentTool::spec).toList();
        List<ToolCall> calls = new ArrayList<>();
        LlmProvider.Usage usage = LlmProvider.Usage.NONE;
        StringBuilder answer = new StringBuilder();
        String error = null;
        try {
            for (int step = 0; step < agent.maxSteps(); step++) {
                LlmProvider.Response r = provider.complete(new LlmProvider.Request(agent.model(), agent.systemPrompt(), specs, messages, agent.maxTokens()));
                usage = usage.plus(r.usage());
                messages.addObject().put("role", "assistant").set("content", r.content());
                ArrayNode results = JSON.createArrayNode();
                answer.setLength(0);
                for (JsonNode block : r.content()) {
                    if ("text".equals(block.path("type").asText())) answer.append(block.path("text").asText());
                    if (!"tool_use".equals(block.path("type").asText())) continue;
                    String name = block.path("name").asText();
                    AgentTool tool = agent.tools().stream().filter(t -> t.spec().name().equals(name)).findFirst().orElse(null);
                    String out;
                    boolean failed = false;
                    try {
                        if (tool == null) throw new IllegalArgumentException("There is no tool called " + name);
                        out = masker.mask(JSON.writeValueAsString(tool.handler().call(block.path("input"), masker)));
                    } catch (Exception e) {
                        out = "Error: " + e.getMessage();
                        failed = true;
                    }
                    calls.add(new ToolCall(name, block.path("input"), out, failed));
                    ObjectNode res = results.addObject().put("type", "tool_result").put("tool_use_id", block.path("id").asText()).put("content", out);
                    if (failed) res.put("is_error", true);
                }
                if (results.isEmpty() || !"tool_use".equals(r.stopReason())) break;
                messages.addObject().put("role", "user").set("content", results);
                if (step == agent.maxSteps() - 1) answer.append("I stopped after ").append(agent.maxSteps()).append(" steps without a full answer.");
            }
        } catch (RuntimeException e) {
            error = e.getMessage();
        }
        boolean grounded = calls.stream().anyMatch(c -> !c.error());
        String masked = answer.toString().trim();
        if (!grounded && error == null) masked = "Not verified: " + masked;
        long cost = cost(agent.model(), usage);
        String runId = record(agent, userId, masker.mask(question), calls, error == null ? masked : null, grounded, error, usage, cost, started);
        if (error != null) throw new IllegalStateException("The " + agent.name() + " agent failed: " + error);
        return new Result(runId, masker.unmask(masked), grounded, calls, usage, cost);
    }

    public long tokensToday(String agentName) {
        LocalDateTime midnight = CoreClock.now().toLocalDate().atStartOfDay();
        Long v = tx.execute(s -> em.createQuery("SELECT SUM(COALESCE(r.inputTokens, 0) + COALESCE(r.outputTokens, 0)) FROM AiAgentRun r "
                + "WHERE r.agentName = :a AND r.startedAt >= :d", Long.class).setParameter("a", agentName).setParameter("d", midnight).getSingleResult());
        return v == null ? 0 : v;
    }

    /** Cost in millionths of a dollar from per-model prices per million tokens (properties nexus.ai.price.MODEL.input/output). */
    long cost(String model, LlmProvider.Usage u) {
        double in = env.getProperty("nexus.ai.price." + model + ".input", Double.class, 3.0);
        double out = env.getProperty("nexus.ai.price." + model + ".output", Double.class, 15.0);
        // cache reads cost a tenth of input, cache writes a quarter more
        double dollars = (u.inputTokens() * in + u.cacheReadTokens() * in * 0.1 + u.cacheWriteTokens() * in * 1.25 + u.outputTokens() * out) / 1_000_000.0;
        return Math.round(dollars * 1_000_000);
    }

    private String record(Agent agent, String userId, String question, List<ToolCall> calls, String answer, boolean grounded, String error,
                          LlmProvider.Usage u, long cost, LocalDateTime started) {
        return tx.execute(s -> {
            AiAgentRun r = new AiAgentRun();
            r.setAiRunId(sequences.nextId("AiAgentRun"));
            r.setAgentName(agent.name());
            r.setUserId(userId);
            r.setModelId(agent.model());
            r.setQuestion(question);
            try {
                r.setToolCalls(JSON.writeValueAsString(calls));
            } catch (Exception e) {
                r.setToolCalls("[]");
            }
            r.setAnswer(answer);
            r.setGrounded(grounded);
            r.setStatusId(error == null ? "DONE" : "FAILED");
            r.setErrorText(error == null ? null : error.length() > 4000 ? error.substring(0, 4000) : error);
            r.setInputTokens(u.inputTokens());
            r.setOutputTokens(u.outputTokens());
            r.setCacheReadTokens(u.cacheReadTokens());
            r.setCacheWriteTokens(u.cacheWriteTokens());
            r.setCostMicros(cost);
            r.setStartedAt(started);
            r.setEndedAt(CoreClock.now());
            em.persist(r);
            return r.getAiRunId();
        });
    }
}
