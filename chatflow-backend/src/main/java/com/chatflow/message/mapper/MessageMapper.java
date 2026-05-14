package com.chatflow.message.mapper;

import com.chatflow.message.dto.ConversationResponse;
import com.chatflow.message.dto.MessageResponse;
import com.chatflow.message.entity.Conversation;
import com.chatflow.message.entity.Message;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.UUID;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class MessageMapper {

    public static ConversationResponse toConversationResponse(Conversation conversation, UUID callerId) {
        boolean callerIsP1 = callerId.equals(conversation.getParticipantOneId());

        UUID otherUserId = callerIsP1
                ? conversation.getParticipantTwoId()
                : conversation.getParticipantOneId();

        int unreadCount = callerIsP1
                ? conversation.getUnreadCountP1()
                : conversation.getUnreadCountP2();

        return ConversationResponse.builder()
                .id(conversation.getId())
                .otherUserId(otherUserId)
                .lastMessage(conversation.getLastMessage())
                .lastMessageAt(conversation.getLastMessageAt())
                .unreadCount(unreadCount)
                .createdAt(conversation.getCreatedAt())
                .build();
    }

    public static MessageResponse toMessageResponse(Message message) {
        return MessageResponse.from(message);
    }
}