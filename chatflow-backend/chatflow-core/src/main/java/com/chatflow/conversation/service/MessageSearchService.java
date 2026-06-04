package com.chatflow.conversation.service;

import com.chatflow.contracts.dto.EmbeddingSearchHit;
import com.chatflow.conversation.dto.MessageSearchResult;
import com.chatflow.conversation.dto.RankedSearchResult;
import com.chatflow.conversation.dto.SearchPageResponse;
import com.chatflow.conversation.entity.Conversation;
import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.ConversationRepository;
import com.chatflow.conversation.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Cross-conversation message search. Because DIRECT and GROUP chats now share one
 * {@code messages} table, this is a single query over the unified model.
 *
 * <p>Keyword search is local (full-text over core's {@code messages}); the semantic side of
 * hybrid search is delegated to ai-service, which owns the embedding store. core supplies the
 * caller's conversation scope (it owns membership), then hydrates + merges the hits locally.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSearchService {

    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 20;
    /** Reciprocal Rank Fusion constant; dampens the weight of lower-ranked hits. */
    private static final int RRF_K = 60;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final EmbeddingSearchClient embeddingSearchClient;

    @Transactional(readOnly = true)
    public SearchPageResponse search(UUID userId, String query, String cursor, int limit) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < 2) {
            throw new IllegalArgumentException("Search query must be at least 2 characters");
        }
        int pageSize = Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT);

        String likePattern = "%" + escapeLike(trimmed.toLowerCase()) + "%";
        SearchCursor decoded = SearchCursor.decode(cursor);
        Instant beforeTime = decoded == null ? null : decoded.createdAt();
        UUID beforeId = decoded == null ? null : decoded.id();

        // Over-fetch by one to detect whether another page exists.
        List<Message> rows = messageRepository.searchForUser(
                userId, likePattern, beforeTime, beforeId, PageRequest.of(0, pageSize + 1));

        boolean hasMore = rows.size() > pageSize;
        List<Message> page = hasMore ? rows.subList(0, pageSize) : rows;

        Map<UUID, Conversation> convCache = new HashMap<>();
        List<MessageSearchResult> results = page.stream()
                .map(m -> MessageSearchResult.of(m, convCache.computeIfAbsent(
                        m.getConversationId(),
                        id -> conversationRepository.findById(id).orElse(null))))
                .toList();

        String nextCursor = null;
        if (hasMore) {
            Message last = page.get(page.size() - 1);
            nextCursor = new SearchCursor(last.getCreatedAt(), last.getId()).encode();
        }

        return new SearchPageResponse(results, nextCursor);
    }

    /**
     * Hybrid semantic + keyword search. Runs the local {@code LIKE} search and a vector search
     * (via ai-service), then merges them with Reciprocal Rank Fusion so the two sources combine
     * without normalizing their incomparable scores. Returns top-k ranked results (no cursor —
     * semantic relevance isn't a stable pagination key). If ai is unavailable, it degrades
     * gracefully to keyword-only.
     */
    @Transactional(readOnly = true)
    public List<RankedSearchResult> hybridSearch(UUID userId, String query, int limit) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.length() < 2) {
            throw new IllegalArgumentException("Search query must be at least 2 characters");
        }
        int pageSize = Math.min(limit <= 0 ? DEFAULT_LIMIT : limit, MAX_LIMIT);

        String likePattern = "%" + escapeLike(trimmed.toLowerCase()) + "%";
        List<Message> keywordHits = messageRepository.searchForUser(
                userId, likePattern, null, null, PageRequest.of(0, pageSize));

        // Semantic hits from ai-service, scoped to the caller's conversations (core owns membership).
        List<UUID> conversationIds = participantRepository.findConversationIdsByUserId(userId);
        List<EmbeddingSearchHit> vectorHits = embeddingSearchClient.search(trimmed, conversationIds, pageSize);

        // Reciprocal Rank Fusion: rankScore = Σ 1 / (RRF_K + rank) across both ranked lists.
        Map<UUID, Double> rankScore = new HashMap<>();
        Map<UUID, Double> similarityById = new HashMap<>();
        Map<UUID, Message> messageById = new HashMap<>();
        for (int i = 0; i < keywordHits.size(); i++) {
            Message m = keywordHits.get(i);
            messageById.put(m.getId(), m);
            rankScore.merge(m.getId(), 1.0 / (RRF_K + i + 1), Double::sum);
        }
        for (int i = 0; i < vectorHits.size(); i++) {
            EmbeddingSearchHit hit = vectorHits.get(i);
            rankScore.merge(hit.messageId(), 1.0 / (RRF_K + i + 1), Double::sum);
            similarityById.put(hit.messageId(), hit.similarity());
        }

        // Hydrate messages only present in the vector list (keyword hits are already loaded).
        List<UUID> missing = rankScore.keySet().stream()
                .filter(id -> !messageById.containsKey(id))
                .toList();
        if (!missing.isEmpty()) {
            messageRepository.findAllById(missing).forEach(m -> messageById.put(m.getId(), m));
        }

        Map<UUID, Conversation> convCache = new HashMap<>();
        return rankScore.entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue().reversed())
                .limit(pageSize)
                .map(entry -> {
                    Message m = messageById.get(entry.getKey());
                    if (m == null) {
                        return null; // disappeared / deleted between the two queries
                    }
                    Conversation c = convCache.computeIfAbsent(m.getConversationId(),
                            id -> conversationRepository.findById(id).orElse(null));
                    return new RankedSearchResult(
                            MessageSearchResult.of(m, c), entry.getValue(), similarityById.get(entry.getKey()));
                })
                .filter(Objects::nonNull)
                .toList();
    }

    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
