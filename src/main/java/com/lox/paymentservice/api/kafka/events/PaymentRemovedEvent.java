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
public class PaymentRemovedEvent implements Event {

    private String eventType;
    private UUID paymentId;
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
     * @return A PaymentRemovedEvent instance.
     */
    public static PaymentRemovedEvent fromPayment(Payment payment) {
        return PaymentRemovedEvent.builder()
                .eventType(EventType.PAYMENT_REMOVED.name())
                .paymentId(payment.getPaymentId())
                .timestamp(Instant.now())
                .build();
    }
}
