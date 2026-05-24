package com.chatflow.group.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.util.UUID;

@Data
public class GroupDeliveryAckRequest {

    @NotNull
    private UUID groupId;

    @NotNull
    @Positive
    private Long upToSequenceNumber;
}