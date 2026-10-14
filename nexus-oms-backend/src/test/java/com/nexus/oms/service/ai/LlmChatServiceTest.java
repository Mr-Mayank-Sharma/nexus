package com.nexus.oms.service.ai;

import com.nexus.oms.exception.AiServiceException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** An assistant with no model must say so; it used to answer from a script of invented business facts. */
class LlmChatServiceTest {

    private final LlmChatService unconfigured = new LlmChatService("", "gpt-4o", 1000,
            mock(SemanticCacheService.class), mock(AiCostTrackingService.class), new SimpleMeterRegistry());

    private static List<Map<String, String>> ask(String text) {
        return List.of(Map.of("role", "user", "content", text));
    }

    @Test
    void withoutAModel_chatRefuses_forEveryKindOfQuestion() {
        for (String q : new String[]{"Which SKUs are low on stock?", "Where is my order?", "How are sales this month?",
                "Who are my top customers?", "Any problems or alerts?", "hello"}) {
            AiServiceException e = assertThrows(AiServiceException.class, () -> unconfigured.chat("sys", ask(q)), q);
            assertTrue(e.getMessage().contains("not available"), e.getMessage());
            assertTrue(e.getMessage().contains("nexus.ai.openai.api-key"), "says how to fix it");
        }
    }

    @Test
    void withoutAModel_usageChat_refusesToo() {
        assertThrows(AiServiceException.class, () -> unconfigured.chatWithUsage("sys", ask("inventory?")));
    }

    @Test
    void theCircuitBreakerFallback_doesNotInventAnAnswerEither() {
        assertThrows(AiServiceException.class, () -> unconfigured.chatFallback("sys", ask("sales report"), new RuntimeException("down")));
    }

    @Test
    void reportsItselfAsDisabled() {
        assertFalse(unconfigured.isEnabled());
    }
}
