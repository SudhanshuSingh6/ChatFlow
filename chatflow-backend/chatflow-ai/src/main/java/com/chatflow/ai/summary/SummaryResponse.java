package com.chatflow.ai.summary;

/**
 * A "catch me up" summary plus the range of unread messages it covered (so the client can
 * advance/annotate the read position).
 */
public record SummaryResponse(
        String summary,
        int messageCount,
        long fromSequence,
        long toSequence
) {
}
