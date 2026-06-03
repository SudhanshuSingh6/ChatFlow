package com.chatflow.conversation.service;

import com.chatflow.ai.chat.ChatCompletionService;
import com.chatflow.conversation.dto.SummaryResponse;
import com.chatflow.conversation.entity.ConversationParticipant;
import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * "Catch me up" — summarizes the messages a caller hasn't read yet (everything past their
 * {@code lastReadSeq} watermark) via the provider-agnostic {@link ChatCompletionService}.
 * The transcript is passed as the cacheable context, so follow-up asks over the same range
 * reuse the cached prefix.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationSummaryService {

    /** Cap the transcript so a very long backlog can't blow up the prompt. */
    private static final int MAX_MESSAGES = 500;

    private static final String SYSTEM = """
            You summarize direct and group chat conversations for a user catching up on what
            they missed. Be concise — a few sentences or short bullet points. Attribute key
            points to the people who made them. Do not invent details that are not in the
            transcript.""";

    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChatCompletionService chatCompletionService;

    @Transactional(readOnly = true)
    public SummaryResponse summarizeUnread(UUID callerId, UUID conversationId) {
        ConversationParticipant me = participantRepository
                .findByConversationIdAndUserId(conversationId, callerId)
                .orElseThrow(() -> new SecurityException("You are not a participant in this conversation"));

        long afterSeq = me.getLastReadSeq();
        List<Message> messages = messageRepository.findPageAfter(
                conversationId, afterSeq, PageRequest.of(0, MAX_MESSAGES));
        if (messages.isEmpty()) {
            return new SummaryResponse("You're all caught up — nothing new to summarize.",
                    0, afterSeq, afterSeq);
        }

        String transcript = buildTranscript(messages);
        String summary = chatCompletionService.complete(
                SYSTEM, transcript,
                "Summarize what I missed in this conversation since I last read it.");

        return new SummaryResponse(summary, messages.size(),
                messages.get(0).getSequenceNumber(),
                messages.get(messages.size() - 1).getSequenceNumber());
    }

    private String buildTranscript(List<Message> messages) {
        Map<UUID, String> names = resolveSenderNames(messages);
        StringBuilder sb = new StringBuilder(messages.size() * 48);
        for (Message m : messages) {
            String who = m.getSenderId() == null
                    ? "System"
                    : names.getOrDefault(m.getSenderId(), "Unknown");
            String content = m.getContent() == null ? "[non-text message]" : m.getContent();
            sb.append(who).append(": ").append(content).append('\n');
        }
        return sb.toString();
    }

    private Map<UUID, String> resolveSenderNames(List<Message> messages) {
        Set<UUID> senderIds = messages.stream()
                .map(Message::getSenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> names = new HashMap<>();
        userRepository.findAllById(senderIds).forEach(u -> names.put(u.getId(), u.getUsername()));
        return names;
    }
}
