// src/main/java/com/lox/paymentservice/api/exceptions/InsufficientFundsException.java

package com.lox.paymentservice.api.exceptions;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(String message) {
        super(message);
    }
}
