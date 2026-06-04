package com.chatflow.conversation.service;

import com.chatflow.contracts.dto.ConversationTranscript;
import com.chatflow.conversation.entity.ConversationParticipant;
import com.chatflow.conversation.entity.Message;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Builds a caller's unread transcript for ai-service to summarize. core owns this data
 * (messages, the per-participant read watermark, and sender names), so it exposes it via the
 * internal endpoint rather than ai reading core's tables. The summarization (prompt + LLM)
 * lives in ai; this is purely the read.
 */
@Service
@RequiredArgsConstructor
public class ConversationTranscriptService {

    /** Cap the backlog so a huge unread count can't blow up the prompt downstream. */
    private static final int MAX_MESSAGES = 500;

    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ConversationTranscript unreadFor(UUID conversationId, UUID userId) {
        ConversationParticipant me = participantRepository
                .findByConversationIdAndUserId(conversationId, userId)
                .orElse(null);
        if (me == null) {
            return new ConversationTranscript(0, 0, 0, List.of()); // not a participant
        }

        long afterSeq = me.getLastReadSeq();
        List<Message> messages = messageRepository.findPageAfter(
                conversationId, afterSeq, PageRequest.of(0, MAX_MESSAGES));
        if (messages.isEmpty()) {
            return new ConversationTranscript(0, afterSeq, afterSeq, List.of());
        }

        Map<UUID, String> names = resolveSenderNames(messages);
        List<ConversationTranscript.Entry> entries = messages.stream()
                .map(m -> new ConversationTranscript.Entry(
                        m.getSenderId() == null ? "System" : names.getOrDefault(m.getSenderId(), "Unknown"),
                        m.getContent() == null ? "[non-text message]" : m.getContent()))
                .toList();

        return new ConversationTranscript(messages.size(),
                messages.get(0).getSequenceNumber(),
                messages.get(messages.size() - 1).getSequenceNumber(),
                entries);
    }

    private Map<UUID, String> resolveSenderNames(List<Message> messages) {
        Set<UUID> senderIds = messages.stream()
                .map(Message::getSenderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return userRepository.findAllById(senderIds).stream()
                .collect(Collectors.toMap(u -> u.getId(), u -> u.getUsername()));
    }
}
