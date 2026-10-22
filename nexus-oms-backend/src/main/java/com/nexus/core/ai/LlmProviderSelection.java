package com.nexus.core.ai;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;

/**
 * Which model the AI agents use: {@code nexus.ai.provider} is {@code ollama} (a local model, the default for
 * development) or {@code anthropic} (Claude, needs {@code ANTHROPIC_API_KEY}).
 */
@Configuration
public class LlmProviderSelection {

    public static final String SELECTED = "coreLlmProvider";

    @Bean(SELECTED)
    public LlmProvider selectedProvider(@Value("${nexus.ai.provider:anthropic}") String providerName,
                                        @Qualifier("coreAnthropicProvider") LlmProvider anthropic,
                                        @Qualifier("coreOllamaProvider") LlmProvider ollama) {
        return switch (providerName.trim().toLowerCase(Locale.ROOT)) {
            case "ollama" -> ollama;
            case "anthropic" -> anthropic;
            default -> throw new IllegalArgumentException("nexus.ai.provider must be ollama or anthropic, not " + providerName);
        };
    }
}
