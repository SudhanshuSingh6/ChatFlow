package com.chatflow.friend.dto;

import com.chatflow.friend.entity.Friendship;
import com.chatflow.friend.entity.FriendshipStatus;

import java.time.Instant;
import java.util.UUID;

public record FriendshipResponse(
        UUID id,
        UUID otherUserId,
        String otherUsername,
        UUID initiatorId,
        FriendshipStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static FriendshipResponse from(
            Friendship friendship,
            UUID callerId,
            String otherUsername
    ) {
        return new FriendshipResponse(
                friendship.getId(),
                friendship.otherUserId(callerId),
                otherUsername,
                friendship.getInitiatorId(),
                friendship.getStatus(),
                friendship.getCreatedAt(),
                friendship.getUpdatedAt()
        );
    }
}
