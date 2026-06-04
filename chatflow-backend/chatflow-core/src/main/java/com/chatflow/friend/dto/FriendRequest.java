package com.chatflow.friend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class FriendRequest {

    @NotBlank
    @Size(min = 3, max = 30)
    private String username;
}