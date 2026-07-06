package com.cloudbrain.audit.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableRabbit
public class AmqpConfig {

    @Bean
    TopicExchange auditExchange(@Value("${audit.exchange:audit.events}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    Queue auditQueue(@Value("${audit.queue:audit.log.persist}") String queueName) {
        return QueueBuilder.durable(queueName).build();
    }

    @Bean
    Binding auditBinding(
            Queue auditQueue,
            TopicExchange auditExchange,
            @Value("${audit.routing-key:audit.event}") String routingKey) {
        return BindingBuilder.bind(auditQueue).to(auditExchange).with(routingKey);
    }
}
