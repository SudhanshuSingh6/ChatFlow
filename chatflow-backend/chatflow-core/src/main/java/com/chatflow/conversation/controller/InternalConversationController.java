package com.chatflow.conversation.controller;

import com.chatflow.contracts.dto.ConversationTranscript;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.service.ConversationTranscriptService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service ("internal") endpoints — not for end users. Provides what ai-service
 * needs but core owns: conversation membership (RAG authorization) and the unread transcript
 * (summary input).
 *
 * <p>Guarded by a shared {@code X-Internal-Token} rather than a user JWT; the path is
 * permitted in {@code SecurityConfig} so the token is the only gate. Replace with mTLS / a
 * gateway-injected identity when the platform matures.
 */
@RestController
@RequestMapping("/internal/conversations")
@RequiredArgsConstructor
public class InternalConversationController {

    private final ConversationParticipantRepository participantRepository;
    private final ConversationTranscriptService transcriptService;

    @Value("${app.internal.token:dev-internal-token}")
    private String internalToken;

    @GetMapping("/{conversationId}/participants/{userId}")
    public boolean isParticipant(@PathVariable UUID conversationId,
                                 @PathVariable UUID userId,
                                 @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requireInternalToken(token);
        return participantRepository.existsByConversationIdAndUserId(conversationId, userId);
    }

    /** The caller's unread backlog past their read watermark — input for ai-service's summary. */
    @GetMapping("/{conversationId}/transcript/unread")
    public ConversationTranscript unreadTranscript(@PathVariable UUID conversationId,
                                                   @RequestParam UUID userId,
                                                   @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requireInternalToken(token);
        return transcriptService.unreadFor(conversationId, userId);
    }

    private void requireInternalToken(String token) {
        if (token == null || !token.equals(internalToken)) {
            throw new SecurityException("Invalid internal token");
        }
    }
}
