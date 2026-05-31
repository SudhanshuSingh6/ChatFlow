package com.chatflow.conversation.dto;

import com.chatflow.conversation.entity.Conversation;
import com.chatflow.conversation.entity.ConversationType;
import com.chatflow.conversation.entity.Message;

import java.time.Instant;
import java.util.UUID;

/**
 * One hit from a message search. Carries enough conversation context for the
 * client to render and deep-link the result without a second round trip.
 */
public record MessageSearchResult(
        UUID messageId,
        UUID conversationId,
        ConversationType conversationType,
        String conversationName,
        UUID senderId,
        String content,
        long sequenceNumber,
        Instant createdAt
) {
    public static MessageSearchResult of(Message m, Conversation c) {
        return new MessageSearchResult(
                m.getId(),
                m.getConversationId(),
                c == null ? null : c.getType(),
                c == null ? null : c.getName(),
                m.getSenderId(),
                m.getContent(),
                m.getSequenceNumber(),
                m.getCreatedAt()
        );
    }
}
