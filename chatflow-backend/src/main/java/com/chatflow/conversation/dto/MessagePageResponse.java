package com.chatflow.conversation.dto;

import java.util.List;

/** A page of messages plus the cursor for the next page (null = no more). */
public record MessagePageResponse(
        List<MessageResponse> messages,
        Long nextCursor
) {
}
