package com.chatflow.infra.websocket;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class OutboundMessage {

    public enum Type {
        // 1:1
        MESSAGE,
        MESSAGE_ACK,
        STATUS_UPDATE,
        SEEN_UPDATE,
        // Group
        GROUP_MESSAGE,
        GROUP_MESSAGE_ACK,
        GROUP_READ_RECEIPT,
        GROUP_DELIVERY_ACK,
        // Shared
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

    public static OutboundMessage error(String requestId, String message) {
        return OutboundMessage.builder()
                .type(Type.ERROR)
                .requestId(requestId)
                .payload(Map.of("message", message))
                .build();
    }
}