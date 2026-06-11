package com.chatflow.user.controller;

import com.chatflow.user.dto.UserSummary;
import com.chatflow.user.repository.UserRepository;
import com.chatflow.user.service.UserDirectory;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Read-only user lookups for the client: people-picker search (new chat / new group /
 * add friend) and "who am I". Display names elsewhere are embedded in their owning
 * responses (participants, friendships, conversation titles), not fetched here.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private static final int DEFAULT_SEARCH_LIMIT = 10;

    private final UserDirectory userDirectory;
    private final UserRepository userRepository;

    @GetMapping("/search")
    public List<UserSummary> search(
            @RequestParam("q") String query,
            @RequestParam(defaultValue = "" + DEFAULT_SEARCH_LIMIT) int limit,
            Principal principal) {
        return userDirectory.search(query, callerId(principal), limit);
    }

    @GetMapping("/me")
    public UserSummary me(Principal principal) {
        UUID callerId = callerId(principal);
        return userRepository.findById(callerId)
                .map(UserSummary::from)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + callerId));
    }

    private UUID callerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
