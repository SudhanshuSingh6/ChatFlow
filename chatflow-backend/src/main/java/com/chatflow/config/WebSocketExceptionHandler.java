package com.chatflow.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.ControllerAdvice;

import java.util.Map;

@Slf4j
@ControllerAdvice
public class WebSocketExceptionHandler {

    @MessageExceptionHandler({IllegalArgumentException.class, SecurityException.class})
    @SendToUser("/queue/errors")
    public Map<String, String> handleClientError(Exception ex) {
        log.warn("WebSocket client error: {}", ex.getMessage());
        return Map.of(
                "type", "ERROR",
                "message", ex.getMessage()
        );
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser("/queue/errors")
    public Map<String, String> handleServerError(Exception ex) {
        log.error("WebSocket server error", ex);
        return Map.of(
                "type", "ERROR",
                "message", "Internal server error"
        );
    }
}