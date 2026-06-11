package com.chatflow.user.repository;

import com.chatflow.user.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    /** Case-insensitive substring match for the people-picker search. */
    List<User> findByUsernameContainingIgnoreCase(String username, Pageable pageable);
}