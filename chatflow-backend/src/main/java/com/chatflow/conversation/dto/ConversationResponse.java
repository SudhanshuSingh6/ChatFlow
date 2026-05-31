package com.chatflow.conversation.dto;

import com.chatflow.conversation.entity.Conversation;
import com.chatflow.conversation.entity.ConversationType;
import com.chatflow.conversation.entity.ParticipantRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ConversationResponse(
        UUID id,
        ConversationType type,
        String name,
        UUID createdBy,
        /** Null in list views; populated in detail views. */
        List<ParticipantResponse> participants,
        String lastMessagePreview,
        Instant lastMessageAt,
        Long lastMessageSeq,
        long unreadCount,
        ParticipantRole callerRole,
        int memberCount
) {
    /** Lightweight summary for list views (no participant roster). */
    public static ConversationResponse summary(Conversation c,
                                               ParticipantRole callerRole,
                                               long unread,
                                               int memberCount) {
        return new ConversationResponse(
                c.getId(), c.getType(), c.getName(), c.getCreatedBy(),
                null, c.getLastMessagePreview(), c.getLastMessageAt(), c.getLastMessageSeq(),
                unread, callerRole, memberCount);
    }

    /** Full detail with the participant roster. */
    public static ConversationResponse detail(Conversation c,
                                              List<ParticipantResponse> participants,
                                              ParticipantRole callerRole,
                                              long unread) {
        return new ConversationResponse(
                c.getId(), c.getType(), c.getName(), c.getCreatedBy(),
                participants, c.getLastMessagePreview(), c.getLastMessageAt(), c.getLastMessageSeq(),
                unread, callerRole, participants.size());
    }
}
