package com.chatflow.ai.chat;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link ChatCompletionService} backed by the Anthropic Java SDK.
 *
 * <p>Prompt caching: the frozen system instruction and the large reusable context
 * (e.g. a conversation transcript) render as the cached prefix — a {@code cache_control}
 * breakpoint sits on the context block, so follow-up questions over the same transcript
 * reuse it at ~0.1x cost. The volatile question goes in the (uncached) user turn.
 *
 * <p>Adaptive thinking is enabled; its reasoning is omitted from the response content by
 * default, so we simply concatenate the returned text blocks.
 */
@Service
@ConditionalOnProperty(name = "app.ai.chat.provider", havingValue = "anthropic", matchIfMissing = true)
public class AnthropicChatCompletionService implements ChatCompletionService {

    private final ChatProperties props;
    /** Built lazily so the app boots without an API key — only a real AI call requires one. */
    private volatile AnthropicClient client;

    public AnthropicChatCompletionService(ChatProperties props) {
        this.props = props;
    }

    private AnthropicClient client() {
        AnthropicClient c = client;
        if (c == null) {
            synchronized (this) {
                c = client;
                if (c == null) {
                    c = (props.getApiKey() == null || props.getApiKey().isBlank())
                            ? AnthropicOkHttpClient.fromEnv()                     // reads ANTHROPIC_API_KEY
                            : AnthropicOkHttpClient.builder().apiKey(props.getApiKey()).build();
                    client = c;
                }
            }
        }
        return c;
    }

    @Override
    public String complete(String systemInstruction, String cacheableContext, String userQuestion) {
        MessageCreateParams params = MessageCreateParams.builder()
                .model(props.getModel())
                .maxTokens(props.getMaxTokens())
                .thinking(ThinkingConfigAdaptive.builder().build())
                // System = frozen instruction + large reusable context; cache breakpoint on the
                // context block caches both, so repeated questions over it are cheap.
                .systemOfTextBlockParams(List.of(
                        TextBlockParam.builder().text(systemInstruction).build(),
                        TextBlockParam.builder()
                                .text(cacheableContext)
                                .cacheControl(CacheControlEphemeral.builder().build())
                                .build()))
                .addUserMessage(userQuestion)
                .build();

        Message response = client().messages().create(params);

        StringBuilder answer = new StringBuilder();
        for (ContentBlock block : response.content()) {
            block.text().ifPresent(text -> answer.append(text.text()));
        }
        return answer.toString();
    }
}
