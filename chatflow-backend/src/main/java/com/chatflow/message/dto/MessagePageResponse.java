package com.chatflow.message.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class MessagePageResponse {

    private List<MessageResponse> messages;

    private Long nextCursor;
}