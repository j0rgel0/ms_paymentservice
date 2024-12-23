package com.lox.paymentservice.api.models;

public enum PaymentStatus {
    INITIATED,   // initial state
    SUCCESS,     // payment success
    FAILED       // payment failure
}
