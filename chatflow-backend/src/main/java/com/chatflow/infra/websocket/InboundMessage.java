package com.chatflow.infra.websocket;

import jakarta.validation.constraints.NotNull;
import lombok.Data;
import tools.jackson.databind.JsonNode;

@Data
public class InboundMessage {

    public enum Type {
        SEND_MESSAGE,
        MESSAGE_ACK,
        CONVERSATION_OPEN,
        CONVERSATION_SEEN,
        TYPING,
        PING
    }

    @NotNull
    private Type type;

    private String requestId;

    private JsonNode payload;
}