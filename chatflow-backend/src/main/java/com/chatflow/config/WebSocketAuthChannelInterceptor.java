package com.chatflow.config;

import com.chatflow.message.repository.ConversationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    private static final String TYPING_TOPIC_PREFIX = "/topic/typing.";
    private static final String PRESENCE_TOPIC_PREFIX = "/topic/presence.";

    private final ConversationRepository conversationRepository;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null) {
            return message;
        }

        if (accessor.getCommand() == StompCommand.SUBSCRIBE) {
            validateSubscription(accessor);
            return message;
        }

        if (accessor.getCommand() != StompCommand.CONNECT) {
            return message;
        }

        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            log.warn("STOMP CONNECT has no session attributes — handshake interceptor may not have run");
            throw new IllegalStateException("No WebSocket session attributes found");
        }

        UUID userId = (UUID) sessionAttributes.get(JwtHandshakeInterceptor.USER_ID_ATTR);
        if (userId == null) {
            log.warn("STOMP CONNECT missing userId in session attributes");
            throw new IllegalStateException("Unauthenticated WebSocket connection");
        }

        UsernamePasswordAuthenticationToken principal = new UsernamePasswordAuthenticationToken(
                userId.toString(),
                null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        accessor.setUser(principal);

        log.debug("STOMP CONNECT authenticated userId={}", userId);
        return message;
    }

    private void validateSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !isConversationTopic(destination)) {
            return;
        }

        UUID userId = extractPrincipalUserId(accessor);
        UUID conversationId = extractConversationId(destination);

        if (!conversationRepository.existsParticipant(conversationId, userId)) {
            log.warn("Rejecting subscription userId={} destination={}", userId, destination);
            throw new SecurityException("User " + userId
                    + " is not a participant in conversation " + conversationId);
        }
    }

    private boolean isConversationTopic(String destination) {
        return destination.startsWith(TYPING_TOPIC_PREFIX)
                || destination.startsWith(PRESENCE_TOPIC_PREFIX);
    }

    private UUID extractConversationId(String destination) {
        String rawId = destination.startsWith(TYPING_TOPIC_PREFIX)
                ? destination.substring(TYPING_TOPIC_PREFIX.length())
                : destination.substring(PRESENCE_TOPIC_PREFIX.length());
        try {
            return UUID.fromString(rawId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid conversation topic: " + destination);
        }
    }

    private UUID extractPrincipalUserId(StompHeaderAccessor accessor) {
        if (accessor.getUser() == null) {
            throw new SecurityException("Unauthenticated WebSocket subscription");
        }
        try {
            return UUID.fromString(accessor.getUser().getName());
        } catch (IllegalArgumentException ex) {
            throw new SecurityException("Invalid WebSocket principal");
        }
    }
}
