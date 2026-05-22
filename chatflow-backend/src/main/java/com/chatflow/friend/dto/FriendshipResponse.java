package com.chatflow.friend.dto;

import com.chatflow.friend.entity.Friendship;
import com.chatflow.friend.entity.FriendshipStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

public record FriendshipResponse(
        UUID id,
        UUID otherUserId,
        UUID initiatorId,
        FriendshipStatus status,
        Instant createdAt,
        Instant updatedAt
) {
    public static FriendshipResponse from(
            Friendship friendship,
            UUID callerId
    ) {
        return new FriendshipResponse(
                friendship.getId(),
                friendship.otherUserId(callerId),
                friendship.getInitiatorId(),
                friendship.getStatus(),
                friendship.getCreatedAt(),
                friendship.getUpdatedAt()
        );
    }
}