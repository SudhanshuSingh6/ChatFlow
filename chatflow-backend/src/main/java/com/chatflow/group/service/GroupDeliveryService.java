package com.chatflow.group.service;

import com.chatflow.group.dto.GroupDeliveryAckRequest;
import com.chatflow.group.dto.GroupReadReceiptRequest;
import com.chatflow.group.dto.GroupReadReceiptResponse;
import com.chatflow.group.repository.GroupMemberRepository;
import com.chatflow.group.repository.GroupMessageRepository;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupDeliveryService {

    private final GroupMemberRepository groupMemberRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final WebSocketGateway webSocketGateway;

    @Transactional
    public void markDelivered(UUID userId,
                              GroupDeliveryAckRequest request,
                              String requestId) {
        UUID groupId = request.getGroupId();
        long upTo = request.getUpToSequenceNumber();

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new SecurityException("User " + userId
                    + " is not a member of group " + groupId);
        }

        long maxSequenceNumber = groupMessageRepository.maxSequenceNumber(groupId);
        if (upTo > maxSequenceNumber) {
            throw new IllegalArgumentException(
                    "Cannot mark delivered beyond latest group message sequence "
                            + maxSequenceNumber);
        }

        int updated = groupMemberRepository.advanceDeliveryCursor(groupId, userId, upTo);

        afterCommit(() -> webSocketGateway.sendToUser(
                userId,
                OutboundMessage.responseTo(
                        OutboundMessage.Type.GROUP_DELIVERY_ACK,
                        requestId,
                        Map.of(
                                "groupId", groupId,
                                "lastDeliveredSequenceNumber", upTo,
                                "updated", updated > 0
                        )
                )
        ));

        log.debug("Advanced delivery cursor userId={} groupId={} upTo={} updated={}",
                userId, groupId, upTo, updated);
    }

    @Transactional
    public void markRead(UUID userId, GroupReadReceiptRequest request) {
        UUID groupId = request.getGroupId();
        long upTo = request.getUpToSequenceNumber();

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new SecurityException("User " + userId
                    + " is not a member of group " + groupId);
        }

        long maxSequenceNumber = groupMessageRepository.maxSequenceNumber(groupId);
        if (upTo > maxSequenceNumber) {
            throw new IllegalArgumentException(
                    "Cannot mark read beyond latest group message sequence "
                            + maxSequenceNumber);
        }

        int updated = groupMemberRepository.advanceReadCursor(groupId, userId, upTo);
        groupMemberRepository.advanceDeliveryCursor(groupId, userId, upTo);

        if (updated == 0) {
            log.debug("Read cursor for userId={} in groupId={} already at or past seq={}",
                    userId, groupId, upTo);
            return;
        }

        GroupReadReceiptResponse receipt = GroupReadReceiptResponse.builder()
                .groupId(groupId)
                .userId(userId)
                .lastReadSequenceNumber(upTo)
                .build();

        List<UUID> otherMemberIds = groupMemberRepository
                .findUserIdsByGroupId(groupId)
                .stream()
                .filter(id -> !id.equals(userId))
                .toList();

        afterCommit(() -> webSocketGateway.sendToUsers(
                otherMemberIds,
                OutboundMessage.of(OutboundMessage.Type.GROUP_READ_RECEIPT, receipt)
        ));

        log.debug("Advanced read cursor userId={} groupId={} upTo={}",
                userId, groupId, upTo);
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            action.run();
                        }
                    }
            );
        } else {
            action.run();
        }
    }
}