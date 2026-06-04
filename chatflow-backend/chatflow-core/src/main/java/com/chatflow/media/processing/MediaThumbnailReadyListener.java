package com.chatflow.media.processing;

import com.chatflow.contracts.events.MediaThumbnailReady;
import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.infra.tx.AfterCommit;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.media.entity.MediaMessage;
import com.chatflow.media.entity.MediaStatus;
import com.chatflow.media.repository.MediaMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Completes the media thumbnail round-trip: chatflow-media publishes {@link MediaThumbnailReady}
 * once it has generated + stored the thumbnail; core persists the URL on the media row and pushes
 * {@code MEDIA_THUMBNAIL_READY} to the conversation participants (core owns both the media
 * metadata and the WebSocket layer). Replaces the old in-process {@code ThumbnailEventListener}.
 *
 * <p>Only active when the outbox uses Kafka (distributed mode); otherwise there is no worker to
 * produce these events and no broker to connect to.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "kafka")
public class MediaThumbnailReadyListener {

    private final ObjectMapper objectMapper;
    private final MediaMessageRepository mediaMessageRepository;
    private final MessageRepository messageRepository;
    private final ConversationParticipantRepository participantRepository;
    private final WebSocketGateway webSocketGateway;

    @KafkaListener(
            topics = "${app.media.thumbnail-ready-topic:chatflow.media.thumbnail-ready}",
            groupId = "${app.media.thumbnail-ready-consumer-group:chatflow-core-media}")
    @Transactional
    public void onThumbnailReady(String json) {
        MediaThumbnailReady event = objectMapper.readValue(json, MediaThumbnailReady.class);

        MediaMessage media = mediaMessageRepository.findByIdAndDeletedFalse(event.mediaMessageId()).orElse(null);
        if (media == null) {
            log.warn("Thumbnail ready for unknown media message {}", event.mediaMessageId());
            return;
        }
        media.setThumbnailUrl(event.thumbnailUrl());
        media.setStatus(MediaStatus.READY);
        mediaMessageRepository.save(media);

        Message parent = messageRepository.findById(media.getMessageId()).orElse(null);
        if (parent == null) {
            return;
        }
        List<UUID> recipients = participantRepository.findUserIdsByConversationId(parent.getConversationId());
        OutboundMessage frame = OutboundMessage.of(OutboundMessage.Type.MEDIA_THUMBNAIL_READY,
                Map.of("mediaMessageId", media.getId(), "thumbnailUrl", event.thumbnailUrl()));
        AfterCommit.run(() -> webSocketGateway.sendToUsers(recipients, frame));
    }
}
