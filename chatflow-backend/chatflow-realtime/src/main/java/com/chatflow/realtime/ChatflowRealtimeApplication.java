package com.chatflow.realtime;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Realtime gateway (Phase 3). Terminates WebSocket connections, holds the session registry,
 * delivers frames off the Redis {@code chat:relay} bus, and forwards inbound commands to core.
 * Business logic, presence, and typing remain in core.
 */
@SpringBootApplication
public class ChatflowRealtimeApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatflowRealtimeApplication.class, args);
    }
}
