// src/main/java/com/lox/paymentservice/api/models/PaymentRequest.java

package com.lox.paymentservice.api.models;

import lombok.Data;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.UUID;

@Data
public class PaymentRequest {
    @NotNull
    private UUID orderId;

    @NotNull
    private UUID userId;

    @NotNull
    @DecimalMin("0.0")
    private BigDecimal amount;

    @NotBlank
    @Size(min = 3, max = 3)
    private String currency;

    @NotBlank
    private String paymentMethod;
}
