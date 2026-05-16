package com.chatflow.presence.controller;

import com.chatflow.message.entity.Conversation;
import com.chatflow.message.repository.ConversationRepository;
import com.chatflow.presence.dto.ConversationPresenceResponse;
import com.chatflow.presence.dto.PresenceResponse;
import com.chatflow.presence.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;
    private final ConversationRepository conversationRepository;

    @GetMapping("/users/{userId}/presence")
    public PresenceResponse getUserPresence(@PathVariable UUID userId, Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        if (!callerId.equals(userId)
                && !conversationRepository.existsConversationBetween(callerId, userId)) {
            throw new SecurityException("User " + callerId
                    + " cannot view presence for user " + userId);
        }

        Instant onlineSince = presenceService.getOnlineSince(userId).orElse(null);
        return PresenceResponse.of(userId, onlineSince != null, onlineSince);
    }

    @GetMapping("/conversations/{conversationId}/presence")
    public ConversationPresenceResponse getConversationPresence(
            @PathVariable UUID conversationId,
            Principal principal) {

        UUID callerId = UUID.fromString(principal.getName());

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Conversation not found: " + conversationId));

        if (!isParticipant(conversation, callerId)) {
            throw new SecurityException(
                    "User " + callerId + " is not a participant in conversation " + conversationId);
        }

        PresenceResponse p1 = buildPresenceResponse(conversation.getParticipantOneId());
        PresenceResponse p2 = buildPresenceResponse(conversation.getParticipantTwoId());

        return ConversationPresenceResponse.builder()
                .participantOne(p1)
                .participantTwo(p2)
                .build();
    }

    private PresenceResponse buildPresenceResponse(UUID userId) {
        Instant onlineSince = presenceService.getOnlineSince(userId).orElse(null);
        return PresenceResponse.of(userId, onlineSince != null, onlineSince);
    }

    private boolean isParticipant(Conversation conversation, UUID userId) {
        return userId.equals(conversation.getParticipantOneId())
                || userId.equals(conversation.getParticipantTwoId());
    }
}
