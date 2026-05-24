package com.chatflow.group.service;

import com.chatflow.friend.repository.FriendshipRepository;
import com.chatflow.group.dto.AddMemberRequest;
import com.chatflow.group.dto.CreateGroupRequest;
import com.chatflow.group.dto.GroupMemberResponse;
import com.chatflow.group.dto.GroupResponse;
import com.chatflow.group.dto.TransferOwnershipRequest;
import com.chatflow.group.dto.UpdateMemberRoleRequest;
import com.chatflow.group.entity.Group;
import com.chatflow.group.entity.GroupMember;
import com.chatflow.group.entity.GroupMemberRole;
import com.chatflow.group.repository.GroupMemberRepository;
import com.chatflow.group.repository.GroupMessageRepository;
import com.chatflow.group.repository.GroupRepository;
import com.chatflow.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class GroupService {

    private final GroupRepository groupRepository;
    private final GroupMemberRepository groupMemberRepository;
    private final GroupMessageRepository groupMessageRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;

    @Transactional
    public GroupResponse create(UUID callerId, CreateGroupRequest request) {
        List<UUID> uniqueMemberIds = request.getMemberIds()
                .stream()
                .distinct()
                .toList();

        if (uniqueMemberIds.size() != request.getMemberIds().size()) {
            throw new IllegalArgumentException("Duplicate memberIds are not allowed");
        }

        uniqueMemberIds.forEach(memberId -> {
            if (!userRepository.existsById(memberId)) {
                throw new IllegalArgumentException("User not found: " + memberId);
            }
            if (memberId.equals(callerId)) {
                throw new IllegalArgumentException(
                        "Do not include yourself, you are added as OWNER automatically");
            }
            if (!friendshipRepository.areFriends(callerId, memberId)) {
                throw new IllegalArgumentException("User " + memberId + " is not your friend");
            }
        });

        Group group = groupRepository.save(Group.builder()
                .name(request.getName())
                .createdBy(callerId)
                .build());

        List<GroupMember> members = new ArrayList<>();

        members.add(groupMemberRepository.save(GroupMember.builder()
                .groupId(group.getId())
                .userId(callerId)
                .role(GroupMemberRole.OWNER)
                .build()));

        uniqueMemberIds.forEach(memberId ->
                members.add(groupMemberRepository.save(GroupMember.builder()
                        .groupId(group.getId())
                        .userId(memberId)
                        .role(GroupMemberRole.MEMBER)
                        .build())));

        log.debug("Created group id={} name={} members={}",
                group.getId(), group.getName(), members.size());

        return GroupResponse.from(group, members.stream()
                .map(GroupMemberResponse::from)
                .toList());
    }

    @Transactional(readOnly = true)
    public List<GroupResponse> listForCaller(UUID callerId) {
        return groupMemberRepository.findByUserId(callerId).stream()
                .map(membership -> {
                    Group group = groupRepository.findById(membership.getGroupId()).orElseThrow();
                    long unread = groupMessageRepository.countUnread(
                            group.getId(), membership.getLastReadSequenceNumber(), callerId);
                    return GroupResponse.summary(group, unread);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public GroupResponse getById(UUID callerId, UUID groupId) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        requireMember(groupId, callerId);
        List<GroupMemberResponse> members = groupMemberRepository.findByGroupId(groupId)
                .stream().map(GroupMemberResponse::from).toList();
        return GroupResponse.from(group, members);
    }

    @Transactional
    public GroupMemberResponse addMember(UUID callerId,
                                         UUID groupId,
                                         AddMemberRequest request) {
        groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        requireOwnerOrAdmin(groupId, callerId);

        UUID newMemberId = request.getUserId();

        if (!userRepository.existsById(newMemberId)) {
            throw new IllegalArgumentException("User not found: " + newMemberId);
        }

        if (!friendshipRepository.areFriends(callerId, newMemberId)) {
            throw new IllegalArgumentException(
                    "User " + newMemberId + " is not your friend — only friends can be added");
        }

        long currentMaxSequenceNumber = groupMessageRepository.maxSequenceNumber(groupId);

        try {
            GroupMember member = groupMemberRepository.save(
                    GroupMember.builder()
                            .groupId(groupId)
                            .userId(newMemberId)
                            .role(GroupMemberRole.MEMBER)
                            .lastReadSequenceNumber(currentMaxSequenceNumber)
                            .lastDeliveredSequenceNumber(currentMaxSequenceNumber)
                            .build()
            );

            return GroupMemberResponse.from(member);
        } catch (DataIntegrityViolationException e) {
            throw new IllegalArgumentException(
                    "User " + newMemberId + " is already a member of group " + groupId);
        }
    }

    @Transactional
    public void removeMember(UUID callerId, UUID groupId, UUID targetUserId) {
        groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));
        requireMember(groupId, callerId);

        GroupMember callerMembership = getMembership(groupId, callerId);
        GroupMember targetMembership = getMembership(groupId, targetUserId);

        boolean selfLeave = callerId.equals(targetUserId);

        if (selfLeave) {
            if (callerMembership.getRole() == GroupMemberRole.OWNER) {
                throw new IllegalArgumentException(
                        "Transfer ownership before leaving the group");
            }
        } else {
            GroupMemberRole callerRole = callerMembership.getRole();
            GroupMemberRole targetRole = targetMembership.getRole();

            if (callerRole == GroupMemberRole.MEMBER) {
                throw new SecurityException("Members cannot remove other members");
            }
            if (callerRole == GroupMemberRole.ADMIN && targetRole != GroupMemberRole.MEMBER) {
                throw new SecurityException(
                        "Admins can only remove members, not other admins or the owner");
            }
            if (targetRole == GroupMemberRole.OWNER) {
                throw new SecurityException("The owner cannot be removed");
            }
        }

        groupMemberRepository.deleteByGroupIdAndUserId(groupId, targetUserId);
        log.debug("Removed userId={} from groupId={} by callerId={}", targetUserId, groupId, callerId);
    }

    @Transactional
    public GroupMemberResponse updateMemberRole(UUID callerId, UUID groupId,
                                                UUID targetUserId,
                                                UpdateMemberRoleRequest request) {
        groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        groupMemberRepository.findByGroupIdForUpdate(groupId);

        GroupMember callerMembership = getMembership(groupId, callerId);
        if (callerMembership.getRole() != GroupMemberRole.OWNER) {
            throw new SecurityException("Only the group owner can perform this action");
        }

        if (callerId.equals(targetUserId)) {
            throw new IllegalArgumentException("You cannot change your own role");
        }

        GroupMember target = getMembership(groupId, targetUserId);

        if (target.getRole() == GroupMemberRole.OWNER) {
            throw new IllegalArgumentException(
                    "Cannot change the owner's role — use transfer ownership");
        }

        if (request.getRole() == GroupMemberRole.OWNER) {
            throw new IllegalArgumentException(
                    "Use the transfer ownership endpoint to change the owner");
        }

        target.setRole(request.getRole());
        GroupMember saved = groupMemberRepository.save(target);

        return GroupMemberResponse.from(saved);
    }

    @Transactional
    public GroupResponse transferOwnership(UUID callerId, UUID groupId,
                                           TransferOwnershipRequest request) {
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        groupMemberRepository.findByGroupIdForUpdate(groupId);

        UUID newOwnerId = request.getNewOwnerId();

        if (callerId.equals(newOwnerId)) {
            throw new IllegalArgumentException("You are already the owner");
        }

        GroupMember callerMembership = getMembership(groupId, callerId);
        if (callerMembership.getRole() != GroupMemberRole.OWNER) {
            throw new SecurityException("Only the group owner can perform this action");
        }

        GroupMember newOwnerMembership = getMembership(groupId, newOwnerId);

        callerMembership.setRole(GroupMemberRole.ADMIN);
        groupMemberRepository.saveAndFlush(callerMembership);

        newOwnerMembership.setRole(GroupMemberRole.OWNER);
        groupMemberRepository.save(newOwnerMembership);

        List<GroupMemberResponse> members = groupMemberRepository.findByGroupId(groupId)
                .stream()
                .map(GroupMemberResponse::from)
                .toList();

        return GroupResponse.from(group, members);
    }

    @Transactional
    public void deleteGroup(UUID callerId, UUID groupId) {
        Group group = groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found: " + groupId));

        requireOwner(groupId, callerId);

        int deletedMessages = groupMessageRepository.deleteByGroupId(groupId);
        int deletedMembers = groupMemberRepository.deleteByGroupId(groupId);

        groupRepository.delete(group);

        log.debug("Deleted groupId={} by ownerId={} deletedMessages={} deletedMembers={}",
                groupId, callerId, deletedMessages, deletedMembers);
    }

    private GroupMember getMembership(UUID groupId, UUID userId) {
        return groupMemberRepository.findByGroupIdAndUserId(groupId, userId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "User " + userId + " is not a member of group " + groupId));
    }

    private void requireMember(UUID groupId, UUID userId) {
        if (!groupMemberRepository.existsByGroupIdAndUserId(groupId, userId)) {
            throw new SecurityException("User " + userId + " is not a member of group " + groupId);
        }
    }

    private void requireOwner(UUID groupId, UUID userId) {
        if (!groupMemberRepository.existsByGroupIdAndUserIdAndRole(
                groupId, userId, GroupMemberRole.OWNER)) {
            throw new SecurityException("Only the group owner can perform this action");
        }
    }

    private void requireOwnerOrAdmin(UUID groupId, UUID userId) {
        GroupMember membership = getMembership(groupId, userId);
        if (membership.getRole() == GroupMemberRole.MEMBER) {
            throw new SecurityException("Only admins and the owner can perform this action");
        }
    }
}