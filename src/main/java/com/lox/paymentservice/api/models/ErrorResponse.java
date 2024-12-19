package com.lox.paymentservice.api.models;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class ErrorResponse {

    private int status;
    private String message;
    private Instant timestamp;
}
