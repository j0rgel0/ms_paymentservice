package com.lox.paymentservice.api.kafka.topics;

import com.lox.paymentservice.common.kafka.KafkaConfig;
import lombok.RequiredArgsConstructor;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class KafkaTopics {

    public static final String NOTIFICATION_EVENTS_TOPIC = "notification.events";
    public static final String PAYMENT_STATUS_EVENTS_TOPIC = "payment.status.events";

    private final KafkaConfig kafkaConfig;

    @Bean
    public NewTopic notificationsEventsTopic() {
        return kafkaConfig.createTopic(NOTIFICATION_EVENTS_TOPIC, 3, (short) 1);
    }

    @Bean
    public NewTopic paymentStatusEventsTopic() {
        return kafkaConfig.createTopic(PAYMENT_STATUS_EVENTS_TOPIC, 3, (short) 1);
    }

}
