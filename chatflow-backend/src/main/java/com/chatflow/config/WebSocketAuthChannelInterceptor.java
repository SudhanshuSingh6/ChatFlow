package com.chatflow.config;

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
public class WebSocketAuthChannelInterceptor implements ChannelInterceptor {

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor =
                MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor == null || accessor.getCommand() != StompCommand.CONNECT) {
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
}