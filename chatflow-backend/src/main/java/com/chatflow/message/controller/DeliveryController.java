package com.chatflow.message.controller;

import com.chatflow.message.dto.AckRequest;
import com.chatflow.message.dto.ConversationOpenRequest;
import com.chatflow.message.dto.SeenRequest;
import com.chatflow.message.service.DeliveryService;
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
public class DeliveryController {

    private final DeliveryService deliveryService;

    @MessageMapping("/message.ack")
    public void ack(@Payload @Valid AckRequest request, Principal principal) {
        UUID receiverId = UUID.fromString(principal.getName());
        log.debug("message.ack from receiver={} messageId={}", receiverId, request.getMessageId());
        deliveryService.ack(receiverId, request);
    }

    @MessageMapping("/conversation.open")
    public void conversationOpen(@Payload @Valid ConversationOpenRequest request,
                                 Principal principal) {
        UUID receiverId = UUID.fromString(principal.getName());
        log.debug("conversation.open from receiver={} conversationId={}",
                receiverId, request.getConversationId());
        deliveryService.conversationOpen(receiverId, request);
    }

    @MessageMapping("/conversation.seen")
    public void conversationSeen(@Payload @Valid SeenRequest request,
                                 Principal principal) {
        UUID receiverId = UUID.fromString(principal.getName());
        log.debug("conversation.seen from receiver={} conversationId={} upTo={}",
                receiverId, request.getConversationId(), request.getUpToSequenceNumber());
        deliveryService.markSeen(receiverId, request);
    }
}