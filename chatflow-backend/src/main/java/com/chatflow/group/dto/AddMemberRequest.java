package com.chatflow.group.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class AddMemberRequest {

    @NotNull
    private UUID userId;
}