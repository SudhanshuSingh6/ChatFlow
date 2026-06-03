package com.chatflow.conversation.service;

import com.chatflow.ai.chat.ChatCompletionService;
import com.chatflow.conversation.dto.SummaryResponse;
import com.chatflow.conversation.entity.ConversationParticipant;
import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.entity.MessageType;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.user.entity.User;
import com.chatflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConversationSummaryServiceTest {

    private final ConversationParticipantRepository participantRepository =
            mock(ConversationParticipantRepository.class);
    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ChatCompletionService chat = mock(ChatCompletionService.class);

    private final ConversationSummaryService service = new ConversationSummaryService(
            participantRepository, messageRepository, userRepository, chat);

    private final UUID caller = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @Test
    void rejectsNonParticipant() {
        when(participantRepository.findByConversationIdAndUserId(conversationId, caller))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.summarizeUnread(caller, conversationId))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(chat);
    }

    @Test
    void returnsCaughtUpWhenNothingUnread() {
        ConversationParticipant me = mock(ConversationParticipant.class);
        when(me.getLastReadSeq()).thenReturn(7L);
        when(participantRepository.findByConversationIdAndUserId(conversationId, caller))
                .thenReturn(Optional.of(me));
        when(messageRepository.findPageAfter(eq(conversationId), eq(7L), any()))
                .thenReturn(List.of());

        SummaryResponse response = service.summarizeUnread(caller, conversationId);

        assertThat(response.messageCount()).isZero();
        assertThat(response.summary()).contains("caught up");
        verifyNoInteractions(chat);
    }

    @Test
    void summarizesUnreadMessages() {
        UUID alice = UUID.randomUUID();
        ConversationParticipant me = mock(ConversationParticipant.class);
        when(me.getLastReadSeq()).thenReturn(0L);
        when(participantRepository.findByConversationIdAndUserId(conversationId, caller))
                .thenReturn(Optional.of(me));

        Message m1 = Message.builder().id(UUID.randomUUID()).senderId(alice)
                .type(MessageType.TEXT).content("lunch at 1?").sequenceNumber(1L).build();
        Message m2 = Message.builder().id(UUID.randomUUID()).senderId(alice)
                .type(MessageType.TEXT).content("bring the deck").sequenceNumber(2L).build();
        when(messageRepository.findPageAfter(eq(conversationId), eq(0L), any()))
                .thenReturn(List.of(m1, m2));

        User u = mock(User.class);
        when(u.getId()).thenReturn(alice);
        when(u.getUsername()).thenReturn("alice");
        when(userRepository.findAllById(any())).thenReturn(List.of(u));

        when(chat.complete(anyString(), anyString(), anyString())).thenReturn("SUMMARY");

        SummaryResponse response = service.summarizeUnread(caller, conversationId);

        assertThat(response.summary()).isEqualTo("SUMMARY");
        assertThat(response.messageCount()).isEqualTo(2);
        assertThat(response.fromSequence()).isEqualTo(1L);
        assertThat(response.toSequence()).isEqualTo(2L);
    }
}
