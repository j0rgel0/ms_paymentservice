package com.lox.paymentservice.api.kafka.events;

import com.lox.paymentservice.api.kafka.topics.KafkaTopics;
import lombok.Getter;

@Getter
public enum EventType {
    PAYMENT_INITIATED(KafkaTopics.NOTIFICATION_EVENTS_TOPIC),
    PAYMENT_COMPLETED(KafkaTopics.NOTIFICATION_EVENTS_TOPIC),
    PAYMENT_FAILED(KafkaTopics.NOTIFICATION_EVENTS_TOPIC),
    PAYMENT_UPDATED(KafkaTopics.NOTIFICATION_EVENTS_TOPIC),
    PAYMENT_REMOVED(KafkaTopics.NOTIFICATION_EVENTS_TOPIC),

    // (1)
    PAYMENT_SUCCESS_ORDERS(KafkaTopics.PAYMENT_STATUS_EVENTS_TOPIC),
    PAYMENT_FAILED_ORDERS(KafkaTopics.PAYMENT_STATUS_EVENTS_TOPIC);

    private final String topic;

    EventType(String topic) {
        this.topic = topic;
    }
}
