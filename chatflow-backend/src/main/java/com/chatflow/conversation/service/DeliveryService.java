package com.chatflow.conversation.service;

import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.infra.tx.AfterCommit;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Delivery and read receipts driven entirely by per-participant sequence
 * watermarks — the single mechanism behind both 1:1 ticks and group receipts.
 * Replaces the old DeliveryService + GroupDeliveryService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final WebSocketGateway webSocketGateway;

    /** Recipient confirms delivery up to {@code upToSeq}. */
    @Transactional
    public void markDelivered(UUID userId, UUID conversationId, long upToSeq, String requestId) {
        requireParticipant(conversationId, userId);
        long max = messageRepository.maxSequenceNumber(conversationId);
        if (upToSeq > max) {
            throw new IllegalArgumentException("Cannot mark delivered beyond latest sequence " + max);
        }

        int updated = participantRepository.advanceDeliveryCursor(conversationId, userId, upToSeq);
        List<UUID> others = othersExcept(conversationId, userId);

        AfterCommit.run(() -> {
            webSocketGateway.sendToUser(userId, OutboundMessage.responseTo(
                    OutboundMessage.Type.STATUS_UPDATE, requestId,
                    Map.of("conversationId", conversationId,
                            "lastDeliveredSeq", upToSeq, "updated", updated > 0)));
            if (updated > 0) {
                webSocketGateway.sendToUsers(others, OutboundMessage.of(
                        OutboundMessage.Type.STATUS_UPDATE,
                        Map.of("conversationId", conversationId,
                                "userId", userId, "lastDeliveredSeq", upToSeq)));
            }
        });
        log.debug("Delivery cursor user={} conversation={} upTo={} updated={}",
                userId, conversationId, upToSeq, updated);
    }

    /** Recipient opens the conversation — mark everything delivered. */
    @Transactional
    public void conversationOpen(UUID userId, UUID conversationId) {
        requireParticipant(conversationId, userId);
        long max = messageRepository.maxSequenceNumber(conversationId);
        if (max == 0) {
            return;
        }
        int updated = participantRepository.advanceDeliveryCursor(conversationId, userId, max);
        if (updated == 0) {
            return;
        }
        List<UUID> others = othersExcept(conversationId, userId);
        AfterCommit.run(() -> webSocketGateway.sendToUsers(others, OutboundMessage.of(
                OutboundMessage.Type.STATUS_UPDATE,
                Map.of("conversationId", conversationId, "userId", userId, "lastDeliveredSeq", max))));
        log.debug("Conversation opened user={} conversation={} delivered up to {}",
                userId, conversationId, max);
    }

    /** Recipient has read up to {@code upToSeq}. */
    @Transactional
    public void markRead(UUID userId, UUID conversationId, long upToSeq) {
        requireParticipant(conversationId, userId);
        long max = messageRepository.maxSequenceNumber(conversationId);
        if (upToSeq > max) {
            throw new IllegalArgumentException("Cannot mark read beyond latest sequence " + max);
        }

        int updated = participantRepository.advanceReadCursor(conversationId, userId, upToSeq);
        if (updated == 0) {
            log.debug("Read cursor user={} conversation={} already at/past {}",
                    userId, conversationId, upToSeq);
            return;
        }
        List<UUID> others = othersExcept(conversationId, userId);
        AfterCommit.run(() -> webSocketGateway.sendToUsers(others, OutboundMessage.of(
                OutboundMessage.Type.SEEN_UPDATE,
                Map.of("conversationId", conversationId, "userId", userId, "lastReadSeq", upToSeq))));
        log.debug("Read cursor user={} conversation={} upTo={}", userId, conversationId, upToSeq);
    }

    private void requireParticipant(UUID conversationId, UUID userId) {
        if (!participantRepository.existsByConversationIdAndUserId(conversationId, userId)) {
            throw new SecurityException(
                    "User " + userId + " is not a participant in " + conversationId);
        }
    }

    private List<UUID> othersExcept(UUID conversationId, UUID userId) {
        return participantRepository.findUserIdsByConversationId(conversationId).stream()
                .filter(id -> !id.equals(userId))
                .toList();
    }
}
