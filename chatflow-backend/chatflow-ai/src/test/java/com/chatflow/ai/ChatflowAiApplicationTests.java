package com.chatflow.ai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import javax.sql.DataSource;

/**
 * Proves the ai-service wiring boots: every bean (Kafka consumer, ingest service, embedding
 * repository + provider) constructs together. The DataSource is mocked and Flyway/Kafka
 * startup are disabled (see test application.yaml), so no real infra is required.
 */
@SpringBootTest
class ChatflowAiApplicationTests {

    @MockitoBean
    DataSource dataSource;

    @Test
    void contextLoads() {
    }
}
