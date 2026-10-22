package com.nexus.core.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Checks that a model did not invent a number (doc 25: a language model is never the source of a number). Every number
 * in the answer must appear in what the tools returned, in the question, or be the length of a list a tool returned.
 * List markers ("1." or "2)" at the start of a line) are not counts and are skipped.
 */
final class AnswerNumbers {

    /** A run of digits (with decimal or thousands separators); letters around it do not matter, so "08T07" holds 8 and 7. */
    private static final Pattern NUMBER = Pattern.compile("(?<![\\d.,])\\d+(?:[.,]\\d+)*(?!\\d)");
    private static final Pattern LIST_MARKER = Pattern.compile("(?m)^\\s*\\d+[.)]\\s");

    private AnswerNumbers() {
    }

    /** The answer's numbers that nothing the tools returned supports, in order of appearance. */
    static Set<String> unsupported(String answer, String question, List<AgentRunner.ToolCall> toolCalls) {
        // Step 1: every number the model was given.
        Set<BigDecimal> known = new HashSet<>(numbersIn(question));
        for (AgentRunner.ToolCall call : toolCalls) {
            if (call.error() || call.output() == null) {
                continue;
            }
            known.addAll(numbersIn(call.output()));
            addListLengths(call.output(), known);
        }

        // Step 2: the answer's numbers that are not among them.
        Set<String> unsupported = new LinkedHashSet<>();
        Matcher number = NUMBER.matcher(LIST_MARKER.matcher(answer).replaceAll(" "));
        while (number.find()) {
            BigDecimal value = parse(number.group());
            if (value != null && !known.contains(value)) {
                unsupported.add(number.group());
            }
        }
        return unsupported;
    }

    private static Set<BigDecimal> numbersIn(String text) {
        Set<BigDecimal> numbers = new HashSet<>();
        if (text == null) {
            return numbers;
        }
        Matcher number = NUMBER.matcher(text);
        while (number.find()) {
            BigDecimal value = parse(number.group());
            if (value != null) {
                numbers.add(value);
            }
        }
        return numbers;
    }

    /** The size of every list in a tool's JSON output: "how many" is often answered by counting one. */
    private static void addListLengths(String toolOutput, Set<BigDecimal> known) {
        try {
            addListLengths(AgentRunner.JSON.readTree(toolOutput), known);
        } catch (JsonProcessingException notJson) {
            // plain-text output: its numbers were already taken
        }
    }

    private static void addListLengths(JsonNode node, Set<BigDecimal> known) {
        if (node.isArray()) {
            known.add(BigDecimal.valueOf(node.size()).stripTrailingZeros());
        }
        node.forEach(child -> addListLengths(child, known));
    }

    /** "1,250" and "1250.0" are the same number; "08" is 8. */
    private static BigDecimal parse(String text) {
        try {
            return new BigDecimal(text.replace(",", "")).stripTrailingZeros();
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
