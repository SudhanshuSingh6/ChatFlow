package com.chatflow.group.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class TransferOwnershipRequest {

    @NotNull
    private UUID newOwnerId;
}