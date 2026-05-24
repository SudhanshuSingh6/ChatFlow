package com.chatflow.group.dto;

import com.chatflow.group.entity.GroupMember;
import com.chatflow.group.entity.GroupMemberRole;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GroupMemberResponse {

    private UUID userId;
    private GroupMemberRole role;
    private LocalDateTime joinedAt;

    public static GroupMemberResponse from(GroupMember member) {
        return GroupMemberResponse.builder()
                .userId(member.getUserId())
                .role(member.getRole())
                .joinedAt(member.getJoinedAt())
                .build();
    }
}