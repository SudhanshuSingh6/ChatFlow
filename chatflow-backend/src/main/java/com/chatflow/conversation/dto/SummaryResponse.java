package com.chatflow.conversation.dto;

/**
 * AI summary of a range of a conversation. {@code fromSequence}/{@code toSequence} bound
 * the messages that were summarized; {@code messageCount} is how many were fed to the model.
 */
public record SummaryResponse(
        String summary,
        int messageCount,
        long fromSequence,
        long toSequence
) {
}
