package com.chatflow.infra.websocket;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class OutboundMessage {

    public enum Type {
        MESSAGE,
        MESSAGE_ACK,
        STATUS_UPDATE,
        SEEN_UPDATE,
        PRESENCE,
        TYPING,
        ERROR,
        PONG
    }

    private Type type;
    private String requestId;
    private Object payload;

    public static OutboundMessage of(Type type, Object payload) {
        return OutboundMessage.builder()
                .type(type)
                .payload(payload)
                .build();
    }

    public static OutboundMessage responseTo(Type type, String requestId, Object payload) {
        return OutboundMessage.builder()
                .type(type)
                .requestId(requestId)
                .payload(payload)
                .build();
    }

    public static OutboundMessage error(String requestId, String code, String message) {
        return OutboundMessage.builder()
                .type(Type.ERROR)
                .requestId(requestId)
                .payload(WebSocketErrorPayload.of(code, message))
                .build();
    }

    public static OutboundMessage error(String requestId, String code, String message, List<String> details) {
        return OutboundMessage.builder()
                .type(Type.ERROR)
                .requestId(requestId)
                .payload(WebSocketErrorPayload.of(code, message, details))
                .build();
    }
}