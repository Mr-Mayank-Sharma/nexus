package com.nexus.core.ai;

import com.nexus.core.CoreTransactional;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.core.JsonProcessingException;
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
import java.util.Optional;
import java.util.Set;

/**
 * Runs an agent (doc 25, section 2).
 * <ol>
 *   <li>The question goes to the model with the agent's tools.</li>
 *   <li>Each tool the model calls runs against the live OMS, and its result goes back, masked.</li>
 *   <li>This repeats until the model answers or the step limit is reached.</li>
 * </ol>
 * An answer with no successful tool result behind it is marked "Not verified". Every run is an {@code ai_agent_run}
 * row with the masked question, the tools called, the answer, tokens and cost; a daily token budget per agent
 * (system property {@code nexus.core / ai.dailyTokens}, default 2,000,000) stops runaway spend.
 */
@Component("coreAgentRunner")
public class AgentRunner {

    static final ObjectMapper JSON = new ObjectMapper();

    static final long DEFAULT_DAILY_TOKENS = 2_000_000;
    // Default prices in dollars per million tokens, when no nexus.ai.price.MODEL property is set.
    static final double DEFAULT_INPUT_PRICE = 3.0;
    static final double DEFAULT_OUTPUT_PRICE = 15.0;
    // Cache reads cost a tenth of input; cache writes a quarter more than input.
    static final double CACHE_READ_FACTOR = 0.1;
    static final double CACHE_WRITE_FACTOR = 1.25;
    private static final int MAX_ERROR_LENGTH = 4000;

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

    /** What a finished run writes to {@code ai_agent_run}. */
    private record RunRecord(Agent agent, String userId, String maskedQuestion, List<ToolCall> toolCalls, String maskedAnswer, boolean grounded,
                             String error, LlmProvider.Usage usage, long costMicros, LocalDateTime startedAt) {
    }

    private final EntityManager entityManager;
    private final SequenceService sequenceService;
    private final SystemPropertyService systemPropertyService;
    private final Environment environment;
    private final TransactionTemplate newTransaction;

    public AgentRunner(EntityManager entityManager, SequenceService sequenceService, SystemPropertyService systemPropertyService, Environment environment,
                       @Qualifier(CoreTransactional.MANAGER) PlatformTransactionManager transactionManager) {
        this.entityManager = entityManager;
        this.sequenceService = sequenceService;
        this.systemPropertyService = systemPropertyService;
        this.environment = environment;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /** Runs the agent on the question and returns its answer (names unmasked). The run is recorded even when it fails. */
    public Result run(Agent agent, String question, String userId, LlmProvider provider) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Ask a question");
        }
        if (!provider.available()) {
            throw new IllegalStateException("AI is not set up: no model provider is available (see nexus.ai.provider)");
        }
        long dailyBudget = systemPropertyService.getLong("nexus.core", "ai.dailyTokens", DEFAULT_DAILY_TOKENS);
        if (tokensToday(agent.name()) >= dailyBudget) {
            throw new IllegalStateException("The " + agent.name() + " agent has used its daily budget of " + dailyBudget + " tokens");
        }

        LocalDateTime startedAt = CoreClock.now();
        Masker masker = new Masker();
        String maskedQuestion = masker.mask(question);
        ArrayNode conversation = JSON.createArrayNode();
        conversation.addObject().put("role", "user").put("content", maskedQuestion);
        List<LlmProvider.Tool> toolSpecs = agent.tools().stream().map(AgentTool::spec).toList();
        List<ToolCall> toolCalls = new ArrayList<>();
        LlmProvider.Usage usage = LlmProvider.Usage.NONE;
        StringBuilder answer = new StringBuilder();
        String error = null;

        try {
            for (int step = 0; step < agent.maxSteps(); step++) {
                LlmProvider.Request request = new LlmProvider.Request(agent.model(), agent.systemPrompt(), toolSpecs, conversation, agent.maxTokens());
                LlmProvider.Response response = provider.complete(request);
                usage = usage.plus(response.usage());
                conversation.addObject().put("role", "assistant").set("content", response.content());

                // The model's text so far is the answer; its tool calls are answered from live data.
                answer.setLength(0);
                ArrayNode toolResults = JSON.createArrayNode();
                for (JsonNode block : response.content()) {
                    String blockType = block.path("type").asText();
                    if ("text".equals(blockType)) {
                        answer.append(block.path("text").asText());
                    } else if ("tool_use".equals(blockType)) {
                        ToolCall call = callTool(agent, block, masker);
                        toolCalls.add(call);
                        ObjectNode toolResult = toolResults.addObject()
                                .put("type", "tool_result")
                                .put("tool_use_id", block.path("id").asText())
                                .put("content", call.output());
                        if (call.error()) {
                            toolResult.put("is_error", true);
                        }
                    }
                }
                boolean modelIsDone = toolResults.isEmpty() || !"tool_use".equals(response.stopReason());
                if (modelIsDone) {
                    break;
                }
                conversation.addObject().put("role", "user").set("content", toolResults);
                if (step == agent.maxSteps() - 1) {
                    answer.append("I stopped after ").append(agent.maxSteps()).append(" steps without a full answer.");
                }
            }
        } catch (RuntimeException failure) {
            error = failure.getMessage();
        }

        // Grounded: a tool backed the answer, and every number in it comes from what the tools returned.
        String maskedAnswer = answer.toString().trim();
        boolean toolBacked = toolCalls.stream().anyMatch(call -> !call.error());
        Set<String> inventedNumbers = AnswerNumbers.unsupported(maskedAnswer, maskedQuestion, toolCalls);
        boolean grounded = toolBacked && inventedNumbers.isEmpty();
        if (error == null && !toolBacked) {
            maskedAnswer = "Not verified: " + maskedAnswer;
        } else if (error == null && !inventedNumbers.isEmpty()) {
            maskedAnswer = "Not verified (" + String.join(", ", inventedNumbers) + " is not in the data the tools returned): " + maskedAnswer;
        }
        long costMicros = costMicros(agent.model(), usage);
        String runId = recordRun(new RunRecord(agent, userId, maskedQuestion, toolCalls, error == null ? maskedAnswer : null, grounded, error, usage,
                costMicros, startedAt));
        if (error != null) {
            throw new IllegalStateException("The " + agent.name() + " agent failed: " + error);
        }
        return new Result(runId, masker.unmask(maskedAnswer), grounded, toolCalls, usage, costMicros);
    }

    /** Runs one tool the model asked for; a missing tool or a failure becomes an error result the model can read. */
    private static ToolCall callTool(Agent agent, JsonNode toolUseBlock, Masker masker) {
        String toolName = toolUseBlock.path("name").asText();
        JsonNode input = toolUseBlock.path("input");
        Optional<AgentTool> tool = agent.tools().stream().filter(candidate -> candidate.spec().name().equals(toolName)).findFirst();
        try {
            if (tool.isEmpty()) {
                throw new IllegalArgumentException("There is no tool called " + toolName);
            }
            JsonNode output = tool.get().handler().call(input, masker);
            return new ToolCall(toolName, input, masker.mask(JSON.writeValueAsString(output)), false);
        } catch (Exception failure) {
            return new ToolCall(toolName, input, "Error: " + failure.getMessage(), true);
        }
    }

    /** One recorded run, as the audit screen shows it (question and answer in their masked form). */
    public record RunSummary(String aiRunId, String agentName, String userId, String modelId, String question, String toolCalls, String answer,
                             Boolean grounded, String statusId, String errorText, Long inputTokens, Long outputTokens, Long cacheReadTokens,
                             Long costMicros, LocalDateTime startedAt, LocalDateTime endedAt) {
    }

    /** The latest runs of every agent, newest first. */
    public List<RunSummary> recentRuns(int limit) {
        return newTransaction.execute(status -> entityManager.createQuery("SELECT r FROM AiAgentRun r ORDER BY r.startedAt DESC", AiAgentRun.class)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(run -> new RunSummary(run.getAiRunId(), run.getAgentName(), run.getUserId(), run.getModelId(), run.getQuestion(), run.getToolCalls(),
                        run.getAnswer(), run.getGrounded(), run.getStatusId(), run.getErrorText(), run.getInputTokens(), run.getOutputTokens(),
                        run.getCacheReadTokens(), run.getCostMicros(), run.getStartedAt(), run.getEndedAt()))
                .toList());
    }

    /** Tokens the agent used since midnight (UTC), input and output. */
    public long tokensToday(String agentName) {
        LocalDateTime midnight = CoreClock.now().toLocalDate().atStartOfDay();
        Long tokens = newTransaction.execute(status -> entityManager.createQuery("SELECT SUM(COALESCE(r.inputTokens, 0) + COALESCE(r.outputTokens, 0)) "
                        + "FROM AiAgentRun r WHERE r.agentName = :agentName AND r.startedAt >= :midnight", Long.class)
                .setParameter("agentName", agentName)
                .setParameter("midnight", midnight)
                .getSingleResult());
        return tokens == null ? 0 : tokens;
    }

    /** Cost in millionths of a dollar from per-model prices per million tokens (properties nexus.ai.price.MODEL.input/output). */
    long costMicros(String model, LlmProvider.Usage usage) {
        double inputPrice = environment.getProperty("nexus.ai.price." + model + ".input", Double.class, DEFAULT_INPUT_PRICE);
        double outputPrice = environment.getProperty("nexus.ai.price." + model + ".output", Double.class, DEFAULT_OUTPUT_PRICE);
        double dollars = (usage.inputTokens() * inputPrice
                + usage.cacheReadTokens() * inputPrice * CACHE_READ_FACTOR
                + usage.cacheWriteTokens() * inputPrice * CACHE_WRITE_FACTOR
                + usage.outputTokens() * outputPrice) / 1_000_000.0;
        return Math.round(dollars * 1_000_000);
    }

    private String recordRun(RunRecord run) {
        return newTransaction.execute(status -> {
            AiAgentRun row = new AiAgentRun();
            row.setAiRunId(sequenceService.nextId("AiAgentRun"));
            row.setAgentName(run.agent().name());
            row.setUserId(run.userId());
            row.setModelId(run.agent().model());
            row.setQuestion(run.maskedQuestion());
            row.setToolCalls(toJsonOrEmpty(run.toolCalls()));
            row.setAnswer(run.maskedAnswer());
            row.setGrounded(run.grounded());
            row.setStatusId(run.error() == null ? "DONE" : "FAILED");
            String error = run.error();
            row.setErrorText(error == null || error.length() <= MAX_ERROR_LENGTH ? error : error.substring(0, MAX_ERROR_LENGTH));
            row.setInputTokens(run.usage().inputTokens());
            row.setOutputTokens(run.usage().outputTokens());
            row.setCacheReadTokens(run.usage().cacheReadTokens());
            row.setCacheWriteTokens(run.usage().cacheWriteTokens());
            row.setCostMicros(run.costMicros());
            row.setStartedAt(run.startedAt());
            row.setEndedAt(CoreClock.now());
            entityManager.persist(row);
            return row.getAiRunId();
        });
    }

    private static String toJsonOrEmpty(List<ToolCall> toolCalls) {
        try {
            return JSON.writeValueAsString(toolCalls);
        } catch (JsonProcessingException notSerializable) {
            return "[]";
        }
    }
}
