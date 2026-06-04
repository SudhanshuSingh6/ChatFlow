package com.chatflow.realtime.ws;

import com.chatflow.realtime.client.CommandRejectedException;
import com.chatflow.realtime.client.CoreCommandClient;
import com.chatflow.realtime.metrics.RealtimeMetrics;
import com.chatflow.realtime.security.JwtHandshakeInterceptor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RealtimeWebSocketHandlerTest {

    private final ObjectMapper mapper = JsonMapper.builder().findAndAddModules().build();
    private final RealtimeSessionRegistry registry = mock(RealtimeSessionRegistry.class);
    private final CoreCommandClient core = mock(CoreCommandClient.class);
    private final RealtimeMetrics metrics = mock(RealtimeMetrics.class);
    private final RealtimeWebSocketHandler handler =
            new RealtimeWebSocketHandler(registry, core, metrics, mapper);

    private final UUID userId = UUID.randomUUID();

    private WebSocketSession session() {
        WebSocketSession s = mock(WebSocketSession.class);
        when(s.getAttributes()).thenReturn(Map.of(JwtHandshakeInterceptor.USER_ID_ATTR, userId));
        when(s.isOpen()).thenReturn(true);
        return s;
    }

    private String lastSent(WebSocketSession s) throws Exception {
        ArgumentCaptor<WebSocketMessage<?>> cap = ArgumentCaptor.forClass(WebSocketMessage.class);
        verify(s, atLeastOnce()).sendMessage(cap.capture());
        return cap.getValue().getPayload().toString();
    }

    @Test
    void forwardsInboundCommandToCore() {
        handler.handleTextMessage(session(),
                new TextMessage("{\"type\":\"SEND_MESSAGE\",\"requestId\":\"r1\",\"payload\":{\"x\":1}}"));
        verify(core).inbound(eq(userId), eq("SEND_MESSAGE"), any(), eq("r1"));
        verify(metrics).frameReceived();
    }

    @Test
    void answersPingLocallyWithoutCallingCore() throws Exception {
        WebSocketSession s = session();
        handler.handleTextMessage(s, new TextMessage("{\"type\":\"PING\",\"requestId\":\"p1\"}"));
        assertThat(lastSent(s)).contains("PONG");
        verifyNoInteractions(core);
    }

    @Test
    void rejectedCommandBecomesErrorFrame() throws Exception {
        doThrow(new CommandRejectedException("bad input")).when(core).inbound(any(), any(), any(), any());
        WebSocketSession s = session();
        handler.handleTextMessage(s,
                new TextMessage("{\"type\":\"SEND_MESSAGE\",\"requestId\":\"r2\",\"payload\":{}}"));
        String sent = lastSent(s);
        assertThat(sent).contains("ERROR");
        assertThat(sent).contains("bad input");
    }
}
