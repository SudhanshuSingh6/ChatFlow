package com.chatflow.infra.websocket;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class OutboundMessage {

    public enum Type {
        // Chat (unified DIRECT + GROUP)
        MESSAGE,
        MESSAGE_ACK,
        STATUS_UPDATE,
        SEEN_UPDATE,
        // Shared
        PRESENCE,
        TYPING,
        ERROR,
        PONG,
        // Media
        MEDIA_MESSAGE,
        MEDIA_THUMBNAIL_READY,
        // Group lifecycle
        GROUP_CREATED,
        GROUP_MEMBER_ADDED,
        GROUP_MEMBER_REMOVED,
        GROUP_ROLE_CHANGED,
        GROUP_OWNERSHIP_TRANSFERRED,
        GROUP_DELETED,
        // Friend events
        FRIEND_REQUEST,
        FRIEND_REQUEST_ACCEPTED,
        FRIEND_REQUEST_DECLINED,
        FRIEND_REMOVED,
        // Notifications
        NOTIFICATION,
        NOTIFICATION_READ
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