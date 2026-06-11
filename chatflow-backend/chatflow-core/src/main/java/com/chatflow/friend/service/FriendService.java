package com.chatflow.friend.service;

import com.chatflow.friend.dto.FriendRequest;
import com.chatflow.friend.dto.FriendshipResponse;
import com.chatflow.friend.entity.Friendship;
import com.chatflow.friend.entity.FriendshipStatus;
import com.chatflow.friend.repository.FriendshipRepository;
import com.chatflow.infra.outbox.OutboxEventType;
import com.chatflow.infra.outbox.OutboxWriter;
import com.chatflow.infra.tx.AfterCommit;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.notification.entity.NotificationType;
import com.chatflow.notification.entity.ReferenceType;
import com.chatflow.notification.event.NotificationCommand;
import com.chatflow.user.entity.User;
import com.chatflow.user.repository.UserRepository;
import com.chatflow.user.service.UserDirectory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;
    private final UserDirectory userDirectory;
    private final WebSocketGateway webSocketGateway;
    private final OutboxWriter outboxWriter;

    /** Build a response from {@code perspective}'s point of view, resolving the other user's name. */
    private FriendshipResponse toResponse(Friendship friendship, UUID perspective) {
        UUID other = friendship.otherUserId(perspective);
        return FriendshipResponse.from(friendship, perspective,
                userDirectory.username(other).orElse(null));
    }

    @Transactional
    public FriendshipResponse sendRequest(UUID callerId, FriendRequest request) {

        User targetUser = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new IllegalArgumentException(
                        "User not found: " + request.getUsername()));

        UUID targetId = targetUser.getId();

        if (callerId.equals(targetId)) {
            throw new IllegalArgumentException(
                    "You cannot send a friend request to yourself"
            );
        }

        Friendship friendship = friendshipRepository.findByUsers(callerId, targetId)
                .map(existing -> {

                    if (existing.getStatus() == FriendshipStatus.ACCEPTED) {
                        throw new IllegalArgumentException(
                                "You are already friends"
                        );
                    }

                    if (existing.getStatus() == FriendshipStatus.PENDING) {
                        throw new IllegalArgumentException(
                                "A friend request already exists"
                        );
                    }

                    existing.resend(callerId);

                    log.debug(
                            "Re-sent friend request from {} to {}",
                            callerId,
                            targetId
                    );

                    return existing;
                })
                .orElseGet(() -> {

                    try {
                        Friendship created = friendshipRepository.saveAndFlush(
                                Friendship.create(callerId, targetId)
                        );

                        log.debug(
                                "Sent friend request from {} to {}",
                                callerId,
                                targetId
                        );

                        return created;
                    } catch (DataIntegrityViolationException e) {
                        // A concurrent request created the pair between our lookup
                        // and insert; the unique constraint is the backstop.
                        throw new IllegalArgumentException(
                                "A friend request already exists"
                        );
                    }
                });

        // Notify the recipient live; the sender has the REST response.
        AfterCommit.run(() -> webSocketGateway.sendToUser(targetId,
                OutboundMessage.of(OutboundMessage.Type.FRIEND_REQUEST,
                        toResponse(friendship, targetId))));

        // Durable notification via the transactional outbox.
        outboxWriter.writeNotification(OutboxEventType.FRIEND_REQUESTED,
                "friendship", friendship.getId(),
                new NotificationCommand(List.of(targetId), callerId,
                        NotificationType.FRIEND_REQUEST, ReferenceType.FRIENDSHIP,
                        friendship.getId(), "sent you a friend request", false));

        return toResponse(friendship, callerId);
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> getPendingReceived(UUID callerId) {

        return friendshipRepository.findPendingReceived(callerId)
                .stream()
                .map(friendship -> toResponse(friendship, callerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> getPendingSent(UUID callerId) {

        return friendshipRepository.findPendingSent(callerId)
                .stream()
                .map(friendship -> toResponse(friendship, callerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> getFriends(UUID callerId) {

        return friendshipRepository
                .findByUserAndStatus(
                        callerId,
                        FriendshipStatus.ACCEPTED
                )
                .stream()
                .map(friendship -> toResponse(friendship, callerId))
                .toList();
    }

    @Transactional
    public FriendshipResponse acceptRequest(
            UUID callerId,
            UUID friendshipId
    ) {

        Friendship friendship =
                findAndValidateRecipient(callerId, friendshipId);

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Request is already "
                            + friendship.getStatus().name().toLowerCase()
            );
        }

        friendship.accept();

        UUID otherUserId = friendship.otherUserId(callerId);
        log.debug(
                "Friendship accepted between {} and {}",
                callerId,
                otherUserId
        );

        // Notify the original requester that their request was accepted.
        AfterCommit.run(() -> webSocketGateway.sendToUser(otherUserId,
                OutboundMessage.of(OutboundMessage.Type.FRIEND_REQUEST_ACCEPTED,
                        toResponse(friendship, otherUserId))));

        // Durable notification via the transactional outbox.
        outboxWriter.writeNotification(OutboxEventType.FRIEND_REQUEST_ACCEPTED,
                "friendship", friendship.getId(),
                new NotificationCommand(List.of(otherUserId), callerId,
                        NotificationType.FRIEND_REQUEST_ACCEPTED, ReferenceType.FRIENDSHIP,
                        friendship.getId(), "accepted your friend request", false));

        return toResponse(friendship, callerId);
    }

    @Transactional
    public FriendshipResponse declineRequest(
            UUID callerId,
            UUID friendshipId
    ) {

        Friendship friendship =
                findAndValidateRecipient(callerId, friendshipId);

        if (friendship.getStatus() != FriendshipStatus.PENDING) {
            throw new IllegalArgumentException(
                    "Request is already "
                            + friendship.getStatus().name().toLowerCase()
            );
        }

        friendship.reject();

        UUID otherUserId = friendship.otherUserId(callerId);
        log.debug(
                "Friendship declined by {} for request {}",
                callerId,
                friendshipId
        );

        // Let the requester clear the pending request from their UI.
        AfterCommit.run(() -> webSocketGateway.sendToUser(otherUserId,
                OutboundMessage.of(OutboundMessage.Type.FRIEND_REQUEST_DECLINED,
                        toResponse(friendship, otherUserId))));

        return toResponse(friendship, callerId);
    }

    @Transactional
    public void unfriend(UUID callerId, UUID otherUserId) {

        Friendship friendship = friendshipRepository
                .findByUsers(callerId, otherUserId)
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No friendship found with user " + otherUserId
                ));

        friendshipRepository.delete(friendship);

        log.debug(
                "Unfriended: {} and {}",
                callerId,
                otherUserId
        );

        // Tell the other user to drop the caller from their friends list.
        AfterCommit.run(() -> webSocketGateway.sendToUser(otherUserId,
                OutboundMessage.of(OutboundMessage.Type.FRIEND_REMOVED,
                        Map.of("userId", callerId))));
    }

    private Friendship findAndValidateRecipient(
            UUID callerId,
            UUID friendshipId
    ) {

        Friendship friendship = friendshipRepository
                .findById(friendshipId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Friend request not found: " + friendshipId
                ));

        if (!friendship.involves(callerId)) {
            throw new AccessDeniedException(
                    "Not your friend request"
            );
        }

        if (friendship.getInitiatorId().equals(callerId)) {
            throw new AccessDeniedException(
                    "You cannot accept or decline your own request"
            );
        }

        return friendship;
    }
}