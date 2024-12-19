package com.lox.paymentservice.api.kafka.events;

import com.lox.paymentservice.api.kafka.topics.KafkaTopics;
import lombok.Getter;

@Getter
public enum EventType {
    PAYMENT_INITIATED(KafkaTopics.PAYMENT_EVENTS),
    PAYMENT_COMPLETED(KafkaTopics.PAYMENT_EVENTS),
    PAYMENT_FAILED(KafkaTopics.PAYMENT_EVENTS),
    PAYMENT_UPDATED(KafkaTopics.PAYMENT_EVENTS),
    PAYMENT_REMOVED(KafkaTopics.PAYMENT_EVENTS);

    private final String topic;

    EventType(String topic) {
        this.topic = topic;
    }
}
