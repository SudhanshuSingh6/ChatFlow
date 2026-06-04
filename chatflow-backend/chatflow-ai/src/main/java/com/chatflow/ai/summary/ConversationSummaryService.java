package com.chatflow.ai.summary;

import com.chatflow.ai.chat.ChatCompletionService;
import com.chatflow.ai.rag.ConversationAccessClient;
import com.chatflow.contracts.dto.ConversationTranscript;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * "Catch me up" — summarizes the messages a caller hasn't read yet via the provider-agnostic
 * {@link ChatCompletionService}. The unread backlog is fetched synchronously from core (it
 * owns messages + the read watermark); the prompt + LLM call live here. Membership is checked
 * up front, so a non-participant gets 403 rather than a misleading "all caught up".
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    private static final String SYSTEM = """
            You summarize direct and group chat conversations for a user catching up on what
            they missed. Be concise — a few sentences or short bullet points. Attribute key
            points to the people who made them. Do not invent details that are not in the
            transcript.""";

    private final ConversationAccessClient accessClient;
    private final TranscriptClient transcriptClient;
    private final ChatCompletionService chatCompletionService;

    public SummaryResponse summarizeUnread(UUID callerId, UUID conversationId) {
        if (!accessClient.isParticipant(conversationId, callerId)) {
            throw new SecurityException("You are not a participant in this conversation");
        }

        ConversationTranscript transcript = transcriptClient.fetchUnread(conversationId, callerId);
        if (transcript == null || transcript.messageCount() == 0) {
            long seq = transcript == null ? 0 : transcript.toSequence();
            return new SummaryResponse("You're all caught up — nothing new to summarize.", 0, seq, seq);
        }

        String summary = chatCompletionService.complete(
                SYSTEM, buildTranscript(transcript), "Summarize what I missed in this conversation since I last read it.");

        return new SummaryResponse(summary, transcript.messageCount(),
                transcript.fromSequence(), transcript.toSequence());
    }

    private String buildTranscript(ConversationTranscript transcript) {
        StringBuilder sb = new StringBuilder(transcript.entries().size() * 48);
        for (ConversationTranscript.Entry e : transcript.entries()) {
            sb.append(e.senderName()).append(": ").append(e.content()).append('\n');
        }
        return sb.toString();
    }
}
