package com.chatflow.ai.chat;

/**
 * Provider-agnostic chat completion. Implementations talk to a concrete LLM provider
 * (Anthropic today; OpenAI / Gemini / local tomorrow); callers — the summarizer and RAG
 * endpoints — depend only on this interface.
 */
public interface ChatCompletionService {

    /**
     * Produce a text answer.
     *
     * @param systemInstruction frozen role/behavior prompt (stable prefix)
     * @param cacheableContext  large reusable context, e.g. a conversation transcript;
     *                          the implementation should mark it for prompt caching so
     *                          repeated questions over the same context are cheap
     * @param userQuestion      the volatile per-request question
     * @return the model's text answer
     */
    String complete(String systemInstruction, String cacheableContext, String userQuestion);
}
