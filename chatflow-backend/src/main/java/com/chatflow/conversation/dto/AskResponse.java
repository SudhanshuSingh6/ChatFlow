package com.chatflow.conversation.dto;

import java.util.List;
import java.util.UUID;

/**
 * A grounded RAG answer plus the messages it was built from. Citations are returned from
 * day one so the answer is verifiable (the client can deep-link each cited message), not a
 * black-box "ChatGPT over my messages."
 */
public record AskResponse(
        String answer,
        List<Citation> citations
) {
    /** One retrieved message backing the answer. */
    public record Citation(
            UUID messageId,
            long sequenceNumber,
            double similarity,
            String preview
    ) {
    }
}
