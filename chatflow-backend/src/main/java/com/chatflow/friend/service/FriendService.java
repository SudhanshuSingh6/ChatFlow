package com.chatflow.friend.service;

import com.chatflow.friend.dto.FriendRequest;
import com.chatflow.friend.dto.FriendshipResponse;
import com.chatflow.friend.entity.Friendship;
import com.chatflow.friend.entity.FriendshipStatus;
import com.chatflow.friend.repository.FriendshipRepository;
import com.chatflow.user.entity.User;
import com.chatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class FriendService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

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

        return friendshipRepository.findByUsers(callerId, targetId)
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

                    return FriendshipResponse.from(existing, callerId);
                })
                .orElseGet(() -> {

                    Friendship friendship = friendshipRepository.save(
                            Friendship.create(callerId, targetId)
                    );

                    log.debug(
                            "Sent friend request from {} to {}",
                            callerId,
                            targetId
                    );

                    return FriendshipResponse.from(friendship, callerId);
                });
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> getPendingReceived(UUID callerId) {

        return friendshipRepository.findPendingReceived(callerId)
                .stream()
                .map(friendship -> FriendshipResponse.from(friendship, callerId))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FriendshipResponse> getPendingSent(UUID callerId) {

        return friendshipRepository.findPendingSent(callerId)
                .stream()
                .map(friendship -> FriendshipResponse.from(friendship, callerId))
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
                .map(friendship -> FriendshipResponse.from(friendship, callerId))
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

        log.debug(
                "Friendship accepted between {} and {}",
                callerId,
                friendship.otherUserId(callerId)
        );

        return FriendshipResponse.from(friendship, callerId);
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

        log.debug(
                "Friendship declined by {} for request {}",
                callerId,
                friendshipId
        );

        return FriendshipResponse.from(friendship, callerId);
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