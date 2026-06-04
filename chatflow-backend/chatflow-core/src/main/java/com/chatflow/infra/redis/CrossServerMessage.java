package com.chatflow.infra.redis;

import com.chatflow.infra.websocket.OutboundMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CrossServerMessage {

    private String sourceInstanceId;
    private UUID targetUserId;
    private OutboundMessage payload;
}