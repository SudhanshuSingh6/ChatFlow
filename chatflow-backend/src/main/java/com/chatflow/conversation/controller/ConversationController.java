package com.chatflow.conversation.controller;

import com.chatflow.conversation.dto.*;
import com.chatflow.conversation.service.ConversationRagService;
import com.chatflow.conversation.service.ConversationService;
import com.chatflow.conversation.service.ConversationSummaryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

/**
 * Unified conversation API for both DIRECT and GROUP chats. Group management is
 * modelled as sub-resources (participants, roles), replacing the separate
 * {@code /api/groups} controller and the old 1:1-only conversation controller.
 */
@Validated
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ConversationService conversationService;
    private final ConversationSummaryService conversationSummaryService;
    private final ConversationRagService conversationRagService;

    // ---- creation ----

    /** Open (or fetch the existing) 1:1 conversation with another user. */
    @PostMapping("/direct")
    public ConversationResponse getOrCreateDirect(
            @RequestBody @Valid CreateDirectRequest request, Principal principal) {
        return conversationService.getOrCreateDirect(callerId(principal), request.userId());
    }

    @PostMapping("/group")
    @ResponseStatus(HttpStatus.CREATED)
    public ConversationResponse createGroup(
            @RequestBody @Valid CreateGroupRequest request, Principal principal) {
        return conversationService.createGroup(callerId(principal), request.name(), request.memberIds());
    }

    // ---- reads ----

    @GetMapping
    public List<ConversationResponse> list(Principal principal) {
        return conversationService.listForCaller(callerId(principal));
    }

    @GetMapping("/{conversationId}")
    public ConversationResponse get(@PathVariable UUID conversationId, Principal principal) {
        return conversationService.getById(callerId(principal), conversationId);
    }

    @GetMapping("/{conversationId}/messages")
    public MessagePageResponse getMessages(
            @PathVariable UUID conversationId,
            @RequestParam(defaultValue = "" + Long.MAX_VALUE) long before,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int limit,
            Principal principal) {
        return conversationService.getMessages(callerId(principal), conversationId, before, limit);
    }

    @GetMapping("/{conversationId}/messages/after")
    public MessagePageResponse getMessagesAfter(
            @PathVariable UUID conversationId,
            @RequestParam long after,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int limit,
            Principal principal) {
        return conversationService.getMessagesAfter(callerId(principal), conversationId, after, limit);
    }

    /** AI "catch me up" — summarize everything the caller hasn't read in this conversation. */
    @PostMapping("/{conversationId}/summary")
    public SummaryResponse summarize(@PathVariable UUID conversationId, Principal principal) {
        return conversationSummaryService.summarizeUnread(callerId(principal), conversationId);
    }

    /** RAG "ask your chat history" — answer a question grounded in this conversation, with citations. */
    @PostMapping("/{conversationId}/ask")
    public AskResponse ask(@PathVariable UUID conversationId,
                           @RequestBody @Valid AskRequest request,
                           Principal principal) {
        return conversationRagService.ask(callerId(principal), conversationId, request.question());
    }

    // ---- group lifecycle / membership ----

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteGroup(@PathVariable UUID conversationId, Principal principal) {
        conversationService.deleteGroup(callerId(principal), conversationId);
    }

    @PostMapping("/{conversationId}/participants")
    @ResponseStatus(HttpStatus.CREATED)
    public ParticipantResponse addParticipant(
            @PathVariable UUID conversationId,
            @RequestBody @Valid AddParticipantRequest request,
            Principal principal) {
        return conversationService.addMember(callerId(principal), conversationId, request.userId());
    }

    @DeleteMapping("/{conversationId}/participants/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeParticipant(
            @PathVariable UUID conversationId,
            @PathVariable UUID userId,
            Principal principal) {
        conversationService.removeMember(callerId(principal), conversationId, userId);
    }

    @PutMapping("/{conversationId}/participants/{userId}/role")
    public ParticipantResponse updateRole(
            @PathVariable UUID conversationId,
            @PathVariable UUID userId,
            @RequestBody @Valid UpdateRoleRequest request,
            Principal principal) {
        return conversationService.updateMemberRole(
                callerId(principal), conversationId, userId, request.role());
    }

    @PostMapping("/{conversationId}/transfer-ownership")
    public ConversationResponse transferOwnership(
            @PathVariable UUID conversationId,
            @RequestBody @Valid TransferOwnershipRequest request,
            Principal principal) {
        return conversationService.transferOwnership(
                callerId(principal), conversationId, request.newOwnerId());
    }

    private UUID callerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }
}
