package com.chatflow.typing.controller;

import com.chatflow.message.repository.ConversationRepository;
import com.chatflow.typing.dto.TypingEventRequest;
import com.chatflow.typing.service.TypingStateManager;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class TypingController {

    private final TypingStateManager typingStateManager;
    private final ConversationRepository conversationRepository;

    @MessageMapping("/typing")
    public void typing(@Payload @Valid TypingEventRequest request, Principal principal) {
        UUID userId = UUID.fromString(principal.getName());
        if (!conversationRepository.existsParticipant(request.getConversationId(), userId)) {
            throw new SecurityException("User " + userId
                    + " is not a participant in conversation " + request.getConversationId());
        }
        log.debug("typing from userId={} conversationId={} typing={}",
                userId, request.getConversationId(), request.getTyping());
        typingStateManager.handleTyping(
                request.getConversationId(), userId, request.getTyping());
    }
}
