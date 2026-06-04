package com.chatflow.media.service;

import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.media.entity.MediaMessage;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class MediaAccessGuardTest {

    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final ConversationParticipantRepository participantRepository =
            mock(ConversationParticipantRepository.class);
    private final MediaAccessGuard guard =
            new MediaAccessGuard(messageRepository, participantRepository);

    private MediaMessage media(UUID messageId) {
        return MediaMessage.builder()
                .id(UUID.randomUUID())
                .senderId(UUID.randomUUID())
                .messageId(messageId)
                .build();
    }

    private Message parent(UUID conversationId) {
        return Message.builder().conversationId(conversationId).build();
    }

    @Test
    void conversationParticipantHasReadAccess() {
        UUID caller = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MediaMessage m = media(messageId);

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(parent(conversationId)));
        when(participantRepository.existsByConversationIdAndUserId(conversationId, caller))
                .thenReturn(true);

        assertThatCode(() -> guard.requireReadAccess(caller, m)).doesNotThrowAnyException();
    }

    @Test
    void nonParticipantIsDeniedReadAccess() {
        UUID caller = UUID.randomUUID();
        UUID conversationId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MediaMessage m = media(messageId);

        when(messageRepository.findById(messageId)).thenReturn(Optional.of(parent(conversationId)));
        when(participantRepository.existsByConversationIdAndUserId(conversationId, caller))
                .thenReturn(false);

        assertThatThrownBy(() -> guard.requireReadAccess(caller, m))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void missingParentMessageIsDenied() {
        UUID caller = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        MediaMessage m = media(messageId);

        when(messageRepository.findById(messageId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> guard.requireReadAccess(caller, m))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void onlySenderMayDelete() {
        UUID sender = UUID.randomUUID();
        MediaMessage m = MediaMessage.builder()
                .id(UUID.randomUUID()).senderId(sender).messageId(UUID.randomUUID()).build();

        assertThatCode(() -> guard.requireDeleteAccess(sender, m)).doesNotThrowAnyException();
        assertThatThrownBy(() -> guard.requireDeleteAccess(UUID.randomUUID(), m))
                .isInstanceOf(SecurityException.class);
    }
}
