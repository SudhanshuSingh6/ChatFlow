package com.chatflow.media.processing;

import com.chatflow.contracts.events.MediaProcessingRequested;
import com.chatflow.contracts.events.MediaThumbnailReady;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Consumes core's shared outbox topic, filters to {@link MediaProcessingRequested#TYPE}, and
 * generates the thumbnail off the chat hot path. On success it publishes
 * {@link MediaThumbnailReady} to its own topic, which core consumes to persist the URL and push
 * to participants. Own consumer group so it receives events independently of core.
 *
 * <p>Delivery is at-least-once; thumbnailing is idempotent (same key overwritten), so redelivery
 * just regenerates.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MediaProcessingConsumer {

    private final ObjectMapper objectMapper;
    private final ThumbnailService thumbnailService;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Value("${app.media.thumbnail-ready-topic:chatflow.media.thumbnail-ready}")
    private String thumbnailReadyTopic;

    @KafkaListener(
            topics = "${app.outbox.topic:chatflow.outbox.events}",
            groupId = "${app.media.consumer-group:chatflow-media}")
    public void onOutboxEvent(String json) {
        OutboxEnvelope envelope = objectMapper.readValue(json, OutboxEnvelope.class);
        if (!MediaProcessingRequested.TYPE.equals(envelope.eventType())) {
            return; // not ours
        }
        MediaProcessingRequested request =
                objectMapper.readValue(envelope.payload(), MediaProcessingRequested.class);

        Optional<String> thumbnailUrl = thumbnailService.generate(
                request.storageKey(), request.messageType(), request.mimeType());
        if (thumbnailUrl.isEmpty()) {
            log.debug("No thumbnail for media {} (type {})", request.mediaMessageId(), request.messageType());
            return;
        }

        MediaThumbnailReady ready = new MediaThumbnailReady(request.mediaMessageId(), thumbnailUrl.get());
        kafkaTemplate.send(thumbnailReadyTopic, request.mediaMessageId().toString(),
                objectMapper.writeValueAsString(ready));
        log.debug("Thumbnail ready for media {} → {}", request.mediaMessageId(), thumbnailUrl.get());
    }
}
