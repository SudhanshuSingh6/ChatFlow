package com.chatflow.realtime.relay;

import com.chatflow.realtime.metrics.RealtimeMetrics;
import com.chatflow.realtime.ws.RealtimeSessionRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

/**
 * Consumes the {@code chat:relay} pub/sub channel core publishes outbound frames to. Each message
 * is {@code {sourceInstanceId, targetUserId, payload}}; we deliver {@code payload} (already a full
 * frame) verbatim to the target user's open sockets — no frame deserialization needed.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RelaySubscriber implements MessageListener {

    private final RealtimeSessionRegistry sessionRegistry;
    private final RealtimeMetrics metrics;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(Message message, byte[] pattern) {
        metrics.relayMessage();
        try {
            JsonNode envelope = objectMapper.readTree(message.getBody());
            String target = envelope.path("targetUserId").asString(null);
            JsonNode payload = envelope.get("payload");
            if (target == null || payload == null) {
                return;
            }
            sessionRegistry.sendRaw(UUID.fromString(target), objectMapper.writeValueAsString(payload));
        } catch (Exception ex) {
            log.warn("Failed to process relay message: {}", ex.getMessage());
        }
    }
}
