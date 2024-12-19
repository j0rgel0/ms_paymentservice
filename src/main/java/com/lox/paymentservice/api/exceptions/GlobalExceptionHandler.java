package com.lox.paymentservice.api.exceptions;

import java.util.HashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(PaymentNotFoundException.class)
    public Mono<Void> handlePaymentNotFoundException(ServerWebExchange exchange,
            PaymentNotFoundException ex) {
        exchange.getResponse().setStatusCode(HttpStatus.NOT_FOUND);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> response = new HashMap<>();
        response.put("error", ex.getMessage());
        byte[] bytes = new byte[0];
        try {
            bytes = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(response);
        } catch (Exception e) {
            // Handle exception
        }
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }

    @ExceptionHandler(Exception.class)
    public Mono<Void> handleGenericException(ServerWebExchange exchange, Exception ex) {
        exchange.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        Map<String, String> response = new HashMap<>();
        response.put("error", "An unexpected error occurred.");
        byte[] bytes = new byte[0];
        try {
            bytes = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(response);
        } catch (Exception e) {
            // Handle exception
        }
        return exchange.getResponse()
                .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}
