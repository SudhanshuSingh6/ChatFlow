package com.chatflow.conversation.service;

import com.chatflow.ai.chat.ChatCompletionService;
import com.chatflow.ai.embedding.EmbeddingResult;
import com.chatflow.ai.embedding.EmbeddingService;
import com.chatflow.conversation.search.MessageEmbeddingRepository;
import com.chatflow.conversation.search.VectorSearchHit;
import com.chatflow.conversation.dto.AskResponse;
import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * RAG "ask your chat history" — embeds a question, retrieves the top-k most relevant
 * messages from this conversation via pgvector, and has the {@link ChatCompletionService}
 * answer grounded in those messages. The answer is accompanied by citations (the retrieved
 * messages) so it is verifiable. Conversation-scoped; a global "/ask" is a later follow-up.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationRagService {

    private static final int TOP_K = 10;
    private static final int PREVIEW_LEN = 160;

    private static final String SYSTEM = """
            You answer questions using ONLY the chat messages provided as context. Each message
            is prefixed with its id in square brackets, e.g. [<uuid>]. Ground every claim in those
            messages and cite the message ids you used inline in brackets. If the answer is not in
            the provided messages, say you don't know — do not use outside knowledge or guess.""";

    private final ConversationParticipantRepository participantRepository;
    private final MessageEmbeddingRepository embeddingRepository;
    private final EmbeddingService embeddingService;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatCompletionService chatCompletionService;

    @Transactional(readOnly = true)
    public AskResponse ask(UUID callerId, UUID conversationId, String question) {
        String trimmed = question == null ? "" : question.trim();
        if (trimmed.length() < 2) {
            throw new IllegalArgumentException("Question must be at least 2 characters");
        }
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, callerId)) {
            throw new SecurityException("You are not a participant in this conversation");
        }

        EmbeddingResult embedded = embeddingService.embed(trimmed);
        List<VectorSearchHit> hits = embeddingRepository.searchByVectorInConversation(
                conversationId, embedded.vector(), TOP_K);
        if (hits.isEmpty()) {
            return new AskResponse("I couldn't find anything relevant in this conversation.", List.of());
        }

        Map<UUID, Message> messagesById = hydrate(hits);
        Map<UUID, String> names = resolveSenderNames(messagesById.values());

        StringBuilder context = new StringBuilder();
        List<AskResponse.Citation> citations = new ArrayList<>(hits.size());
        for (VectorSearchHit hit : hits) {
            Message m = messagesById.get(hit.messageId());
            if (m == null) {
                continue; // deleted/purged between retrieval and hydration
            }
            String who = m.getSenderId() == null ? "System" : names.getOrDefault(m.getSenderId(), "Unknown");
            String content = m.getContent() == null ? "[non-text message]" : m.getContent();
            context.append('[').append(m.getId()).append("] ")
                    .append(who).append(": ").append(content).append('\n');
            citations.add(new AskResponse.Citation(
                    m.getId(), m.getSequenceNumber(), hit.similarity(), preview(content)));
        }

        String answer = chatCompletionService.complete(SYSTEM, context.toString(), trimmed);
        return new AskResponse(answer, citations);
    }

    private Map<UUID, Message> hydrate(List<VectorSearchHit> hits) {
        List<UUID> ids = hits.stream().map(VectorSearchHit::messageId).toList();
        Map<UUID, Message> byId = new HashMap<>();
        messageRepository.findAllById(ids).forEach(m -> byId.put(m.getId(), m));
        return byId;
    }

    private Map<UUID, String> resolveSenderNames(Iterable<Message> messages) {
        Set<UUID> senderIds = new java.util.HashSet<>();
        messages.forEach(m -> {
            if (m.getSenderId() != null) {
                senderIds.add(m.getSenderId());
            }
        });
        return userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getUsername()));
    }

    private static String preview(String content) {
        if (content == null) {
            return null;
        }
        return content.length() <= PREVIEW_LEN ? content : content.substring(0, PREVIEW_LEN);
    }
}
