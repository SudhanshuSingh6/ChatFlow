package com.chatflow.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.micrometer.metrics.autoconfigure.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Centralises metric name constants and applies global tags to every meter.
 *
 * All metric names are defined as constants so they can be referenced from
 * multiple classes without string duplication — a typo in a name is a
 * compile error, not a missing metric at runtime.
 */
@Configuration
public class MetricsConfig {

    // WebSocket
    public static final String WS_CONNECTIONS       = "chatflow.websocket.connections";
    public static final String WS_FRAMES_INBOUND    = "chatflow.websocket.frames.inbound";

    // Messaging
    public static final String MESSAGES_SENT        = "chatflow.messages.sent";
    public static final String MESSAGES_REPLAYED    = "chatflow.messages.replayed";
    public static final String DELIVERY_LATENCY     = "chatflow.messages.delivery.latency";

    // Cross-server relay
    public static final String RELAY_PUBLISHES      = "chatflow.relay.publishes";
    public static final String RELAY_DELIVERIES     = "chatflow.relay.deliveries";

    // Group
    public static final String GROUP_MESSAGES_SENT  = "chatflow.group.messages.sent";

    /**
     * Applies the "app" tag to every meter registered in this process.
     * Enables filtering by app name in Prometheus / Grafana when multiple
     * services write to the same metrics backend.
     */
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> commonTags() {
        return registry -> registry.config()
                .commonTags("app", "chatflow");
    }
}