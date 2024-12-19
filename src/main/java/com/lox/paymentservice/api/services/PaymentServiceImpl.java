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
import com.lox.paymentservice.api.models.PaymentRequest;
import com.lox.paymentservice.api.models.PaymentResponse;
import com.lox.paymentservice.api.models.PaymentStatus;
import com.lox.paymentservice.api.models.page.PaymentPage;
import com.lox.paymentservice.api.repositories.r2dbc.PaymentRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.mapstruct.factory.Mappers;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderRecord;
import reactor.kafka.sender.SenderResult;

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
        return paymentRepository.findById(paymentId)
                .switchIfEmpty(Mono.error(
                        new PaymentNotFoundException("Payment not found with ID: " + paymentId)))
                .flatMap(existingPayment -> {
                    // Update payment status and transaction ID based on request
                    existingPayment.setStatus(
                            PaymentStatus.valueOf(paymentRequest.getPaymentMethod().toUpperCase()));
                    existingPayment.setTransactionId(paymentRequest.getPaymentMethod());
                    existingPayment.setUpdatedAt(Instant.now());
                    return paymentRepository.save(existingPayment);
                })
                .flatMap(updatedPayment -> {
                    PaymentUpdatedEvent event = PaymentUpdatedEvent.fromPayment(updatedPayment);
                    return emitEvent(event)
                            .thenReturn(paymentMapper.toPaymentResponse(updatedPayment));
                });
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
