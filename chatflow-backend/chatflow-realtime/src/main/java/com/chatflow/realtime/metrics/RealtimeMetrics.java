package com.chatflow.realtime.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Realtime counters (the gauges {@code realtime.active.sessions} / {@code realtime.connected.users}
 * are registered by the session registry, which owns the session map). All tagged
 * {@code app=chatflow-realtime} via the common Micrometer tag and scraped at /actuator/prometheus.
 */
@Component
public class RealtimeMetrics {

    private final Counter framesSent;
    private final Counter framesReceived;
    private final Counter relayMessages;

    public RealtimeMetrics(MeterRegistry registry) {
        this.framesSent = Counter.builder("realtime.frames.sent")
                .description("Frames written to client sockets").register(registry);
        this.framesReceived = Counter.builder("realtime.frames.received")
                .description("Inbound frames received from clients").register(registry);
        this.relayMessages = Counter.builder("realtime.relay.messages")
                .description("Messages consumed from the chat:relay bus").register(registry);
    }

    public void frameSent() {
        framesSent.increment();
    }

    public void frameReceived() {
        framesReceived.increment();
    }

    public void relayMessage() {
        relayMessages.increment();
    }
}
