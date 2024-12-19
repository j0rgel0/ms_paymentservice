package com.lox.paymentservice.api.services;

import com.lox.paymentservice.api.models.PaymentRequest;
import com.lox.paymentservice.api.models.PaymentResponse;
import com.lox.paymentservice.api.models.page.PaymentPage;
import java.time.Instant;
import java.util.UUID;
import reactor.core.publisher.Mono;

public interface PaymentService {

    Mono<PaymentResponse> initiatePayment(PaymentRequest paymentRequest);

    Mono<PaymentResponse> getPaymentById(UUID paymentId);

    Mono<PaymentResponse> updatePaymentStatus(UUID paymentId, PaymentRequest paymentRequest);

    Mono<Void> deletePayment(UUID paymentId);

    Mono<PaymentPage> listPayments(String status, UUID userId, UUID orderId, Instant startDate,
            Instant endDate, int page, int size);

    Mono<Void> handlePaymentCallback(String callbackPayload);
}
