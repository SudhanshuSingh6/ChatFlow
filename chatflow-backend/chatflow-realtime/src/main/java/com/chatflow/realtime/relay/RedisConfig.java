package com.chatflow.realtime.relay;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/** Subscribes the relay consumer to the {@code chat:relay} channel core publishes to. */
@Configuration
public class RedisConfig {

    public static final String RELAY_CHANNEL = "chat:relay";

    /** Disabled in tests (no broker) so context startup doesn't require Redis. */
    @Value("${app.relay.listener.auto-startup:true}")
    private boolean autoStartup;

    @Bean
    public RedisMessageListenerContainer relayListenerContainer(RedisConnectionFactory connectionFactory,
                                                               RelaySubscriber relaySubscriber) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(relaySubscriber, new ChannelTopic(RELAY_CHANNEL));
        container.setAutoStartup(autoStartup);
        return container;
    }
}
