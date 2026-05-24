package com.chatflow.infra.websocket;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InboundMessage {

    public enum Type {
        // 1:1
        SEND_MESSAGE,
        MESSAGE_ACK,
        CONVERSATION_OPEN,
        CONVERSATION_SEEN,
        // Group
        GROUP_SEND_MESSAGE,
        GROUP_READ_RECEIPT,
        GROUP_MESSAGE_DELIVERED,

        // Shared
        TYPING,
        PING
    }

    @NotNull
    private Type type;
    private String requestId;
    private JsonNode payload;
}