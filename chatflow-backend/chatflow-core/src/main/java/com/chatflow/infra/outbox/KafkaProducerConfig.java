package com.chatflow.infra.outbox;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.Map;

/**
 * Explicit {@code KafkaTemplate<String, String>} for the outbox publisher. Spring Boot's
 * autoconfigured template is typed {@code <?, ?>} and does NOT satisfy a {@code <String,String>}
 * dependency, so {@link KafkaOutboxPublisher} would fail to wire without this. Only created when
 * the outbox actually uses Kafka. Observation is on so the trace continues across the broker.
 */
@Configuration
@ConditionalOnProperty(name = "app.outbox.transport", havingValue = "kafka")
public class KafkaProducerConfig {

    @Bean
    public KafkaTemplate<String, String> kafkaTemplate(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> props = Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        KafkaTemplate<String, String> template = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props));
        template.setObservationEnabled(true);
        return template;
    }
}
