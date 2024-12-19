package com.lox.paymentservice.api.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lox.paymentservice.api.exceptions.PaymentNotFoundException;
import com.lox.paymentservice.api.kafka.events.Event;
import com.lox.paymentservice.api.kafka.events.EventType;
import com.lox.paymentservice.api.kafka.events.PaymentCompletedEvent;
import com.lox.paymentservice.api.kafka.events.PaymentFailedEvent;
import com.lox.paymentservice.api.kafka.events.PaymentInitiatedEvent;
import com.lox.paymentservice.api.kafka.events.PaymentRemovedEvent;
import com.lox.paymentservice.api.kafka.events.PaymentUpdatedEvent;
import com.lox.paymentservice.api.kafka.topics.KafkaTopics;
import com.lox.paymentservice.api.models.Payment;
import com.lox.paymentservice.api.models.requests.PaymentRequest;
import com.lox.paymentservice.api.models.responses.PaymentResponse;
import com.lox.paymentservice.api.models.PaymentStatus;
import com.lox.paymentservice.api.models.page.PaymentPage;
import com.lox.paymentservice.api.repositories.r2dbc.PaymentRepository;
import java.time.Instant;
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
        return KafkaTopics.PAYMENT_EVENTS;
    }
}
