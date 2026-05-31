package com.chatflow.presence.controller;

import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.ConversationRepository;
import com.chatflow.presence.dto.ConversationPresenceResponse;
import com.chatflow.presence.dto.PresenceResponse;
import com.chatflow.presence.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presenceService;
    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;

    @GetMapping("/users/{userId}/presence")
    public PresenceResponse getUserPresence(@PathVariable UUID userId, Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        if (!callerId.equals(userId)
                && !participantRepository.existsSharedConversation(callerId, userId)) {
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

        if (!conversationRepository.existsById(conversationId)) {
            throw new IllegalArgumentException("Conversation not found: " + conversationId);
        }

        List<UUID> participantIds = participantRepository.findUserIdsByConversationId(conversationId);
        if (!participantIds.contains(callerId)) {
            throw new SecurityException(
                    "User " + callerId + " is not a participant in conversation " + conversationId);
        }

        // Two-party presence (the DIRECT case); for groups the first two are reported.
        PresenceResponse p1 = participantIds.isEmpty() ? null : buildPresenceResponse(participantIds.get(0));
        PresenceResponse p2 = participantIds.size() < 2 ? null : buildPresenceResponse(participantIds.get(1));

        return ConversationPresenceResponse.builder()
                .participantOne(p1)
                .participantTwo(p2)
                .build();
    }

    private PresenceResponse buildPresenceResponse(UUID userId) {
        Instant onlineSince = presenceService.getOnlineSince(userId).orElse(null);
        return PresenceResponse.of(userId, onlineSince != null, onlineSince);
    }
}
