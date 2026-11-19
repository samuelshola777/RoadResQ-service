package com.roadresq.roadresq.rescueRequest.dispatch;

import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RescueDispatchConfig {

    static final String QUEUE = "roadresq.rescue.dispatch";

    @Bean
    public Queue rescueDispatchQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }
}