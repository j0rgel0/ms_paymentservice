package com.lox.paymentservice.api.models.responses;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents the response returned by the Payment Service for payment-related operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {

    /**
     * Unique identifier for the payment.
     */
    private UUID paymentId;

    /**
     * Identifier for the associated order.
     */
    private UUID orderId;

    /**
     * Identifier for the user who made the payment.
     */
    private UUID userId;

    /**
     * The amount of the payment.
     */
    private BigDecimal amount;

    /**
     * The currency in which the payment was made.
     */
    private String currency;

    /**
     * The method used for the payment (e.g., CreditCard, PayPal).
     */
    private String paymentMethod;

    /**
     * The current status of the payment (e.g., INITIATED, COMPLETED, FAILED).
     */
    private String status;

    /**
     * The transaction identifier provided by the payment gateway. This field may be null if the
     * transaction has not been completed.
     */
    private String transactionId;

    /**
     * The timestamp when the payment was created.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private Instant createdAt;

    /**
     * The timestamp when the payment was last updated.
     */
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC")
    private Instant updatedAt;
}
