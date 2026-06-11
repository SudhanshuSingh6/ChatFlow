package com.chatflow.user.service;

import com.chatflow.user.dto.UserSummary;
import com.chatflow.user.entity.User;
import com.chatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Read-only user lookups for assembling DTOs that reference users by id, plus the
 * people-picker search. Centralises batch resolution so callers avoid N+1 queries.
 */
@Service
@RequiredArgsConstructor
public class UserDirectory {

    private static final int MAX_SEARCH_LIMIT = 25;

    private final UserRepository userRepository;

    /** Batch-resolve ids to usernames in a single query. Unknown ids are simply absent. */
    @Transactional(readOnly = true)
    public Map<UUID, String> usernames(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return Map.of();
        }
        return userRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));
    }

    @Transactional(readOnly = true)
    public Optional<String> username(UUID id) {
        return userRepository.findById(id).map(User::getUsername);
    }

    /** Substring search by username, excluding the caller; capped at {@value #MAX_SEARCH_LIMIT}. */
    @Transactional(readOnly = true)
    public List<UserSummary> search(String query, UUID excludeSelf, int limit) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        int capped = Math.min(Math.max(limit, 1), MAX_SEARCH_LIMIT);
        return userRepository
                .findByUsernameContainingIgnoreCase(query.trim(), PageRequest.of(0, capped))
                .stream()
                .filter(u -> !u.getId().equals(excludeSelf))
                .map(UserSummary::from)
                .toList();
    }
}
