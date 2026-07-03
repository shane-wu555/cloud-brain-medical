package com.cloudbrain.auth.config;

import com.cloudbrain.auth.entity.UserAccount;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, UserAccount> userAccountRedisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, UserAccount> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new JdkSerializationRedisSerializer());
        template.setHashValueSerializer(new JdkSerializationRedisSerializer());
        template.afterPropertiesSet();
        return template;
    }
}
