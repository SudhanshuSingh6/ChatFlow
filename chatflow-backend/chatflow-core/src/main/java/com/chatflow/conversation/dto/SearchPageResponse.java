package com.chatflow.conversation.dto;

import java.util.List;

public record SearchPageResponse(
        List<MessageSearchResult> results,
        String nextCursor
) {
}
