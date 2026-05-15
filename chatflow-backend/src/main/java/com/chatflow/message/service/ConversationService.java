package com.chatflow.message.service;

import com.chatflow.message.dto.ConversationResponse;
import com.chatflow.message.dto.CreateConversationRequest;
import com.chatflow.message.dto.MessagePageResponse;
import com.chatflow.message.dto.MessageResponse;
import com.chatflow.message.entity.Conversation;
import com.chatflow.message.entity.Message;
import com.chatflow.message.mapper.MessageMapper;
import com.chatflow.message.repository.ConversationRepository;
import com.chatflow.message.repository.MessageRepository;
import com.chatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    @Transactional
    public ConversationResponse getOrCreate(UUID callerId, CreateConversationRequest request) {
        UUID otherId = request.getOtherUserId();

        if (callerId.equals(otherId)) {
            throw new IllegalArgumentException("Cannot create a conversation with yourself");
        }
        if (!userRepository.existsById(otherId)) {
            throw new IllegalArgumentException("User not found: " + otherId);
        }

        UUID p1 = callerId.compareTo(otherId) < 0 ? callerId : otherId;
        UUID p2 = callerId.compareTo(otherId) < 0 ? otherId : callerId;

        Conversation conversation = conversationRepository
                .findByParticipantOneIdAndParticipantTwoId(p1, p2)
                .orElseGet(() -> {
                    Conversation created = Conversation.create(callerId, otherId);
                    log.debug("Creating new conversation between {} and {}", callerId, otherId);
                    return conversationRepository.save(created);
                });

        return MessageMapper.toConversationResponse(conversation, callerId);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> listForCaller(UUID callerId) {
        return conversationRepository
                .findByParticipantOneIdOrParticipantTwoIdOrderByLastMessageAtDesc(callerId, callerId)
                .stream()
                .map(c -> MessageMapper.toConversationResponse(c, callerId))
                .toList();
    }

    /**
     * Returns a cursor-paginated page of messages.
     *
     * @param callerId must be a participant — 403 if not
     * @param convId   the conversation to read
     * @param before   exclusive upper bound on sequenceNumber;
     *                 pass Long.MAX_VALUE to get the latest page
     * @param limit    page size, capped at MAX_PAGE_SIZE
     */
    @Transactional(readOnly = true)
    public MessagePageResponse getMessages(UUID callerId, UUID convId, long before, int limit) {
        Conversation conversation = conversationRepository.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + convId));

        if (!isParticipant(conversation, callerId)) {
            throw new SecurityException("User " + callerId + " is not a participant in conversation " + convId);
        }

        int pageSize = Math.min(limit, MAX_PAGE_SIZE);
        List<Message> messages = messageRepository.findPageBefore(
                convId, before, PageRequest.of(0, pageSize));

        List<MessageResponse> responses = messages.stream()
                .map(MessageMapper::toMessageResponse)
                .toList();

        // nextCursor is the lowest sequenceNumber in this page — what the client
        // passes as ?before= on the next request. Null means no more pages.
        Long nextCursor = messages.size() == pageSize
                ? messages.get(messages.size() - 1).getSequenceNumber()
                : null;

        return MessagePageResponse.builder()
                .messages(responses)
                .nextCursor(nextCursor)
                .build();
    }

    /**
     * Gap-fill: returns messages with sequenceNumber > after, oldest first.
     *
     * The client calls this when it detects a gap:
     *   received message with seq N but lastSeen was N-2
     *   -> call ?after={lastSeen} to fetch the missing messages.
     *
     * @param callerId must be a participant — 403 if not
     * @param convId   the conversation to read
     * @param after    exclusive lower bound on sequenceNumber
     * @param limit    page size, capped at MAX_PAGE_SIZE
     */
    @Transactional(readOnly = true)
    public MessagePageResponse getMessagesAfter(UUID callerId, UUID convId, long after, int limit) {
        Conversation conversation = conversationRepository.findById(convId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + convId));

        if (!isParticipant(conversation, callerId)) {
            throw new SecurityException("User " + callerId + " is not a participant in conversation " + convId);
        }

        int pageSize = Math.min(limit, MAX_PAGE_SIZE);
        List<Message> messages = messageRepository.findPageAfter(
                convId, after, PageRequest.of(0, pageSize));

        List<MessageResponse> responses = messages.stream()
                .map(MessageMapper::toMessageResponse)
                .toList();

        Long nextCursor = messages.size() == pageSize
                ? messages.get(messages.size() - 1).getSequenceNumber()
                : null;

        return MessagePageResponse.builder()
                .messages(responses)
                .nextCursor(nextCursor)
                .build();
    }

    private boolean isParticipant(Conversation conversation, UUID userId) {
        return userId.equals(conversation.getParticipantOneId())
                || userId.equals(conversation.getParticipantTwoId());
    }
}