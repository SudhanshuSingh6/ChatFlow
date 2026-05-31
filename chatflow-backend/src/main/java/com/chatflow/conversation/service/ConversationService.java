package com.chatflow.conversation.service;

import com.chatflow.conversation.dto.ConversationResponse;
import com.chatflow.conversation.dto.MessagePageResponse;
import com.chatflow.conversation.dto.MessageResponse;
import com.chatflow.conversation.dto.ParticipantResponse;
import com.chatflow.conversation.entity.Conversation;
import com.chatflow.conversation.entity.ConversationParticipant;
import com.chatflow.conversation.entity.ParticipantRole;
import com.chatflow.conversation.repository.ConversationParticipantRepository;
import com.chatflow.conversation.repository.ConversationRepository;
import com.chatflow.conversation.repository.MessageRepository;
import com.chatflow.friend.repository.FriendshipRepository;
import com.chatflow.infra.outbox.OutboxEventType;
import com.chatflow.infra.outbox.OutboxWriter;
import com.chatflow.infra.tx.AfterCommit;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.notification.entity.NotificationType;
import com.chatflow.notification.entity.ReferenceType;
import com.chatflow.notification.event.NotificationCommand;
import com.chatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Lifecycle for unified conversations: DM get-or-create, group create/membership/
 * roles, and message history paging. Replaces the old ConversationService +
 * GroupService.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private static final int MAX_PAGE_SIZE = 50;

    private final ConversationRepository conversationRepository;
    private final ConversationParticipantRepository participantRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final WebSocketGateway webSocketGateway;
    private final OutboxWriter outboxWriter;

    // ----------------------------------------------------------------
    // Direct conversations
    // ----------------------------------------------------------------

    @Transactional
    public ConversationResponse getOrCreateDirect(UUID callerId, UUID otherId) {
        if (callerId.equals(otherId)) {
            throw new IllegalArgumentException("Cannot create a conversation with yourself");
        }
        if (!userRepository.existsById(otherId)) {
            throw new IllegalArgumentException("User not found: " + otherId);
        }

        String dmKey = Conversation.dmKey(callerId, otherId);
        Conversation conversation = conversationRepository.findByDmKey(dmKey)
                .orElseGet(() -> createDirect(callerId, otherId, dmKey));

        return detailFor(conversation, callerId);
    }

    private Conversation createDirect(UUID callerId, UUID otherId, String dmKey) {
        try {
            Conversation conversation =
                    conversationRepository.save(Conversation.direct(callerId, otherId));
            participantRepository.save(
                    ConversationParticipant.of(conversation.getId(), callerId, ParticipantRole.MEMBER));
            participantRepository.save(
                    ConversationParticipant.of(conversation.getId(), otherId, ParticipantRole.MEMBER));
            log.debug("Created direct conversation {} between {} and {}",
                    conversation.getId(), callerId, otherId);
            return conversation;
        } catch (DataIntegrityViolationException e) {
            // Concurrent creation lost the race; the dm_key unique constraint is the backstop.
            return conversationRepository.findByDmKey(dmKey)
                    .orElseThrow(() -> e);
        }
    }

    // ----------------------------------------------------------------
    // Group conversations
    // ----------------------------------------------------------------

    @Transactional
    public ConversationResponse createGroup(UUID callerId, String name, List<UUID> memberIds) {
        List<UUID> unique = memberIds.stream().distinct().toList();
        if (unique.size() != memberIds.size()) {
            throw new IllegalArgumentException("Duplicate memberIds are not allowed");
        }
        unique.forEach(memberId -> {
            if (!userRepository.existsById(memberId)) {
                throw new IllegalArgumentException("User not found: " + memberId);
            }
            if (memberId.equals(callerId)) {
                throw new IllegalArgumentException(
                        "Do not include yourself, you are added as OWNER automatically");
            }
            if (!friendshipRepository.areFriends(callerId, memberId)) {
                throw new IllegalArgumentException("User " + memberId + " is not your friend");
            }
        });

        Conversation group = conversationRepository.save(Conversation.group(name, callerId));
        participantRepository.save(
                ConversationParticipant.of(group.getId(), callerId, ParticipantRole.OWNER));
        unique.forEach(memberId -> participantRepository.save(
                ConversationParticipant.of(group.getId(), memberId, ParticipantRole.MEMBER)));

        log.debug("Created group {} '{}' with {} members", group.getId(), name, unique.size() + 1);

        ConversationResponse response = detailFor(group, callerId);
        AfterCommit.run(() -> webSocketGateway.sendToUsers(unique,
                OutboundMessage.of(OutboundMessage.Type.GROUP_CREATED, response)));
        return response;
    }

    @Transactional
    public ParticipantResponse addMember(UUID callerId, UUID conversationId, UUID newMemberId) {
        Conversation conversation = lockGroup(conversationId);
        requireOwnerOrAdmin(conversationId, callerId);

        if (!userRepository.existsById(newMemberId)) {
            throw new IllegalArgumentException("User not found: " + newMemberId);
        }
        if (!friendshipRepository.areFriends(callerId, newMemberId)) {
            throw new IllegalArgumentException(
                    "User " + newMemberId + " is not your friend — only friends can be added");
        }

        // New members start caught up to "now" so they don't replay old history.
        long max = messageRepository.maxSequenceNumber(conversationId);
        try {
            ConversationParticipant member = participantRepository.save(
                    ConversationParticipant.builder()
                            .conversationId(conversationId)
                            .userId(newMemberId)
                            .role(ParticipantRole.MEMBER)
                            .lastReadSeq(max)
                            .lastDeliveredSeq(max)
                            .build());

            ParticipantResponse memberResponse = ParticipantResponse.from(member);
            List<UUID> recipients = recipientsExcept(conversationId, callerId);
            AfterCommit.run(() -> webSocketGateway.sendToUsers(recipients,
                    OutboundMessage.of(OutboundMessage.Type.GROUP_MEMBER_ADDED,
                            Map.of("conversationId", conversationId,
                                    "groupName", conversation.getName(),
                                    "member", memberResponse))));
            outboxWriter.writeNotification(OutboxEventType.GROUP_MEMBER_ADDED,
                    "conversation", conversationId,
                    new NotificationCommand(List.of(newMemberId), callerId,
                            NotificationType.GROUP_MEMBER_ADDED, ReferenceType.CONVERSATION,
                            conversationId, "added you to " + conversation.getName(), false));
            return memberResponse;
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(
                    "User " + newMemberId + " is already a member of " + conversationId);
        }
    }

    @Transactional
    public void removeMember(UUID callerId, UUID conversationId, UUID targetUserId) {
        lockGroup(conversationId);
        ConversationParticipant caller = requireMembership(conversationId, callerId);
        ConversationParticipant target = requireMembership(conversationId, targetUserId);

        boolean selfLeave = callerId.equals(targetUserId);
        if (selfLeave) {
            if (caller.getRole() == ParticipantRole.OWNER) {
                throw new IllegalArgumentException("Transfer ownership before leaving the group");
            }
        } else {
            if (caller.getRole() == ParticipantRole.MEMBER) {
                throw new SecurityException("Members cannot remove other members");
            }
            if (caller.getRole() == ParticipantRole.ADMIN
                    && target.getRole() != ParticipantRole.MEMBER) {
                throw new SecurityException(
                        "Admins can only remove members, not other admins or the owner");
            }
            if (target.getRole() == ParticipantRole.OWNER) {
                throw new SecurityException("The owner cannot be removed");
            }
        }

        participantRepository.deleteByConversationIdAndUserId(conversationId, targetUserId);
        log.debug("Removed {} from {} by {}", targetUserId, conversationId, callerId);

        Set<UUID> recipients = new HashSet<>(
                participantRepository.findUserIdsByConversationId(conversationId));
        recipients.add(targetUserId);
        recipients.remove(callerId);
        AfterCommit.run(() -> webSocketGateway.sendToUsers(recipients,
                OutboundMessage.of(OutboundMessage.Type.GROUP_MEMBER_REMOVED,
                        Map.of("conversationId", conversationId, "userId", targetUserId))));
        if (!selfLeave) {
            outboxWriter.writeNotification(OutboxEventType.GROUP_MEMBER_REMOVED,
                    "conversation", conversationId,
                    new NotificationCommand(List.of(targetUserId), callerId,
                            NotificationType.GROUP_MEMBER_REMOVED, ReferenceType.CONVERSATION,
                            conversationId, "removed you from the group", false));
        }
    }

    @Transactional
    public ParticipantResponse updateMemberRole(UUID callerId, UUID conversationId,
                                                UUID targetUserId, ParticipantRole newRole) {
        lockGroup(conversationId);
        participantRepository.findByConversationIdForUpdate(conversationId);

        ConversationParticipant caller = requireMembership(conversationId, callerId);
        if (caller.getRole() != ParticipantRole.OWNER) {
            throw new SecurityException("Only the group owner can perform this action");
        }
        if (callerId.equals(targetUserId)) {
            throw new IllegalArgumentException("You cannot change your own role");
        }
        if (newRole == ParticipantRole.OWNER) {
            throw new IllegalArgumentException("Use the transfer ownership endpoint to change the owner");
        }

        ConversationParticipant target = requireMembership(conversationId, targetUserId);
        if (target.getRole() == ParticipantRole.OWNER) {
            throw new IllegalArgumentException("Cannot change the owner's role — use transfer ownership");
        }

        target.setRole(newRole);
        ConversationParticipant saved = participantRepository.save(target);

        List<UUID> recipients = recipientsExcept(conversationId, callerId);
        AfterCommit.run(() -> webSocketGateway.sendToUsers(recipients,
                OutboundMessage.of(OutboundMessage.Type.GROUP_ROLE_CHANGED,
                        Map.of("conversationId", conversationId,
                                "userId", targetUserId, "role", newRole))));
        outboxWriter.writeNotification(OutboxEventType.GROUP_ROLE_CHANGED,
                "conversation", conversationId,
                new NotificationCommand(List.of(targetUserId), callerId,
                        NotificationType.GROUP_ROLE_CHANGED, ReferenceType.CONVERSATION,
                        conversationId, "changed your role to " + newRole, false));
        return ParticipantResponse.from(saved);
    }

    @Transactional
    public ConversationResponse transferOwnership(UUID callerId, UUID conversationId, UUID newOwnerId) {
        lockGroup(conversationId);
        participantRepository.findByConversationIdForUpdate(conversationId);

        if (callerId.equals(newOwnerId)) {
            throw new IllegalArgumentException("You are already the owner");
        }
        ConversationParticipant caller = requireMembership(conversationId, callerId);
        if (caller.getRole() != ParticipantRole.OWNER) {
            throw new SecurityException("Only the group owner can perform this action");
        }
        ConversationParticipant newOwner = requireMembership(conversationId, newOwnerId);

        caller.setRole(ParticipantRole.ADMIN);
        participantRepository.saveAndFlush(caller);
        newOwner.setRole(ParticipantRole.OWNER);
        participantRepository.save(newOwner);

        ConversationResponse response = detailFor(
                conversationRepository.findById(conversationId).orElseThrow(), callerId);
        List<UUID> recipients = recipientsExcept(conversationId, callerId);
        AfterCommit.run(() -> webSocketGateway.sendToUsers(recipients,
                OutboundMessage.of(OutboundMessage.Type.GROUP_OWNERSHIP_TRANSFERRED,
                        Map.of("conversationId", conversationId,
                                "newOwnerId", newOwnerId, "previousOwnerId", callerId))));
        outboxWriter.writeNotification(OutboxEventType.GROUP_OWNERSHIP_TRANSFERRED,
                "conversation", conversationId,
                new NotificationCommand(List.of(newOwnerId), callerId,
                        NotificationType.GROUP_OWNERSHIP_TRANSFERRED, ReferenceType.CONVERSATION,
                        conversationId, "made you the owner of " + response.name(), false));
        return response;
    }

    @Transactional
    public void deleteGroup(UUID callerId, UUID conversationId) {
        Conversation group = lockGroup(conversationId);
        requireOwner(conversationId, callerId);

        List<UUID> recipients = recipientsExcept(conversationId, callerId);
        messageRepository.deleteByConversationId(conversationId);
        participantRepository.deleteByConversationId(conversationId);
        conversationRepository.delete(group);
        log.debug("Deleted group {} by owner {}", conversationId, callerId);

        AfterCommit.run(() -> webSocketGateway.sendToUsers(recipients,
                OutboundMessage.of(OutboundMessage.Type.GROUP_DELETED,
                        Map.of("conversationId", conversationId))));
    }

    // ----------------------------------------------------------------
    // Reads
    // ----------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<ConversationResponse> listForCaller(UUID callerId) {
        return conversationRepository.findAllForUser(callerId).stream()
                .map(c -> {
                    ConversationParticipant me = requireMembership(c.getId(), callerId);
                    long unread = messageRepository.countUnread(c.getId(), me.getLastReadSeq(), callerId);
                    int memberCount = (int) participantRepository.countByConversationId(c.getId());
                    return ConversationResponse.summary(c, me.getRole(), unread, memberCount);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse getById(UUID callerId, UUID conversationId) {
        Conversation conversation = requireConversation(conversationId);
        requireMembership(conversationId, callerId);
        return detailFor(conversation, callerId);
    }

    @Transactional(readOnly = true)
    public MessagePageResponse getMessages(UUID callerId, UUID conversationId, long before, int limit) {
        requireConversation(conversationId);
        requireMembership(conversationId, callerId);
        int pageSize = Math.min(limit, MAX_PAGE_SIZE);
        List<MessageResponse> messages = messageRepository
                .findPageBefore(conversationId, before, PageRequest.of(0, pageSize))
                .stream().map(MessageResponse::from).toList();
        Long nextCursor = messages.size() == pageSize
                ? messages.get(messages.size() - 1).sequenceNumber() : null;
        return new MessagePageResponse(messages, nextCursor);
    }

    @Transactional(readOnly = true)
    public MessagePageResponse getMessagesAfter(UUID callerId, UUID conversationId, long after, int limit) {
        requireConversation(conversationId);
        requireMembership(conversationId, callerId);
        int pageSize = Math.min(limit, MAX_PAGE_SIZE);
        List<MessageResponse> messages = messageRepository
                .findPageAfter(conversationId, after, PageRequest.of(0, pageSize))
                .stream().map(MessageResponse::from).toList();
        Long nextCursor = messages.size() == pageSize
                ? messages.get(messages.size() - 1).sequenceNumber() : null;
        return new MessagePageResponse(messages, nextCursor);
    }

    // ----------------------------------------------------------------
    // Helpers
    // ----------------------------------------------------------------

    private ConversationResponse detailFor(Conversation conversation, UUID callerId) {
        List<ConversationParticipant> participants =
                participantRepository.findByConversationId(conversation.getId());
        ConversationParticipant me = participants.stream()
                .filter(p -> p.getUserId().equals(callerId))
                .findFirst()
                .orElseThrow(() -> new SecurityException(
                        "User " + callerId + " is not a participant in " + conversation.getId()));
        long unread = messageRepository.countUnread(conversation.getId(), me.getLastReadSeq(), callerId);
        return ConversationResponse.detail(
                conversation,
                participants.stream().map(ParticipantResponse::from).toList(),
                me.getRole(), unread);
    }

    private Conversation requireConversation(UUID conversationId) {
        return conversationRepository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
    }

    private Conversation lockGroup(UUID conversationId) {
        Conversation conversation = conversationRepository.findByIdForUpdate(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found: " + conversationId));
        if (!conversation.isGroup()) {
            throw new IllegalArgumentException("Conversation " + conversationId + " is not a group");
        }
        return conversation;
    }

    private ConversationParticipant requireMembership(UUID conversationId, UUID userId) {
        return participantRepository.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> new SecurityException(
                        "User " + userId + " is not a participant in " + conversationId));
    }

    private void requireOwner(UUID conversationId, UUID userId) {
        if (!participantRepository.existsByConversationIdAndUserIdAndRole(
                conversationId, userId, ParticipantRole.OWNER)) {
            throw new SecurityException("Only the group owner can perform this action");
        }
    }

    private void requireOwnerOrAdmin(UUID conversationId, UUID userId) {
        ConversationParticipant membership = requireMembership(conversationId, userId);
        if (membership.getRole() == ParticipantRole.MEMBER) {
            throw new SecurityException("Only admins and the owner can perform this action");
        }
    }

    private List<UUID> recipientsExcept(UUID conversationId, UUID exclude) {
        return participantRepository.findUserIdsByConversationId(conversationId).stream()
                .filter(id -> !id.equals(exclude))
                .toList();
    }
}
