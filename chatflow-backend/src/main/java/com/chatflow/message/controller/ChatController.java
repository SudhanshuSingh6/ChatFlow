package com.chatflow.message.controller;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class ChatController {

    @MessageMapping("/chat.test")
    @SendTo("/topic/test")
    public String test(String message) {
        return "Echo: " + message;
    }
}