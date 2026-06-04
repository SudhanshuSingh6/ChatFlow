package com.chatflow.infra.websocket;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class WebSocketErrorPayload {

    private String code;
    private String message;
    private List<String> details;

    public static WebSocketErrorPayload of(String code, String message) {
        return WebSocketErrorPayload.builder()
                .code(code)
                .message(message)
                .details(List.of())
                .build();
    }

    public static WebSocketErrorPayload of(String code, String message, List<String> details) {
        return WebSocketErrorPayload.builder()
                .code(code)
                .message(message)
                .details(details == null ? List.of() : details)
                .build();
    }
}