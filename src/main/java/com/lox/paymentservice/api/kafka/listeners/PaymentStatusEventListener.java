package com.lox.paymentservice.api.kafka.listeners;

import com.lox.paymentservice.api.kafka.events.InitiatePaymentCommand;
import com.lox.paymentservice.api.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Slf4j
@RequiredArgsConstructor
@Service
public class PaymentStatusEventListener {

    private final PaymentService paymentService;

    @KafkaListener(
            topics = "payment.commands",
            groupId = "orders-service-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void listenPaymentCommands(InitiatePaymentCommand cmd) {
        log.info("Received INITIATE_PAYMENT_COMMAND => {}", cmd);

        // Call the service to process the command
        Mono<Void> result = paymentService.handlePaymentCommands(cmd);

        // Subscribe to execute the reactive flow
        result
                .doOnSubscribe(s -> log.info("Starting payment process in PaymentService..."))
                .doOnSuccess(aVoid -> log.info("Payment process completed successfully."))
                .doOnError(error -> log.error("Error during payment process: {}", error.getMessage(), error))
                .subscribe();
    }
}
