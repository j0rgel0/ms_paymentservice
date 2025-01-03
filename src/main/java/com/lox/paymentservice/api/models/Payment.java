package com.lox.paymentservice.api.models;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("payments")
public class Payment {

    @Id
    @Column("payment_id")
    private UUID paymentId; // auto-generated by Postgres if you use uuid_generate_v4()

    @Column("order_id")
    private UUID orderId;

    @Column("track_id")
    private UUID trackId;

    @Column("user_id")
    private UUID userId;

    @Column("amount")
    private BigDecimal amount;

    @Column("currency")
    private String currency;

    @Column("payment_method")
    private String paymentMethod; // e.g., "CREDIT_CARD", "PAYPAL", "CASH"

    @Column("status")
    private PaymentStatus status; // e.g., SUCCESS / FAILED / INITIATED

    @Column("transaction_id")
    private String transactionId; // random "TX-..."

    @Column("failure_reason")
    private String failureReason;

    @Column("created_at")
    private Instant createdAt;

    @Column("updated_at")
    private Instant updatedAt;
}
