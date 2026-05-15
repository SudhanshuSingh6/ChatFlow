package com.chatflow.message.controller;

import com.chatflow.message.dto.ConversationResponse;
import com.chatflow.message.dto.CreateConversationRequest;
import com.chatflow.message.dto.MessagePageResponse;
import com.chatflow.message.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ConversationResponse> getOrCreate(
            @RequestBody @Valid CreateConversationRequest request,
            Principal principal) {

        UUID callerId = UUID.fromString(principal.getName());
        ConversationResponse response = conversationService.getOrCreate(callerId, request);

        boolean isNew = response.getLastMessage() == null;
        HttpStatus status = isNew ? HttpStatus.CREATED : HttpStatus.OK;

        return ResponseEntity.status(status).body(response);
    }

    @GetMapping
    public List<ConversationResponse> list(Principal principal) {
        UUID callerId = UUID.fromString(principal.getName());
        return conversationService.listForCaller(callerId);
    }

    @GetMapping("/{conversationId}/messages")
    public MessagePageResponse getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "" + Long.MAX_VALUE) long before,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int limit,
            Principal principal) {

        UUID callerId = UUID.fromString(principal.getName());
        return conversationService.getMessages(callerId, conversationId, before, limit);
    }

    @GetMapping("/{conversationId}/messages/after")
    public MessagePageResponse getMessagesAfter(
            @PathVariable UUID conversationId,
            @RequestParam long after,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int limit,
            Principal principal) {

        UUID callerId = UUID.fromString(principal.getName());
        return conversationService.getMessagesAfter(callerId, conversationId, after, limit);
    }
}