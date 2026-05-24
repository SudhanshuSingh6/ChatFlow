package com.chatflow.group.controller;

import com.chatflow.group.dto.*;
import com.chatflow.group.service.GroupChatService;
import com.chatflow.group.service.GroupService;
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

@Validated
@RestController
@RequestMapping("/api/groups")
@RequiredArgsConstructor
public class GroupController {

    private final GroupService groupService;
    private final GroupChatService groupChatService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GroupResponse create(@RequestBody @Valid CreateGroupRequest request,
                                Principal principal) {
        return groupService.create(callerId(principal), request);
    }

    @GetMapping
    public List<GroupResponse> list(Principal principal) {
        return groupService.listForCaller(callerId(principal));
    }

    @GetMapping("/{groupId}")
    public GroupResponse get(@PathVariable UUID groupId, Principal principal) {
        return groupService.getById(callerId(principal), groupId);
    }

    @DeleteMapping("/{groupId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID groupId, Principal principal) {
        groupService.deleteGroup(callerId(principal), groupId);
    }

    @PostMapping("/{groupId}/members")
    @ResponseStatus(HttpStatus.CREATED)
    public GroupMemberResponse addMember(@PathVariable UUID groupId,
                                         @RequestBody @Valid AddMemberRequest request,
                                         Principal principal) {
        return groupService.addMember(callerId(principal), groupId, request);
    }

    @DeleteMapping("/{groupId}/members/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeMember(@PathVariable UUID groupId,
                             @PathVariable UUID userId,
                             Principal principal) {
        groupService.removeMember(callerId(principal), groupId, userId);
    }

    @PutMapping("/{groupId}/members/{userId}/role")
    public GroupMemberResponse updateRole(@PathVariable UUID groupId,
                                          @PathVariable UUID userId,
                                          @RequestBody @Valid UpdateMemberRoleRequest request,
                                          Principal principal) {
        return groupService.updateMemberRole(callerId(principal), groupId, userId, request);
    }

    @PostMapping("/{groupId}/transfer-ownership")
    public GroupResponse transferOwnership(@PathVariable UUID groupId,
                                           @RequestBody @Valid TransferOwnershipRequest request,
                                           Principal principal) {
        return groupService.transferOwnership(callerId(principal), groupId, request);
    }

    private UUID callerId(Principal principal) {
        return UUID.fromString(principal.getName());
    }

    @GetMapping("/{groupId}/messages")
    public List<GroupMessageResponse> messages(@PathVariable UUID groupId,
                                               @RequestParam(required = false) Long before,
                                               @RequestParam(defaultValue = "30") @Min(1) @Max(100) int limit,
                                               Principal principal) {
        return groupChatService.getHistory(
                callerId(principal),
                groupId,
                before,
                limit
        );
    }
}