package com.chatflow.ai.chat;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Chat LLM settings, bound from {@code app.ai.chat.*}. Provider-selectable so a future
 * OpenAI/Gemini/local implementation can drop in without touching callers.
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.ai.chat")
public class ChatProperties {

    /** Which {@link ChatCompletionService} implementation to use (currently "anthropic"). */
    private String provider = "anthropic";

    /** Model id sent to the provider. */
    private String model = "claude-opus-4-8";

    /** Hard output ceiling per response. */
    private long maxTokens = 4096;

    /** API key; blank falls back to the provider SDK's environment lookup (e.g. ANTHROPIC_API_KEY). */
    private String apiKey;
}
