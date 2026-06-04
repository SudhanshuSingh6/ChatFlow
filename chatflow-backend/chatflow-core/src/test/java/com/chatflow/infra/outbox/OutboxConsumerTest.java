package com.chatflow.infra.outbox;

import com.chatflow.infra.idempotency.IdempotencyGuard;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class OutboxConsumerTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final OutboxDispatcher dispatcher = mock(OutboxDispatcher.class);
    private final IdempotencyGuard guard = mock(IdempotencyGuard.class);
    private final OutboxConsumer consumer = new OutboxConsumer(dispatcher, mapper, guard);

    {
        ReflectionTestUtils.setField(consumer, "consumerGroup", "chatflow-outbox");
    }

    private String message(UUID id) {
        return mapper.writeValueAsString(Map.of(
                "id", id.toString(),
                "aggregateType", "conversation",
                "aggregateId", UUID.randomUUID().toString(),
                "eventType", "message.created",
                "payload", "{}"));
    }

    @Test
    void dispatchesOnFirstDelivery() {
        UUID id = UUID.randomUUID();
        when(guard.firstTime("chatflow-outbox", id)).thenReturn(true);

        consumer.consume(message(id));

        verify(dispatcher).dispatch(any());
    }

    @Test
    void skipsDuplicateDelivery() {
        UUID id = UUID.randomUUID();
        when(guard.firstTime("chatflow-outbox", id)).thenReturn(false);

        consumer.consume(message(id));

        verify(dispatcher, never()).dispatch(any());
    }
}
