package com.chatflow.media.processing;

import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import com.chatflow.media.repository.MediaMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class ThumbnailEventListener {

    private final MediaMessageRepository mediaMessageRepository;
    private final WebSocketGateway webSocketGateway;
    private final MessageRepository messageRepository;
    private final ConversationParticipantRepository participantRepository;

    @Async("mediaProcessingExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onThumbnailGenerated(ThumbnailGeneratedEvent event) {
        MediaMessage media = mediaMessageRepository.findByIdAndDeletedFalse(event.getMediaMessageId())
                .orElse(null);
        if (media == null) {
            log.warn("Thumbnail generated for unknown media message {}", event.getMediaMessageId());
            return;
        }

        media.setThumbnailUrl(event.getThumbnailUrl());
        media.setStatus(MediaStatus.READY);
        mediaMessageRepository.save(media);

        OutboundMessage frame = OutboundMessage.of(
                OutboundMessage.Type.MEDIA_THUMBNAIL_READY,
                java.util.Map.of(
                        "mediaMessageId", media.getId(),
                        "thumbnailUrl", event.getThumbnailUrl()));

        deliverToParticipants(media, frame);
    }

    private void deliverToParticipants(MediaMessage media, OutboundMessage frame) {
        Message parent = messageRepository.findById(media.getMessageId()).orElse(null);
        if (parent == null) {
            return;
        }
        List<UUID> recipients =
                participantRepository.findUserIdsByConversationId(parent.getConversationId());
        webSocketGateway.sendToUsers(recipients, frame);
    }
}
