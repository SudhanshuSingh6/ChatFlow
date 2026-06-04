package com.chatflow.ai.search;

import com.chatflow.ai.embedding.EmbeddingResult;
import com.chatflow.ai.embedding.EmbeddingService;
import com.chatflow.ai.embedding.MessageEmbeddingRepository;
import com.chatflow.contracts.dto.EmbeddingSearchHit;
import com.chatflow.contracts.dto.EmbeddingSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Serves semantic (vector) search to core: embeds the query and runs it over ai's store,
 * scoped to the conversations core says the caller may see. ai owns the embeddings; core owns
 * authorization (it passes the conversation scope) and message hydration.
 */
@Service
@RequiredArgsConstructor
public class EmbeddingSearchService {

    private static final int DEFAULT_LIMIT = 20;

    private final EmbeddingService embeddingService;
    private final MessageEmbeddingRepository repository;

    public List<EmbeddingSearchHit> search(EmbeddingSearchRequest request) {
        if (request.conversationIds() == null || request.conversationIds().isEmpty()
                || request.query() == null || request.query().isBlank()) {
            return List.of();
        }
        int limit = request.limit() <= 0 ? DEFAULT_LIMIT : request.limit();
        EmbeddingResult embedded = embeddingService.embed(request.query().trim());
        return repository.searchByVectorInConversations(request.conversationIds(), embedded.vector(), limit);
    }
}
