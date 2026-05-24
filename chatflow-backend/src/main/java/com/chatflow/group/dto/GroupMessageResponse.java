package com.chatflow.group.dto;

import com.chatflow.group.entity.GroupMessage;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class GroupMessageResponse {

    private UUID id;
    private String clientMessageId;
    private UUID groupId;
    private UUID senderId;
    private String content;
    private Long sequenceNumber;
    private LocalDateTime createdAt;

    public static GroupMessageResponse from(GroupMessage message) {
        return GroupMessageResponse.builder()
                .id(message.getId())
                .clientMessageId(message.getClientMessageId())
                .groupId(message.getGroupId())
                .senderId(message.getSenderId())
                .content(message.getContent())
                .sequenceNumber(message.getSequenceNumber())
                .createdAt(message.getCreatedAt())
                .build();
    }
}