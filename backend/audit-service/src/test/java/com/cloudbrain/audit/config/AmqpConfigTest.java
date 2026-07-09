package com.cloudbrain.audit.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

class AmqpConfigTest {
    private final AmqpConfig config = new AmqpConfig();

    @Test
    void beansUseConfiguredNamesAndRoutingKey() {
        TopicExchange exchange = config.auditExchange("audit.events");
        Queue queue = config.auditQueue("audit.log.persist");
        Binding binding = config.auditBinding(queue, exchange, "audit.event");

        assertThat(exchange.getName()).isEqualTo("audit.events");
        assertThat(queue.getName()).isEqualTo("audit.log.persist");
        assertThat(binding.getExchange()).isEqualTo("audit.events");
        assertThat(binding.getDestination()).isEqualTo("audit.log.persist");
        assertThat(binding.getRoutingKey()).isEqualTo("audit.event");
    }
}
