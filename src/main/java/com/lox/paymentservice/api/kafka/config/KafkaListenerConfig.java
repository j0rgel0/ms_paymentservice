package com.lox.paymentservice.api.kafka.config;

import com.lox.paymentservice.api.kafka.events.InitiatePaymentCommand;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

@Configuration
public class KafkaListenerConfig {

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, InitiatePaymentCommand> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, InitiatePaymentCommand> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // Optionally configure error handling, concurrency, etc.
        // e.g., factory.setCommonErrorHandler(new DefaultErrorHandler());
        return factory;
    }

    @Bean
    public DefaultKafkaConsumerFactory<String, InitiatePaymentCommand> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "orders-service-group");

        // Use the ErrorHandlingDeserializer for key and value
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ErrorHandlingDeserializer.class);

        // Tell the ErrorHandlingDeserializer which actual deserializer to delegate to
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS, StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                JsonDeserializer.class.getName());

        // Configure the JsonDeserializer
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.lox.paymentservice.api.kafka.events");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                "com.lox.paymentservice.api.kafka.events.InitiatePaymentCommand");
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        return new DefaultKafkaConsumerFactory<>(props);
    }
}
