package com.chatflow.config;

import com.chatflow.auth.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    static final String USER_ID_ATTR = "userId";

    private final JwtService jwtService;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {

        String token = UriComponentsBuilder
                .fromUri(request.getURI())
                .build()
                .getQueryParams()
                .getFirst("token");

        if (token == null || !jwtService.isValid(token)) {
            log.warn("WebSocket handshake rejected — missing or invalid token from {}",
                    request.getRemoteAddress());
            return false;
        }

        UUID userId = jwtService.extractUserId(token);
        attributes.put(USER_ID_ATTR, userId);
        log.debug("WebSocket handshake accepted for userId={}", userId);
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}