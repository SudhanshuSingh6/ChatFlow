package com.chatflow.user.dto;

import com.chatflow.user.entity.User;

import java.util.UUID;

/** Minimal public projection of a user — id + username, for display and pickers. */
public record UserSummary(UUID id, String username) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getUsername());
    }
}
