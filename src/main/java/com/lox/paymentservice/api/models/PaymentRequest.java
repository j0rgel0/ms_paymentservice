package com.lox.paymentservice.api.models;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.Data;

@Data
public class PaymentRequest {

    @NotNull(groups = {Initiate.class})
    private UUID orderId;

    @NotNull(groups = {Initiate.class})
    private UUID userId;

    @NotNull(groups = {Initiate.class})
    @DecimalMin(value = "0.0", inclusive = false, groups = {Initiate.class})
    private BigDecimal amount;

    @NotBlank(groups = {Initiate.class})
    private String currency;

    @NotBlank(groups = {Initiate.class})
    private String paymentMethod;

    // New fields for updating payment status
    @NotBlank(groups = {Update.class})
    private String status;

    private String transactionId;

    // Validation groups to differentiate between initiation and update
    public interface Initiate {

    }

    public interface Update {

    }
}
