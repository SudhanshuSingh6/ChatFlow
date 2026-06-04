package com.chatflow.media.processing;

import com.chatflow.contracts.events.MediaProcessingRequested;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class MediaProcessingConsumerTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final ThumbnailService thumbnailService = mock(ThumbnailService.class);
    @SuppressWarnings("unchecked")
    private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
    private MediaProcessingConsumer consumer;

    @BeforeEach
    void setUp() {
        consumer = new MediaProcessingConsumer(mapper, thumbnailService, kafkaTemplate);
        ReflectionTestUtils.setField(consumer, "thumbnailReadyTopic", "chatflow.media.thumbnail-ready");
    }

    private String envelope(String eventType, String payloadJson) {
        return mapper.writeValueAsString(Map.of(
                "id", UUID.randomUUID().toString(),
                "aggregateType", "media",
                "aggregateId", UUID.randomUUID().toString(),
                "eventType", eventType,
                "payload", payloadJson));
    }

    @Test
    void generatesThumbnailAndPublishesReadyEvent() {
        UUID mediaId = UUID.randomUUID();
        var req = new MediaProcessingRequested(mediaId, "image/2026/05/a.jpg", "IMAGE", "image/jpeg");
        when(thumbnailService.generate("image/2026/05/a.jpg", "IMAGE", "image/jpeg"))
                .thenReturn(Optional.of("http://store/thumbnails/image/2026/05/a_thumb.jpg"));

        consumer.onOutboxEvent(envelope(MediaProcessingRequested.TYPE, mapper.writeValueAsString(req)));

        verify(thumbnailService).generate("image/2026/05/a.jpg", "IMAGE", "image/jpeg");
        verify(kafkaTemplate).send(eq("chatflow.media.thumbnail-ready"), eq(mediaId.toString()), contains("a_thumb.jpg"));
    }

    @Test
    void doesNotPublishWhenNoThumbnail() {
        var req = new MediaProcessingRequested(UUID.randomUUID(), "file/2026/05/a.pdf", "FILE", "application/pdf");
        when(thumbnailService.generate(any(), any(), any())).thenReturn(Optional.empty());

        consumer.onOutboxEvent(envelope(MediaProcessingRequested.TYPE, mapper.writeValueAsString(req)));

        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void ignoresOtherEventTypes() {
        consumer.onOutboxEvent(envelope("message.created", "{}"));
        verifyNoInteractions(thumbnailService, kafkaTemplate);
    }
}
