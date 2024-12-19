// src/main/java/com/lox/paymentservice/api/models/PaymentResponse.java

package com.lox.paymentservice.api.models;

import java.util.UUID;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PaymentResponse {

    private UUID paymentId;
    private String status;
    private String transactionId;
    private String message;
}
