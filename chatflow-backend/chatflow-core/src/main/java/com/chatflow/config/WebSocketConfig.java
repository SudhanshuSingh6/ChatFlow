package com.chatflow.config;

import com.chatflow.auth.security.JwtHandshakeInterceptor;
import com.chatflow.realtime.ChatWebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * Core serves {@code /ws} only in embedded realtime mode. When {@code app.realtime.mode=external}
 * the chatflow-realtime service terminates WebSockets and this config (+ {@link ChatWebSocketHandler})
 * are not created.
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.realtime.mode", havingValue = "embedded", matchIfMissing = true)
public class WebSocketConfig implements WebSocketConfigurer {

    private final ChatWebSocketHandler chatWebSocketHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(chatWebSocketHandler, "/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");
    }
}