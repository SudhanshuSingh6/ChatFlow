package com.chatflow.infra.redis;

import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketSessionRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

@Slf4j
@Component
public class CrossServerRelay implements MessageListener {

    static final String CHANNEL = "chat:relay";

    private final StringRedisTemplate redisTemplate;
    private final WebSocketSessionRegistry sessionRegistry;
    private final ObjectMapper objectMapper;
    private final String instanceId;

    public CrossServerRelay(StringRedisTemplate redisTemplate,
                            WebSocketSessionRegistry sessionRegistry,
                            ObjectMapper objectMapper,
                            @Value("${app.instance-id:${random.uuid}}") String instanceId) {
        this.redisTemplate = redisTemplate;
        this.sessionRegistry = sessionRegistry;
        this.objectMapper = objectMapper;
        this.instanceId = instanceId;
    }

    public void publish(UUID targetUserId, OutboundMessage message) {
        try {
            CrossServerMessage envelope =
                    new CrossServerMessage(instanceId, targetUserId, message);

            redisTemplate.convertAndSend(
                    CHANNEL,
                    objectMapper.writeValueAsString(envelope)
            );
        } catch (Exception ex) {
            log.warn("Failed to publish relay message to userId={}: {}",
                    targetUserId, ex.getMessage());
        }
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            CrossServerMessage envelope =
                    objectMapper.readValue(message.getBody(), CrossServerMessage.class);

            if (instanceId.equals(envelope.getSourceInstanceId())) {
                return;
            }

            if (sessionRegistry.isConnected(envelope.getTargetUserId())) {
                sessionRegistry.sendToUser(
                        envelope.getTargetUserId(),
                        envelope.getPayload()
                );
            }
        } catch (Exception ex) {
            log.warn("Failed to process relay message: {}", ex.getMessage());
        }
    }
}