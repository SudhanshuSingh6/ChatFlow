package com.chatflow.group.service;

import com.chatflow.group.dto.GroupMessageResponse;
import com.chatflow.group.dto.SendGroupMessageRequest;
import com.chatflow.group.entity.GroupMessage;
import com.chatflow.group.repository.GroupMemberRepository;
import com.chatflow.group.repository.GroupMessageRepository;
import com.chatflow.group.repository.GroupRepository;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupChatService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final WebSocketGateway webSocketGateway;

    @Transactional
    public GroupMessageResponse sendMessage(UUID senderId,
                                            SendGroupMessageRequest request,
                                            String requestId) {
        UUID groupId = request.getGroupId();

        groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, senderId)) {
            throw new SecurityException("User " + senderId
                    + " is not a member of group " + groupId);
        }

        Optional<GroupMessage> existing = groupMessageRepository
                .findByGroupIdAndClientMessageId(groupId, request.getClientMessageId());

        if (existing.isPresent()) {
            GroupMessage message = existing.get();

            if (!message.getSenderId().equals(senderId)) {
                throw new SecurityException("clientMessageId belongs to another sender");
            }


            GroupMessageResponse response = GroupMessageResponse.from(message);
            webSocketGateway.sendToUser(senderId,
                    OutboundMessage.responseTo(
                            OutboundMessage.Type.GROUP_MESSAGE_ACK,
                            requestId,
                            response
                    ));
            return response;
        }

        Long seq = groupMessageRepository.nextSequenceNumber(groupId);

        GroupMessage saved = groupMessageRepository.save(GroupMessage.builder()
                .clientMessageId(request.getClientMessageId())
                .groupId(groupId)
                .senderId(senderId)
                .content(request.getContent())
                .sequenceNumber(seq)
                .build());

        GroupMessageResponse response = GroupMessageResponse.from(saved);

        List<UUID> memberIds = groupMemberRepository
                .findUserIdsByGroupId(groupId)
                .stream()
                .filter(id -> !id.equals(senderId))
                .toList();

        afterCommit(() -> {
            webSocketGateway.sendToUser(senderId,
                    OutboundMessage.responseTo(
                            OutboundMessage.Type.GROUP_MESSAGE_ACK,
                            requestId,
                            response
                    ));

            webSocketGateway.sendToUsers(memberIds,
                    OutboundMessage.of(OutboundMessage.Type.GROUP_MESSAGE, response));
        });

        log.debug("Saved group message id={} seq={} groupId={} fanoutMembers={}",
                saved.getId(), seq, groupId, memberIds.size());

        return response;
    }

    @Transactional(readOnly = true)
    public void replayForUser(UUID userId) {
        groupMemberRepository.findByUserId(userId).forEach(membership -> {
            List<GroupMessage> missed = groupMessageRepository.findPageAfterExcludingSender(
                    membership.getGroupId(),
                    membership.getLastDeliveredSequenceNumber(),
                    userId,
                    PageRequest.of(0, 200)
            );

            missed.forEach(message -> webSocketGateway.sendToUser(userId,
                    OutboundMessage.of(
                            OutboundMessage.Type.GROUP_MESSAGE,
                            GroupMessageResponse.from(message)
                    )));

            log.debug("Replayed {} group messages to userId={} in groupId={}",
                    missed.size(), userId, membership.getGroupId());
        });
    }

    private void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    @Transactional(readOnly = true)
    public List<GroupMessageResponse> getHistory(UUID callerId,
                                                 UUID groupId,
                                                 Long before,
                                                 int limit) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, callerId)) {
            throw new SecurityException("User " + callerId
                    + " is not a member of group " + groupId);
        }

        long beforeSequence = before == null ? Long.MAX_VALUE : before;
        int pageSize = Math.min(Math.max(limit, 1), 100);

        return groupMessageRepository.findPageBefore(
                        groupId,
                        beforeSequence,
                        PageRequest.of(0, pageSize)
                )
                .stream()
                .sorted(java.util.Comparator.comparing(GroupMessage::getSequenceNumber))
                .map(GroupMessageResponse::from)
                .toList();
    }
}