package com.chatflow.group.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class GroupReadReceiptRequest {

    @NotNull
    private UUID groupId;

    /**
     * High-water mark — marks all messages up to and including this
     * sequenceNumber as read for the calling user. Same cursor approach
     * as 1:1 SEEN receipts.
     */
    @NotNull
    @Positive
    private Long upToSequenceNumber;
}