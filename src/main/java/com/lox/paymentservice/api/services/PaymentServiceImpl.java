package com.lox.paymentservice.api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lox.paymentservice.api.exceptions.PaymentNotFoundException;
import com.lox.paymentservice.api.kafka.events.Event;
import com.lox.paymentservice.api.kafka.events.EventType;
import com.lox.paymentservice.api.kafka.events.InitiatePaymentCommand;
import com.lox.paymentservice.api.kafka.events.PaymentCompletedEvent;
import com.lox.paymentservice.api.kafka.events.PaymentFailedEvent;
import com.lox.paymentservice.api.kafka.events.PaymentInitiatedEvent;
import com.lox.paymentservice.api.kafka.events.PaymentRemovedEvent;
import com.lox.paymentservice.api.kafka.events.PaymentUpdatedEvent;
import com.lox.paymentservice.api.kafka.topics.KafkaTopics;
import com.lox.paymentservice.api.models.Payment;
import com.lox.paymentservice.api.models.PaymentStatus;
import com.lox.paymentservice.api.models.page.PaymentPage;
import com.lox.paymentservice.api.models.requests.PaymentRequest;
import com.lox.paymentservice.api.models.responses.PaymentResponse;
import com.lox.paymentservice.api.repositories.r2dbc.PaymentRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Random;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaSender<String, Object> kafkaSender;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);

    @Override
    @Transactional
    public Mono<PaymentResponse> initiatePayment(PaymentRequest paymentRequest) {
        // Create a Payment entity without setting paymentId (let the DB generate it)
        Payment payment = Payment.builder()
                .orderId(paymentRequest.getOrderId())
                .userId(paymentRequest.getUserId())
                .amount(paymentRequest.getAmount())
                .currency(paymentRequest.getCurrency())
                .paymentMethod(paymentRequest.getPaymentMethod())
                .status(PaymentStatus.INITIATED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        return paymentRepository.save(payment)
                .flatMap(savedPayment -> {
                    PaymentInitiatedEvent event = PaymentInitiatedEvent.fromPayment(savedPayment);
                    return emitEvent(event)
                            .thenReturn(paymentMapper.toPaymentResponse(savedPayment));
                })
                .onErrorResume(
                        e -> Mono.error(new RuntimeException("Failed to initiate payment", e)));
    }

    @Override
    public Mono<PaymentResponse> getPaymentById(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .map(paymentMapper::toPaymentResponse)
                .switchIfEmpty(Mono.error(
                        new PaymentNotFoundException("Payment not found with ID: " + paymentId)));
    }

    @Override
    @Transactional
    public Mono<PaymentResponse> updatePaymentStatus(UUID paymentId,
            PaymentRequest paymentRequest) {
        log.info("Starting updatePaymentStatus for paymentId: {}", paymentId);
        log.info("PaymentRequest details: {}", paymentRequest);

        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(
                        new PaymentNotFoundException("Payment not found with ID: " + paymentId)))
                .doOnNext(payment -> log.info("Found payment: {}", payment))
                .flatMap(existingPayment -> {
                    try {
                        PaymentStatus newStatus = PaymentStatus.valueOf(
                                paymentRequest.getStatus().toUpperCase());
                        log.info("Updating payment status from {} to {}",
                                existingPayment.getStatus(), newStatus);
                        existingPayment.setStatus(newStatus);
                        if (paymentRequest.getTransactionId() != null) {
                            log.info("Updating transaction ID to {}",
                                    paymentRequest.getTransactionId());
                            existingPayment.setTransactionId(paymentRequest.getTransactionId());
                        }
                        existingPayment.setUpdatedAt(Instant.now());
                        return paymentRepository.save(existingPayment);
                    } catch (IllegalArgumentException e) {
                        log.error("Invalid payment status: {}", paymentRequest.getStatus(), e);
                        return Mono.error(new RuntimeException(
                                "Invalid payment status: " + paymentRequest.getStatus(), e));
                    }
                })
                .doOnNext(updatedPayment -> log.info("Payment after update: {}", updatedPayment))
                .flatMap(updatedPayment -> {
                    PaymentUpdatedEvent event = PaymentUpdatedEvent.fromPayment(updatedPayment);
                    log.info("Emitting PaymentUpdatedEvent for paymentId: {}", paymentId);
                    return emitEvent(event)
                            .doOnSuccess(
                                    v -> log.info("Event emitted successfully for paymentId: {}",
                                            paymentId))
                            .doOnError(e -> log.error("Failed to emit event for paymentId: {}",
                                    paymentId, e))
                            .thenReturn(paymentMapper.toPaymentResponse(updatedPayment));
                })
                .doOnSuccess(response -> log.info("updatePaymentStatus completed for paymentId: {}",
                        paymentId))
                .doOnError(error -> log.error("updatePaymentStatus failed for paymentId: {}",
                        paymentId, error));
    }


    @Override
    @Transactional
    public Mono<Void> deletePayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(
                        new PaymentNotFoundException("Payment not found with ID: " + paymentId)))
                .flatMap(payment -> paymentRepository.delete(payment)
                        .then(emitEvent(PaymentRemovedEvent.fromPayment(payment))))
                .then();
    }

    @Override
    public Mono<PaymentPage> listPayments(String status, UUID userId, UUID orderId,
            Instant startDate, Instant endDate, int page, int size) {
        String dateRange = null;
        if (startDate != null && endDate != null) {
            dateRange = startDate.toString() + ":" + endDate.toString();
        }

        PageRequest pageRequest = PageRequest.of(page, size);
        return paymentRepository.findPaymentsByFilters(status, userId, orderId, dateRange,
                        pageRequest)
                .map(paymentMapper::toPaymentResponse)
                .collectList()
                .zipWith(paymentRepository.countPaymentsByFilters(status, userId, orderId,
                        dateRange))
                .map(tuple -> PaymentPage.builder()
                        .payments(tuple.getT1())
                        .totalElements(tuple.getT2())
                        .totalPages((int) Math.ceil((double) tuple.getT2() / size))
                        .currentPage(page)
                        .build());
    }

    @Override
    @Transactional
    public Mono<Void> handlePaymentCallback(String callbackPayload) {
        try {
            JsonNode jsonNode = objectMapper.readTree(callbackPayload);
            UUID paymentId = UUID.fromString(jsonNode.get("paymentId").asText());
            String status = jsonNode.get("status").asText();
            String transactionId =
                    jsonNode.has("transactionId") ? jsonNode.get("transactionId").asText() : null;
            String failureReason =
                    jsonNode.has("failureReason") ? jsonNode.get("failureReason").asText() : null;

            return paymentRepository.findById(paymentId)
                    .switchIfEmpty(Mono.error(new PaymentNotFoundException(
                            "Payment not found with ID: " + paymentId)))
                    .flatMap(payment -> {
                        payment.setStatus(PaymentStatus.valueOf(status));
                        payment.setTransactionId(transactionId);
                        payment.setUpdatedAt(Instant.now());
                        return paymentRepository.save(payment);
                    })
                    .flatMap(updatedPayment -> {
                        if ("COMPLETED".equalsIgnoreCase(status)) {
                            PaymentCompletedEvent event = PaymentCompletedEvent.fromPayment(
                                    updatedPayment);
                            return emitEvent(event);
                        } else if ("FAILED".equalsIgnoreCase(status)) {
                            PaymentFailedEvent event = PaymentFailedEvent.fromPayment(
                                    updatedPayment, failureReason);
                            return emitEvent(event);
                        }
                        return Mono.empty();
                    })
                    .then();
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Invalid callback payload", e));
        }
    }
    @Override
    public Mono<Void> handlePaymentCommands(InitiatePaymentCommand cmd) {
        log.info("== handleInitiatePayment START for orderId={}, trackId={} ==",
                cmd.getOrderId(), cmd.getTrackId());

        // Step 1: Create the initial Payment entity with default status "INITIATED"
        Payment payment = Payment.builder()
                .orderId(cmd.getOrderId())
                .trackId(cmd.getTrackId())
                .userId(cmd.getUserId())
                .amount(cmd.getTotalAmount() == null ? BigDecimal.ZERO : cmd.getTotalAmount())
                .currency(cmd.getCurrency())
                .status(PaymentStatus.INITIATED)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
        log.info("Created payment entity: {}", payment);

        // Step 2: Simulate payment logic
        PaymentSimulationResult simulation = simulatePayment();
        log.info("Simulation result: success={}, paymentMethod={}, failureReason={}",
                simulation.success, simulation.paymentMethod, simulation.failureReason);

        // Step 3: Update the Payment entity based on the simulation result
        payment.setPaymentMethod(simulation.paymentMethod);
        payment.setStatus(simulation.success ? PaymentStatus.SUCCESS : PaymentStatus.FAILED);
        payment.setTransactionId(simulation.success ? generateTransactionId() : null);
        payment.setFailureReason(simulation.success ? null : simulation.failureReason);
        payment.setUpdatedAt(Instant.now());
        log.info("Updated payment after simulation: {}", payment);

        // Step 4: Save the Payment entity to the database
        log.info("Saving payment to database...");
        return paymentRepository.save(payment)
                .flatMap(savedPayment -> {
                    if (savedPayment.getStatus() == PaymentStatus.SUCCESS) {
                        log.info("Payment successful. Emitting success events...");
                        return emitPaymentSuccessEvents(savedPayment);
                    } else {
                        log.warn("Payment failed. Emitting failure events...");
                        return emitPaymentFailedEvents(savedPayment);
                    }
                })
                .then()
                .doOnSuccess(aVoid -> log.info("handleInitiatePayment COMPLETED for orderId={}",
                        cmd.getOrderId()))
                .doOnError(error -> log.error("Error in handleInitiatePayment for orderId={}: {}",
                        cmd.getOrderId(), error.getMessage(), error));
    }


    /**
     * Randomly pick a payment method: CREDIT_CARD, PAYPAL, CASH.
     * - If PAYPAL or CASH => always success
     * - If CREDIT_CARD => 80% success, 20% fail with random reason
     */
    private PaymentSimulationResult simulatePayment() {
        log.info("Simulating payment...");

        // Define payment methods and pick one randomly
        String[] methods = {"CREDIT_CARD", "PAYPAL", "CASH"};
        String chosenMethod = methods[new Random().nextInt(methods.length)];
        log.info("Chosen payment method: {}", chosenMethod);

        // Simulate logic based on the payment method
        if (!"CREDIT_CARD".equals(chosenMethod)) {
            // Always success for PAYPAL and CASH
            log.info("Payment method '{}' is always successful.", chosenMethod);
            return new PaymentSimulationResult(true, chosenMethod, null);
        }

        // For CREDIT_CARD: 50% success, 50% failure
        int chance = new Random().nextInt(100); // Random number between 0 and 99
        log.info("Random chance for CREDIT_CARD: {}", chance);

        if (chance < 50) {
            // 50% success
            log.info("CREDIT_CARD payment succeeded.");
            return new PaymentSimulationResult(true, "CREDIT_CARD", null);
        } else {
            // 50% failure with random reasons
            String[] failureReasons = {"INSUFFICIENT_FUNDS", "CARD_ERROR", "EXPIRED_CARD"};
            String chosenFailureReason = failureReasons[new Random().nextInt(failureReasons.length)];
            log.warn("CREDIT_CARD payment failed due to: {}", chosenFailureReason);
            return new PaymentSimulationResult(false, "CREDIT_CARD", chosenFailureReason);
        }
    }

    private record PaymentSimulationResult(boolean success, String paymentMethod, String failureReason) {}

    private String generateTransactionId() {
        return "TX-" + UUID.randomUUID();
    }

    /**
     * If payment was successful, emit:
     *  (1) PAYMENT_COMPLETED => notification.events
     *  (2) PAYMENT_SUCCESS_ORDERS => payment.status.events
     */
    private Mono<Void> emitPaymentSuccessEvents(Payment savedPayment) {
        // Build the "PAYMENT_COMPLETED" event
        PaymentCompletedEvent completedEvent = PaymentCompletedEvent.fromPayment(savedPayment);

        // Also create a second event for the orders topic
        PaymentCompletedEvent successOrdersEvent = PaymentCompletedEvent.builder()
                .eventType(EventType.PAYMENT_SUCCESS_ORDERS.name())
                .paymentId(savedPayment.getPaymentId())
                .trackId(savedPayment.getTrackId())
                .status(savedPayment.getStatus().name())
                .transactionId(savedPayment.getTransactionId())
                .timestamp(Instant.now())
                .build();

        return emitEvents(completedEvent, successOrdersEvent);
    }

    /**
     * If payment failed, emit:
     *  (1) PAYMENT_FAILED => notification.events
     *  (2) PAYMENT_FAILED_ORDERS => payment.status.events
     */
    private Mono<Void> emitPaymentFailedEvents(Payment savedPayment) {
        String reason = (savedPayment.getFailureReason() != null)
                ? savedPayment.getFailureReason()
                : "Unknown failure";

        PaymentFailedEvent failedEvent = PaymentFailedEvent.fromPayment(savedPayment, reason);

        PaymentFailedEvent failedOrdersEvent = PaymentFailedEvent.builder()
                .eventType(EventType.PAYMENT_FAILED_ORDERS.name())
                .paymentId(savedPayment.getPaymentId())
                .trackId(savedPayment.getTrackId())
                .status(savedPayment.getStatus().name())
                .transactionId(savedPayment.getTransactionId())
                .message(reason)
                .timestamp(Instant.now())
                .build();

        return emitEvents(failedEvent, failedOrdersEvent);
    }

    /**
     * Generic method to emit multiple events to Kafka.
     */
    private Mono<Void> emitEvents(Object... events) {
        return reactor.core.publisher.Flux.fromArray(events)
                .flatMap(evt -> {
                    String eventType;
                    if (evt instanceof PaymentCompletedEvent pce) {
                        eventType = pce.getEventType();
                    } else if (evt instanceof PaymentFailedEvent pfe) {
                        eventType = pfe.getEventType();
                    } else {
                        eventType = null;
                    }

                    if (eventType == null) {
                        log.warn("Unknown event type for object: {}", evt);
                        return Mono.empty();
                    }

                    String topic = getTopicForEvent(eventType);
                    log.info("Emitting eventType={} to topic={}", eventType, topic);

                    SenderRecord<String, Object, String> record =
                            SenderRecord.create(
                                    new org.apache.kafka.clients.producer.ProducerRecord<>(topic, null, evt),
                                    eventType
                            );

                    // Send asynchronously
                    return kafkaSender.send(Mono.just(record))
                            .doOnNext(result -> handleKafkaSendResult(eventType, topic, result))
                            .then();
                })
                .then();
    }

    private void handleKafkaSendResult(String eventType, String topic, SenderResult<String> result) {
        if (result.exception() == null) {
            log.info("Successfully sent eventType={} to topic={} offset={}",
                    eventType, topic, result.recordMetadata().offset());
        } else {
            log.error("Failed to send eventType={} to topic={}", eventType, topic, result.exception());
        }
    }

    /**
     * Emits an event to the specified Kafka topic.
     *
     * @param event The event to emit.
     * @return A Mono signaling completion.
     */
    private Mono<Void> emitEvent(Event event) {
        String eventType = event.getEventType();
        SenderRecord<String, Object, String> record = SenderRecord.create(
                new org.apache.kafka.clients.producer.ProducerRecord<>(getTopicForEvent(eventType),
                        null, event),
                eventType
        );
        return kafkaSender.send(Mono.just(record))
                .doOnNext(SenderResult::recordMetadata)
                .then();
    }


    /**
     * Retrieves the Kafka topic based on the event type.
     *
     * @param eventType The type of the event.
     * @return The corresponding Kafka topic.
     */
    private String getTopicForEvent(String eventType) {
        for (EventType type : EventType.values()) {
            if (type.name().equals(eventType)) {
                return type.getTopic();
            }
        }
        // Default topic if event type is not found
        return KafkaTopics.NOTIFICATION_EVENTS_TOPIC;
    }
}
