package com.chatflow.group.dto;

import com.chatflow.group.entity.Group;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
public class GroupResponse {

    private UUID id;
    private String name;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private List<GroupMemberResponse> members;
    private Long unreadCount;

    public static GroupResponse from(Group group, List<GroupMemberResponse> members) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .createdBy(group.getCreatedBy())
                .createdAt(group.getCreatedAt())
                .members(members)
                .build();
    }

    public static GroupResponse summary(Group group, long unreadCount) {
        return GroupResponse.builder()
                .id(group.getId())
                .name(group.getName())
                .createdBy(group.getCreatedBy())
                .createdAt(group.getCreatedAt())
                .unreadCount(unreadCount)
                .build();
    }
}