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
public class PaymentCompletedEvent implements Event {

    private String eventType;
    private UUID paymentId;
    private String status;
    private String transactionId;
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
     * @return A PaymentCompletedEvent instance.
     */
    public static PaymentCompletedEvent fromPayment(Payment payment) {
        return PaymentCompletedEvent.builder()
                .eventType(EventType.PAYMENT_COMPLETED.name())
                .paymentId(payment.getPaymentId())
                .status(payment.getStatus().name())
                .transactionId(payment.getTransactionId())
                .timestamp(Instant.now())
                .build();
    }
}
