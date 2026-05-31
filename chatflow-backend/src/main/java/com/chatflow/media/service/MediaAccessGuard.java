package com.chatflow.media.service;

import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.media.entity.MediaMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Centralised authorization for media messages. Access is resolved through the
 * parent {@code messages} row: the caller must be a participant of that message's
 * conversation. Single source of truth shared by read access and deletion.
 */
@Component
@RequiredArgsConstructor
public class MediaAccessGuard {

    private final MessageRepository messageRepository;
    private final ConversationParticipantRepository participantRepository;

    /** Caller must be a participant of the conversation owning the media's message. */
    public void requireReadAccess(java.util.UUID callerId, MediaMessage media) {
        Message message = messageRepository.findById(media.getMessageId())
                .orElseThrow(() -> new SecurityException(
                        "Access denied to media message " + media.getId()));
        if (!participantRepository.existsByConversationIdAndUserId(
                message.getConversationId(), callerId)) {
            throw new SecurityException("Access denied to media message " + media.getId());
        }
    }

    /** Only the original sender may delete a media message. */
    public void requireDeleteAccess(java.util.UUID callerId, MediaMessage media) {
        if (!media.getSenderId().equals(callerId)) {
            throw new SecurityException(
                    "User " + callerId + " may not delete media message " + media.getId());
        }
    }
}
