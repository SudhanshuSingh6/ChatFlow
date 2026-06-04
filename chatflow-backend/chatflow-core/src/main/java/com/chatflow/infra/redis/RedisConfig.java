package com.chatflow.infra.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new StringRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }

    // Only core consumes chat:relay in embedded mode. In external mode the realtime gateway is the
    // sole consumer; core just publishes (StringRedisTemplate + CrossServerRelay stay available).
    @Bean
    @ConditionalOnProperty(name = "app.realtime.mode", havingValue = "embedded", matchIfMissing = true)
    public RedisMessageListenerContainer redisListenerContainer(
            RedisConnectionFactory factory,
            CrossServerRelay crossServerRelay) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(factory);
        container.addMessageListener(
                crossServerRelay,
                new ChannelTopic(CrossServerRelay.CHANNEL)
        );

        return container;
    }
}