// src/main/java/com/lox/paymentservice/api/exceptions/PaymentNotFoundException.java

package com.lox.paymentservice.api.exceptions;

public class PaymentNotFoundException extends RuntimeException {
    public PaymentNotFoundException(String message) {
        super(message);
    }
}