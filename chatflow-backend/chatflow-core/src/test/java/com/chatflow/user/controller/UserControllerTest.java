package com.chatflow.user.controller;

import com.chatflow.user.dto.UserSummary;
import com.chatflow.user.entity.User;
import com.chatflow.user.repository.UserRepository;
import com.chatflow.user.service.UserDirectory;
import org.junit.jupiter.api.Test;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserControllerTest {

    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserController controller = new UserController(userDirectory, userRepository);

    private Principal principalFor(UUID id) {
        return id::toString;
    }

    @Test
    void searchDelegatesWithCallerAsExcludedSelf() {
        UUID caller = UUID.randomUUID();
        UserSummary match = new UserSummary(UUID.randomUUID(), "bob");
        when(userDirectory.search(eq("bo"), eq(caller), eq(10))).thenReturn(List.of(match));

        List<UserSummary> result = controller.search("bo", 10, principalFor(caller));

        assertThat(result).containsExactly(match);
    }

    @Test
    void meReturnsCallerProjection() {
        UUID caller = UUID.randomUUID();
        User user = User.builder().id(caller).username("alice").build();
        when(userRepository.findById(caller)).thenReturn(Optional.of(user));

        UserSummary me = controller.me(principalFor(caller));

        assertThat(me.id()).isEqualTo(caller);
        assertThat(me.username()).isEqualTo("alice");
    }

    @Test
    void meThrowsWhenUserMissing() {
        UUID caller = UUID.randomUUID();
        when(userRepository.findById(caller)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.me(principalFor(caller)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
