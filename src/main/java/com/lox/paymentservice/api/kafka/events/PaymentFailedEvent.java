package com.lox.paymentservice.api.kafka.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.lox.paymentservice.api.models.Payment;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentFailedEvent implements Event {

    private String eventType;
    private UUID paymentId;
    private String status;
    private String transactionId;
    private String message;
    private Instant timestamp;

    @Override
    public String getEventType() {
        return eventType;
    }

    @Override
    public UUID getPaymentId() {
        return paymentId;
    }

    @Override
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    public Instant getTimestamp() {
        return timestamp;
    }

    /**
     * Static method to build the event from a Payment object.
     *
     * @param payment The Payment entity.
     * @param message The failure reason.
     * @return A PaymentFailedEvent instance.
     */
    public static PaymentFailedEvent fromPayment(Payment payment, String message) {
        return PaymentFailedEvent.builder()
                .eventType(EventType.PAYMENT_FAILED.name())
                .paymentId(payment.getPaymentId())
                .status(payment.getStatus().name())
                .transactionId(payment.getTransactionId())
                .message(message)
                .timestamp(Instant.now())
                .build();
    }
}
