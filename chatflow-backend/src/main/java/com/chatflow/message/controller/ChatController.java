package com.chatflow.message.controller;

import com.chatflow.message.dto.MessageResponse;
import com.chatflow.message.dto.SendMessageRequest;
import com.chatflow.message.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.UUID;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @MessageMapping("/chat.send")
    @SendToUser("/queue/messages.ack")
    public MessageResponse sendMessage(
            @Payload SendMessageRequest request,
            Principal principal) {
              UUID senderId = resolveSenderId(principal, request);

        log.debug("chat.send from sender={} clientMessageId={}",
                senderId, request.getClientMessageId());

        return chatService.sendMessage(senderId, request);
    }

    private UUID resolveSenderId(Principal principal, SendMessageRequest request) {
        if (principal != null) {
            try {
                return UUID.fromString(principal.getName());
            } catch (IllegalArgumentException ignored) { }
        }
        throw new IllegalStateException(
                "No authenticated principal — implement JWT handshake");
    }
}