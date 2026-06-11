package com.chatflow.friend.service;

import com.chatflow.friend.dto.FriendRequest;
import com.chatflow.friend.entity.Friendship;
import com.chatflow.friend.repository.FriendshipRepository;
import com.chatflow.infra.outbox.OutboxWriter;
import com.chatflow.infra.websocket.OutboundMessage;
import com.chatflow.infra.websocket.WebSocketGateway;
import com.chatflow.user.entity.User;
import com.chatflow.user.repository.UserRepository;
import com.chatflow.user.service.UserDirectory;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * No transaction is active in these unit tests, so {@code AfterCommit.run} executes
 * inline — letting us assert the gateway push and its recipient directly.
 */
class FriendServiceTest {

    private final FriendshipRepository friendshipRepo = mock(FriendshipRepository.class);
    private final UserRepository userRepo = mock(UserRepository.class);
    private final UserDirectory userDirectory = mock(UserDirectory.class);
    private final WebSocketGateway gateway = mock(WebSocketGateway.class);
    private final OutboxWriter outboxWriter = mock(OutboxWriter.class);
    private final FriendService service =
            new FriendService(friendshipRepo, userRepo, userDirectory, gateway, outboxWriter);

    private OutboundMessage capturePushTo(UUID userId) {
        ArgumentCaptor<OutboundMessage> frame = ArgumentCaptor.forClass(OutboundMessage.class);
        verify(gateway).sendToUser(eq(userId), frame.capture());
        return frame.getValue();
    }

    @Test
    void sendRequestNotifiesRecipient() {
        UUID caller = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User targetUser = mock(User.class);
        when(targetUser.getId()).thenReturn(target);
        when(userRepo.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(friendshipRepo.findByUsers(caller, target)).thenReturn(Optional.empty());
        when(friendshipRepo.saveAndFlush(any(Friendship.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        FriendRequest req = new FriendRequest();
        req.setUsername("bob");
        service.sendRequest(caller, req);

        assertThat(capturePushTo(target).getType())
                .isEqualTo(OutboundMessage.Type.FRIEND_REQUEST);
    }

    @Test
    void sendRequestLosingAConcurrentInsertReturnsBadRequestNotServerError() {
        UUID caller = UUID.randomUUID();
        UUID target = UUID.randomUUID();
        User targetUser = mock(User.class);
        when(targetUser.getId()).thenReturn(target);
        when(userRepo.findByUsername("bob")).thenReturn(Optional.of(targetUser));
        when(friendshipRepo.findByUsers(caller, target)).thenReturn(Optional.empty());
        // A concurrent request inserted the pair first → unique constraint trips.
        when(friendshipRepo.saveAndFlush(any(Friendship.class)))
                .thenThrow(new DataIntegrityViolationException("uk_friendship_pair"));

        FriendRequest req = new FriendRequest();
        req.setUsername("bob");

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sendRequest(caller, req))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(gateway);
    }

    @Test
    void acceptNotifiesOriginalRequester() {
        UUID requester = UUID.randomUUID();
        UUID caller = UUID.randomUUID(); // the recipient accepting
        Friendship friendship = Friendship.create(requester, caller); // initiator = requester
        UUID friendshipId = UUID.randomUUID();
        when(friendshipRepo.findById(friendshipId)).thenReturn(Optional.of(friendship));

        service.acceptRequest(caller, friendshipId);

        assertThat(capturePushTo(requester).getType())
                .isEqualTo(OutboundMessage.Type.FRIEND_REQUEST_ACCEPTED);
    }

    @Test
    void declineNotifiesOriginalRequester() {
        UUID requester = UUID.randomUUID();
        UUID caller = UUID.randomUUID();
        Friendship friendship = Friendship.create(requester, caller);
        UUID friendshipId = UUID.randomUUID();
        when(friendshipRepo.findById(friendshipId)).thenReturn(Optional.of(friendship));

        service.declineRequest(caller, friendshipId);

        assertThat(capturePushTo(requester).getType())
                .isEqualTo(OutboundMessage.Type.FRIEND_REQUEST_DECLINED);
    }

    @Test
    void unfriendNotifiesOtherUser() {
        UUID caller = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Friendship friendship = Friendship.create(caller, other);
        friendship.accept();
        when(friendshipRepo.findByUsers(caller, other)).thenReturn(Optional.of(friendship));

        service.unfriend(caller, other);

        assertThat(capturePushTo(other).getType())
                .isEqualTo(OutboundMessage.Type.FRIEND_REMOVED);
    }
}
