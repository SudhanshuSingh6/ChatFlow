package com.chatflow.conversation.search;

import com.chatflow.ai.embedding.EmbeddingResult;
import com.chatflow.ai.embedding.EmbeddingService;
import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.entity.MessageType;
import com.chatflow.conversation.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Drains {@code MESSAGE_EMBEDDING_REQUESTED} events: embeds a message's text and stores
 * the vector. Invoked by {@link com.chatflow.infra.outbox.OutboxDispatcher} inside the
 * per-event transaction, so a failure leaves the event PENDING for retry.
 *
 * <p>Re-reads the message by id rather than trusting the event payload, so it always
 * embeds the current text and naturally skips messages deleted before it ran.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MessageEmbeddingWorker {

    private final MessageRepository messageRepository;
    private final EmbeddingService embeddingService;
    private final MessageEmbeddingRepository embeddingRepository;

    public void embed(UUID messageId) {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null || message.isDeleted() || message.getType() != MessageType.TEXT) {
            return; // gone, deleted, or nothing embeddable (media/system)
        }
        String content = message.getContent();
        if (content == null || content.isBlank()) {
            return;
        }

        EmbeddingResult result = embeddingService.embed(content);
        embeddingRepository.upsert(messageId, result.vector(), result.model(),
                result.dimensions(), Instant.now());
        log.debug("Embedded message {} ({} dims, model {})",
                messageId, result.dimensions(), result.model());
    }
}
