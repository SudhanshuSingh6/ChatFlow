package com.chatflow.infra.websocket;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class InboundMessage {

    /**
     * Unified client→server message types. A group is just a conversation, so the
     * old split between 1:1 and GROUP_* frames is collapsed into one set keyed by
     * {@code conversationId} in the payload.
     */
    public enum Type {
        SEND_MESSAGE,
        MESSAGE_DELIVERED,
        CONVERSATION_OPEN,
        MARK_READ,
        TYPING,
        PING
    }

    @NotNull
    private Type type;
    private String requestId;
    private JsonNode payload;
}
