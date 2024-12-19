package com.lox.paymentservice.api.kafka.topics;

import com.lox.paymentservice.common.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class KafkaTopics {

    public static final String PAYMENT_EVENTS = "payment-events";

    private final KafkaConfig kafkaConfig;

    @Bean
    public NewTopic paymentEventsTopic() {
        return kafkaConfig.createTopic(PAYMENT_EVENTS, 0,
                (short) 0); // Example: 3 partitions, replication factor of 1
    }
}
