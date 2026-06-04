package com.chatflow.friend.controller;

import com.chatflow.friend.dto.FriendRequest;
import com.chatflow.friend.dto.FriendshipResponse;
import com.chatflow.friend.service.FriendService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final FriendService friendService;

    @PostMapping("/requests")
    @ResponseStatus(HttpStatus.CREATED)
    public FriendshipResponse sendRequest(@RequestBody @Valid FriendRequest request,
                                          Principal principal) {
        return friendService.sendRequest(callerId(principal), request);
    }

    @GetMapping("/requests/received")
    public List<FriendshipResponse> getPendingReceived(Principal principal) {
        return friendService.getPendingReceived(callerId(principal));
    }

    @GetMapping("/requests/sent")
    public List<FriendshipResponse> getPendingSent(Principal principal) {
        return friendService.getPendingSent(callerId(principal));
    }

    @PostMapping("/requests/{friendshipId}/accept")
    public FriendshipResponse accept(@PathVariable UUID friendshipId, Principal principal) {
        return friendService.acceptRequest(callerId(principal), friendshipId);
    }

    @PostMapping("/requests/{friendshipId}/decline")
    public FriendshipResponse decline(@PathVariable UUID friendshipId, Principal principal) {
        return friendService.declineRequest(callerId(principal), friendshipId);
    }

    @GetMapping
    public List<FriendshipResponse> getFriends(Principal principal) {
        return friendService.getFriends(callerId(principal));
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void unfriend(@PathVariable UUID userId, Principal principal) {
        friendService.unfriend(callerId(principal), userId);
    }

    private UUID callerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}