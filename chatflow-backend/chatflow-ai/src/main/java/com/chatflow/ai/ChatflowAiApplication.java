package com.chatflow.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the AI service (Phase 1 of the microservices migration). Today it boots
 * with only web + actuator; embeddings, vector search, RAG, summary, and the chat-completion
 * provider migrate over from core-chat in the following steps.
 */
@SpringBootApplication
public class ChatflowAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatflowAiApplication.class, args);
    }
}
