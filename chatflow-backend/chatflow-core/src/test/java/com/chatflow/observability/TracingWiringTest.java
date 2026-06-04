package com.chatflow.observability;

import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.micrometer.tracing.autoconfigure.MicrometerTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.OpenTelemetryTracingAutoConfiguration;
import org.springframework.boot.micrometer.tracing.opentelemetry.autoconfigure.otlp.OtlpTracingAutoConfiguration;
import org.springframework.boot.opentelemetry.autoconfigure.OpenTelemetrySdkAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the distributed-tracing wiring (Phase 0 of the microservices migration). Boot 4
 * splits tracing autoconfig across modules and the OTel path is
 * {@code @ConditionalOnClass(OtelTracer)} — if the {@code micrometer-tracing-bridge-otel}
 * bridge is ever dropped from the pom, the Tracer/exporter silently vanish with no
 * compile error. This slice test fails fast in that case. No DB/broker required.
 */
class TracingWiringTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ObservationAutoConfiguration.class,
                    OpenTelemetrySdkAutoConfiguration.class,
                    MicrometerTracingAutoConfiguration.class,
                    OpenTelemetryTracingAutoConfiguration.class,
                    OtlpTracingAutoConfiguration.class))
            .withPropertyValues(
                    "management.tracing.sampling.probability=1.0",
                    "management.otlp.tracing.endpoint=http://localhost:4318/v1/traces");

    @Test
    void micrometerTracerBackedByOpenTelemetryIsWired() {
        runner.run(context -> {
            // A real Tracer (not the no-op) only exists if the OtelTracer bridge is on the
            // classpath and OpenTelemetryTracingAutoConfiguration activated.
            assertThat(context).hasSingleBean(Tracer.class);
            assertThat(context.getBean(Tracer.class).getClass().getName())
                    .contains("otel.bridge.OtelTracer");
        });
    }
}
