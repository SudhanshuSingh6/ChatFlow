package com.chatflow.ai.search;

import com.chatflow.contracts.dto.EmbeddingSearchHit;
import com.chatflow.contracts.dto.EmbeddingSearchRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Service-to-service vector search for core's hybrid message search. Guarded by the shared
 * {@code X-Internal-Token} (path is permitted in {@code SecurityConfig}); not for end users.
 */
@RestController
@RequestMapping("/internal/embeddings")
@RequiredArgsConstructor
public class InternalSearchController {

    private final EmbeddingSearchService searchService;

    @Value("${app.internal.token:dev-internal-token}")
    private String internalToken;

    @PostMapping("/search")
    public List<EmbeddingSearchHit> search(@RequestBody EmbeddingSearchRequest request,
                                           @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        if (token == null || !token.equals(internalToken)) {
            throw new SecurityException("Invalid internal token");
        }
        return searchService.search(request);
    }
}
