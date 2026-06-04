package com.chatflow.ai.rag;

import com.chatflow.ai.chat.ChatCompletionService;
import com.chatflow.ai.embedding.EmbeddingResult;
import com.chatflow.ai.embedding.EmbeddingService;
import com.chatflow.ai.embedding.MessageEmbeddingRepository;
import com.chatflow.ai.embedding.VectorSearchHit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationRagServiceTest {

    @Mock ConversationAccessClient accessClient;
    @Mock MessageEmbeddingRepository repository;
    @Mock EmbeddingService embeddingService;
    @Mock ChatCompletionService chatCompletionService;
    @InjectMocks ConversationRagService service;

    private final UUID caller = UUID.randomUUID();
    private final UUID conversation = UUID.randomUUID();

    @Test
    void rejectsNonParticipantsWithoutEmbedding() {
        when(accessClient.isParticipant(conversation, caller)).thenReturn(false);

        assertThatThrownBy(() -> service.ask(caller, conversation, "what did we decide?"))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(embeddingService, chatCompletionService);
    }

    @Test
    void rejectsTooShortQuestionBeforeAnyCall() {
        assertThatThrownBy(() -> service.ask(caller, conversation, "a"))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(accessClient, embeddingService);
    }

    @Test
    void returnsFallbackWhenNoHits() {
        when(accessClient.isParticipant(conversation, caller)).thenReturn(true);
        when(embeddingService.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f}, "m", 1));
        when(repository.searchByVectorInConversation(eq(conversation), any(), anyInt()))
                .thenReturn(List.of());

        AskResponse response = service.ask(caller, conversation, "anything?");

        assertThat(response.citations()).isEmpty();
        assertThat(response.answer()).contains("couldn't find");
        verifyNoInteractions(chatCompletionService);
    }

    @Test
    void buildsGroundedAnswerWithCitationsFromTheStore() {
        UUID m1 = UUID.randomUUID();
        when(accessClient.isParticipant(conversation, caller)).thenReturn(true);
        when(embeddingService.embed(anyString()))
                .thenReturn(new EmbeddingResult(new float[]{0.1f}, "m", 1));
        when(repository.searchByVectorInConversation(eq(conversation), any(), anyInt()))
                .thenReturn(List.of(new VectorSearchHit(
                        m1, UUID.randomUUID(), "alice", 5L, "let's ship friday", 0.92)));
        when(chatCompletionService.complete(anyString(), contains("alice: let's ship friday"), anyString()))
                .thenReturn("You decided to ship Friday. [" + m1 + "]");

        AskResponse response = service.ask(caller, conversation, "when do we ship?");

        assertThat(response.answer()).contains("ship Friday");
        assertThat(response.citations()).singleElement().satisfies(c -> {
            assertThat(c.messageId()).isEqualTo(m1);
            assertThat(c.sequenceNumber()).isEqualTo(5L);
            assertThat(c.similarity()).isEqualTo(0.92);
            assertThat(c.preview()).isEqualTo("let's ship friday");
        });
    }
}
