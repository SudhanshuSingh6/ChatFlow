package com.chatflow.conversation.service;

import com.chatflow.conversation.dto.MessageSearchResult;
import com.chatflow.conversation.dto.SearchPageResponse;
import com.chatflow.conversation.entity.Conversation;
import com.chatflow.conversation.entity.Message;
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
import java.util.UUID;

/**
 * Cross-conversation message search. Because DIRECT and GROUP chats now share one
 * {@code messages} table, this is a single query over the unified model — replacing
 * the old service that merged two separate direct/group result streams.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageSearchService {

    private static final int MAX_LIMIT = 50;
    private static final int DEFAULT_LIMIT = 20;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;

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

    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
