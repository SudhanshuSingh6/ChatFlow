package com.chatflow.group.dto;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/**
 * Broadcast to all group members when a user reads up to a sequence number.
 * Allows the UI to show "seen by N members" or per-member read indicators.
 */
@Data
@Builder
public class GroupReadReceiptResponse {

    private UUID groupId;
    private UUID userId;
    private Long lastReadSequenceNumber;
}