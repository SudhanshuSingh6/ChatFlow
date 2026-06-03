package com.chatflow.conversation.service;

import com.chatflow.ai.chat.ChatCompletionService;
import com.chatflow.ai.embedding.EmbeddingResult;
import com.chatflow.ai.embedding.EmbeddingService;
import com.chatflow.conversation.search.MessageEmbeddingRepository;
import com.chatflow.conversation.search.VectorSearchHit;
import com.chatflow.conversation.dto.AskResponse;
import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.entity.MessageType;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.user.entity.User;
import com.chatflow.user.repository.UserRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ConversationRagServiceTest {

    private final ConversationParticipantRepository participantRepository =
            mock(ConversationParticipantRepository.class);
    private final MessageEmbeddingRepository embeddingRepository = mock(MessageEmbeddingRepository.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final ChatCompletionService chat = mock(ChatCompletionService.class);

    private final ConversationRagService service = new ConversationRagService(
            participantRepository, embeddingRepository, embeddingService,
            messageRepository, userRepository, chat);

    private final UUID caller = UUID.randomUUID();
    private final UUID conversationId = UUID.randomUUID();

    @Test
    void rejectsShortQuestion() {
        assertThatThrownBy(() -> service.ask(caller, conversationId, "a"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(embeddingService, chat);
    }

    @Test
    void rejectsNonParticipant() {
        when(participantRepository.existsByConversationIdAndUserId(conversationId, caller)).thenReturn(false);

        assertThatThrownBy(() -> service.ask(caller, conversationId, "what about the launch?"))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(embeddingService, chat);
    }

    @Test
    void returnsFriendlyMessageWhenNoHits() {
        when(participantRepository.existsByConversationIdAndUserId(conversationId, caller)).thenReturn(true);
        when(embeddingService.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f, 0.2f}, "m", 2));
        when(embeddingRepository.searchByVectorInConversation(eq(conversationId), any(), anyInt()))
                .thenReturn(List.of());

        AskResponse response = service.ask(caller, conversationId, "what about the launch?");

        assertThat(response.citations()).isEmpty();
        assertThat(response.answer()).contains("couldn't find");
        verifyNoInteractions(chat);
    }

    @Test
    void answersWithCitations() {
        UUID alice = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        when(participantRepository.existsByConversationIdAndUserId(conversationId, caller)).thenReturn(true);
        when(embeddingService.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f, 0.2f}, "m", 2));
        when(embeddingRepository.searchByVectorInConversation(eq(conversationId), any(), anyInt()))
                .thenReturn(List.of(new VectorSearchHit(messageId, 0.91)));

        Message m = Message.builder().id(messageId).senderId(alice)
                .type(MessageType.TEXT).content("launch moved to Friday").sequenceNumber(42L).build();
        when(messageRepository.findAllById(any())).thenReturn(List.of(m));

        User u = mock(User.class);
        when(u.getId()).thenReturn(alice);
        when(u.getUsername()).thenReturn("alice");
        when(userRepository.findAllById(any())).thenReturn(List.of(u));

        when(chat.complete(anyString(), anyString(), anyString())).thenReturn("The launch moved to Friday.");

        AskResponse response = service.ask(caller, conversationId, "when is the launch?");

        assertThat(response.answer()).isEqualTo("The launch moved to Friday.");
        assertThat(response.citations()).hasSize(1);
        AskResponse.Citation c = response.citations().get(0);
        assertThat(c.messageId()).isEqualTo(messageId);
        assertThat(c.sequenceNumber()).isEqualTo(42L);
        assertThat(c.similarity()).isEqualTo(0.91);
    }
}
