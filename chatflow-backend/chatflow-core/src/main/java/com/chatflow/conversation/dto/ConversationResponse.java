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
        /** Display title: group name for GROUP, the other participant's username for DIRECT. */
        String title,
        /** The other participant for DIRECT conversations; null for GROUP. */
        UUID peerId,
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
                                               int memberCount,
                                               String title,
                                               UUID peerId) {
        return new ConversationResponse(
                c.getId(), c.getType(), c.getName(), title, peerId, c.getCreatedBy(),
                null, c.getLastMessagePreview(), c.getLastMessageAt(), c.getLastMessageSeq(),
                unread, callerRole, memberCount);
    }

    /** Full detail with the participant roster. */
    public static ConversationResponse detail(Conversation c,
                                              List<ParticipantResponse> participants,
                                              ParticipantRole callerRole,
                                              long unread,
                                              String title,
                                              UUID peerId) {
        return new ConversationResponse(
                c.getId(), c.getType(), c.getName(), title, peerId, c.getCreatedBy(),
                participants, c.getLastMessagePreview(), c.getLastMessageAt(), c.getLastMessageSeq(),
                unread, callerRole, participants.size());
    }
}
