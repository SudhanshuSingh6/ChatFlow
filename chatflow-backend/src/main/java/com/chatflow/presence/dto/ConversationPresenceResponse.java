package com.chatflow.presence.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConversationPresenceResponse {

    private PresenceResponse participantOne;
    private PresenceResponse participantTwo;
}