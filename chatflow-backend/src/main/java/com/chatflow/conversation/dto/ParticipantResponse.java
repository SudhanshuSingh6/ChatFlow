package com.chatflow.conversation.dto;

import com.chatflow.conversation.entity.ConversationParticipant;
import com.chatflow.conversation.entity.ParticipantRole;

import java.time.Instant;
import java.util.UUID;

public record ParticipantResponse(
        UUID userId,
        ParticipantRole role,
        Instant joinedAt,
        long lastReadSeq,
        long lastDeliveredSeq
) {
    public static ParticipantResponse from(ConversationParticipant p) {
        return new ParticipantResponse(
                p.getUserId(),
                p.getRole(),
                p.getJoinedAt(),
                p.getLastReadSeq(),
                p.getLastDeliveredSeq()
        );
    }
}
