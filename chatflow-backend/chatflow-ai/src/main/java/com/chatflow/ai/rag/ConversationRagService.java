package com.chatflow.ai.rag;

import com.chatflow.ai.chat.ChatCompletionService;
import com.chatflow.ai.embedding.EmbeddingResult;
import com.chatflow.ai.embedding.EmbeddingService;
import com.chatflow.ai.embedding.MessageEmbeddingRepository;
import com.chatflow.ai.embedding.VectorSearchHit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * RAG "ask your chat history" — embeds a question, retrieves the top-k most relevant messages
 * from this conversation via pgvector, and has the {@link ChatCompletionService} answer grounded
 * in those messages, with citations so it's verifiable.
 *
 * <p>Self-contained: context and citations are built entirely from ai-service's denormalized
 * embedding store (snippet + sender + sequence inline). The only cross-service call is the
 * membership check against core — message data never leaves the local store.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationRagService {

    private static final int TOP_K = 10;
    private static final int PREVIEW_LEN = 160;
    private static final int MAX_QUESTION_LEN = 1000;

    private static final String SYSTEM = """
            You answer questions using ONLY the chat messages provided as context. Each message
            is prefixed with its id in square brackets, e.g. [<uuid>]. Ground every claim in those
            messages and cite the message ids you used inline in brackets. If the answer is not in
            the provided messages, say you don't know — do not use outside knowledge or guess.""";

    private final ConversationAccessClient accessClient;
    private final MessageEmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;
    private final ChatCompletionService chatCompletionService;

    public AskResponse ask(UUID callerId, UUID conversationId, String question) {
        String trimmed = question == null ? "" : question.trim();
        if (trimmed.length() < 2) {
            throw new IllegalArgumentException("Question must be at least 2 characters");
        }
        if (trimmed.length() > MAX_QUESTION_LEN) {
            throw new IllegalArgumentException("Question must be at most " + MAX_QUESTION_LEN + " characters");
        }
        if (!accessClient.isParticipant(conversationId, callerId)) {
            throw new SecurityException("You are not a participant in this conversation");
        }

        EmbeddingResult embedded = embeddingService.embed(trimmed);
        List<VectorSearchHit> hits = embeddingRepository.searchByVectorInConversation(
                conversationId, embedded.vector(), TOP_K);
        if (hits.isEmpty()) {
            return new AskResponse("I couldn't find anything relevant in this conversation.", List.of());
        }

        StringBuilder context = new StringBuilder();
        List<AskResponse.Citation> citations = new ArrayList<>(hits.size());
        for (VectorSearchHit hit : hits) {
            String who = hit.senderName() == null
                    ? (hit.senderId() == null ? "System" : "Unknown")
                    : hit.senderName();
            String content = hit.contentSnippet();
            context.append('[').append(hit.messageId()).append("] ")
                    .append(who).append(": ").append(content).append('\n');
            citations.add(new AskResponse.Citation(
                    hit.messageId(), hit.sequenceNumber(), hit.similarity(), preview(content)));
        }

        String answer = chatCompletionService.complete(SYSTEM, context.toString(), trimmed);
        return new AskResponse(answer, citations);
    }

    private static String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= PREVIEW_LEN ? content : content.substring(0, PREVIEW_LEN);
    }
}
