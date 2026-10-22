package com.nexus.core.ai;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A number in an answer must come from the tools; this is what caught a local model answering 42 when the tool said 20. */
class AnswerNumbersTest {

    private static final String STUCK_ORDERS_OUTPUT = "{\"olderThanHours\":24,\"totalStuck\":20,\"orders\":["
            + "{\"orderId\":\"10015\",\"orderDate\":\"2026-10-08T07:20:37\"},{\"orderId\":\"10017\",\"orderDate\":\"2026-10-08T07:22:37\"}]}";

    private static List<AgentRunner.ToolCall> stuckOrdersCall() {
        return List.of(new AgentRunner.ToolCall("stuck_orders", JsonNodeFactory.instance.objectNode(), STUCK_ORDERS_OUTPUT, false));
    }

    @Test
    void anInventedCountIsCaught() {
        assertEquals(Set.of("42"), AnswerNumbers.unsupported("42", "How many orders are stuck right now?", stuckOrdersCall()));
    }

    @Test
    void numbersFromTheToolsTheQuestionAndListLengthsAreAccepted() {
        String answer = "20 orders are stuck for more than 24 hours. The list shows 2, oldest first:\n"
                + "1. Order 10015, placed 2026-10-08\n2. Order 10017";
        assertTrue(AnswerNumbers.unsupported(answer, "Which orders are stuck?", stuckOrdersCall()).isEmpty());
    }

    @Test
    void failedToolsSupportNothing() {
        List<AgentRunner.ToolCall> failed = List.of(new AgentRunner.ToolCall("stuck_orders", JsonNodeFactory.instance.objectNode(), "{\"total\":20}", true));
        assertEquals(Set.of("20"), AnswerNumbers.unsupported("20", "How many?", failed));
    }
}
