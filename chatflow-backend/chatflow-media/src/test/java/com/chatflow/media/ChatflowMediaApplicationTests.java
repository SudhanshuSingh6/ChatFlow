package com.chatflow.media;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Proves the media worker boots: storage (local profile), thumbnail service, and the Kafka
 * consumer wire up. No broker/object-store needed (Kafka listeners don't auto-start in tests).
 */
@SpringBootTest
class ChatflowMediaApplicationTests {

    @Test
    void contextLoads() {
    }
}
