package com.chatflow.conversation.dto;

import com.chatflow.conversation.entity.ConversationParticipant;
import com.chatflow.conversation.entity.ParticipantRole;

import java.time.Instant;
import java.util.UUID;

public record ParticipantResponse(
        UUID userId,
        String username,
        ParticipantRole role,
        Instant joinedAt,
        long lastReadSeq,
        long lastDeliveredSeq
) {
    public static ParticipantResponse from(ConversationParticipant p, String username) {
        return new ParticipantResponse(
                p.getUserId(),
                username,
                p.getRole(),
                p.getJoinedAt(),
                p.getLastReadSeq(),
                p.getLastDeliveredSeq()
        );
    }
}
