package com.chatflow.contracts.dto;

import java.util.List;

/**
 * A caller's unread conversation transcript, served by core to ai-service for summarization.
 *
 * <p>Summary needs the full chronological backlog past the caller's read watermark — not just
 * embedded messages — so unlike RAG it cannot be served from ai's embedding store. core owns
 * this data (messages + per-participant watermark + names), so ai fetches it synchronously
 * (docs/microservices-migration.md §2). Sequence bounds let ai report the range it covered.
 *
 * @param messageCount  number of unread messages (0 = caught up, or caller not a participant)
 * @param fromSequence  sequence of the first unread message (== toSequence when empty)
 * @param toSequence    sequence of the last unread message
 * @param entries       the messages, oldest first
 */
public record ConversationTranscript(
        int messageCount,
        long fromSequence,
        long toSequence,
        List<Entry> entries) {

    /** One transcript line: who said what (sender already resolved to a display name). */
    public record Entry(String senderName, String content) {
    }
}
