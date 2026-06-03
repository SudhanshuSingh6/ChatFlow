package com.chatflow.conversation.search;

import com.chatflow.ai.embedding.EmbeddingResult;
import com.chatflow.ai.embedding.EmbeddingService;
import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.entity.MessageType;
import com.chatflow.conversation.repository.MessageRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class MessageEmbeddingWorkerTest {

    private final MessageRepository messageRepository = mock(MessageRepository.class);
    private final EmbeddingService embeddingService = mock(EmbeddingService.class);
    private final MessageEmbeddingRepository embeddingRepository = mock(MessageEmbeddingRepository.class);

    private final MessageEmbeddingWorker worker =
            new MessageEmbeddingWorker(messageRepository, embeddingService, embeddingRepository);

    @Test
    void embedsAndStoresTextMessage() {
        UUID id = UUID.randomUUID();
        Message m = Message.builder().id(id).type(MessageType.TEXT).content("hello there").build();
        when(messageRepository.findById(id)).thenReturn(Optional.of(m));
        when(embeddingService.embed("hello there"))
                .thenReturn(new EmbeddingResult(new float[]{0.1f, 0.2f, 0.3f}, "model-x", 3));

        worker.embed(id);

        verify(embeddingRepository).upsert(eq(id), any(float[].class), eq("model-x"), eq(3), any(Instant.class));
    }

    @Test
    void skipsDeletedMessage() {
        UUID id = UUID.randomUUID();
        Message m = Message.builder().id(id).type(MessageType.TEXT).content("x").build();
        m.softDelete(); // sets deletedAt, nulls content
        when(messageRepository.findById(id)).thenReturn(Optional.of(m));

        worker.embed(id);

        verifyNoInteractions(embeddingService);
        verify(embeddingRepository, never()).upsert(any(), any(), anyString(), anyInt(), any());
    }

    @Test
    void skipsMissingMessage() {
        UUID id = UUID.randomUUID();
        when(messageRepository.findById(id)).thenReturn(Optional.empty());

        worker.embed(id);

        verifyNoInteractions(embeddingService);
    }
}
