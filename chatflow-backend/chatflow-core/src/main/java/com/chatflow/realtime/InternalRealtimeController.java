package com.chatflow.realtime;

import com.chatflow.conversation.service.ReplayService;
import com.chatflow.infra.websocket.InboundMessage;
import com.chatflow.presence.service.PresenceService;
import com.chatflow.typing.service.TypingStateManager;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Service-to-service endpoints the realtime gateway calls. Connection lifecycle drives presence +
 * replay; inbound commands are dispatched through the same {@link RealtimeInboundService} the
 * embedded handler uses. Guarded by the shared {@code X-Internal-Token} (path permitted in
 * SecurityConfig). Errors propagate as 400/403 so the gateway can build an ERROR frame.
 */
@RestController
@RequestMapping("/internal/realtime")
@RequiredArgsConstructor
public class InternalRealtimeController {

    private final PresenceService presenceService;
    private final ReplayService replayService;
    private final TypingStateManager typingStateManager;
    private final RealtimeInboundService inboundService;

    @Value("${app.internal.token:dev-internal-token}")
    private String internalToken;

    /** First WS session for a user → mark online + replay their undelivered messages. */
    @PostMapping("/connect")
    public void connect(@RequestBody RealtimeUser cmd,
                        @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requireInternalToken(token);
        presenceService.userConnected(cmd.userId());
        replayService.replayForUser(cmd.userId());
    }

    /** Last WS session gone → mark offline + clear typing. */
    @PostMapping("/disconnect")
    public void disconnect(@RequestBody RealtimeUser cmd,
                           @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requireInternalToken(token);
        presenceService.userDisconnected(cmd.userId());
        typingStateManager.clearAllForUser(cmd.userId());
    }

    /** An inbound client command forwarded by the gateway. */
    @PostMapping("/inbound")
    public void inbound(@RequestBody RealtimeInboundCommand cmd,
                        @RequestHeader(value = "X-Internal-Token", required = false) String token) {
        requireInternalToken(token);
        InboundMessage.Type type = InboundMessage.Type.valueOf(cmd.type());
        inboundService.dispatch(cmd.userId(), type, cmd.payload(), cmd.requestId());
    }

    private void requireInternalToken(String token) {
        if (token == null || !token.equals(internalToken)) {
            throw new SecurityException("Invalid internal token");
        }
    }

    public record RealtimeUser(UUID userId) {
    }

    public record RealtimeInboundCommand(UUID userId, String type, JsonNode payload, String requestId) {
    }
}
