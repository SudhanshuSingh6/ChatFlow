package com.chatflow.ai.rag;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.UUID;

/**
 * RAG endpoint. The caller is taken from the validated JWT (same {@code principal.getName()}
 * → user id convention as core); membership is enforced inside the service via core.
 */
@RestController
@RequestMapping("/ai/conversations")
@RequiredArgsConstructor
public class RagController {

    private final ConversationRagService ragService;

    @PostMapping("/{conversationId}/ask")
    public AskResponse ask(@PathVariable UUID conversationId,
                           @RequestBody AskRequest request,
                           Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        return ragService.ask(callerId, conversationId, request.question());
    }
}
