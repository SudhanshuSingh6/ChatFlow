package com.chatflow.realtime.relay;

import com.chatflow.realtime.metrics.RealtimeMetrics;
import com.chatflow.realtime.ws.RealtimeSessionRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RelaySubscriberTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final RealtimeSessionRegistry registry = mock(RealtimeSessionRegistry.class);
    private final RealtimeMetrics metrics = mock(RealtimeMetrics.class);
    private final RelaySubscriber subscriber = new RelaySubscriber(registry, metrics, mapper);

    @Test
    void deliversPayloadToTargetUser() {
        UUID target = UUID.randomUUID();
        String env = "{\"sourceInstanceId\":\"core-1\",\"targetUserId\":\"" + target
                + "\",\"payload\":{\"type\":\"MESSAGE\",\"payload\":{\"id\":\"m1\"}}}";
        Message msg = mock(Message.class);
        when(msg.getBody()).thenReturn(env.getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(msg, null);

        verify(registry).sendRaw(eq(target), contains("\"type\":\"MESSAGE\""));
        verify(metrics).relayMessage();
    }

    @Test
    void ignoresMalformedRelayMessageWithoutThrowing() {
        Message msg = mock(Message.class);
        when(msg.getBody()).thenReturn("not json".getBytes(StandardCharsets.UTF_8));

        subscriber.onMessage(msg, null); // must not throw

        verify(registry, never()).sendRaw(any(), any());
    }
}
