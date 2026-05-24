package com.chatflow.group.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.UUID;

@Data
public class CreateGroupRequest {

    @NotBlank
    @Size(min = 1, max = 100)
    private String name;

    @NotEmpty
    @Size(max = 100)
    private List<@NotNull UUID> memberIds;
}