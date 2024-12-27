package com.lox.paymentservice.api.controllers;

import com.lox.paymentservice.api.models.Payment;
import com.lox.paymentservice.api.models.requests.PaymentRequest;
import com.lox.paymentservice.api.models.responses.PaymentResponse;
import com.lox.paymentservice.api.models.page.PaymentPage;
import com.lox.paymentservice.api.services.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Validated
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Initiates a new payment transaction.
     *
     * @param paymentRequest The payment initiation request payload.
     * @return A Mono emitting the PaymentResponse.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<PaymentResponse> initiatePayment(
            @Valid @RequestBody PaymentRequest paymentRequest) {
        return paymentService.initiatePayment(paymentRequest);
    }

    /**
     * Retrieves detailed information about a specific payment transaction.
     *
     * @param paymentId The ID of the payment to retrieve.
     * @return A Mono emitting the PaymentResponse.
     */
    @GetMapping("/{paymentId}")
    public Mono<PaymentResponse> getPaymentById(@PathVariable UUID paymentId) {
        return paymentService.getPaymentById(paymentId);
    }

    /**
     * Updates the status of an existing payment transaction.
     *
     * @param paymentId      The ID of the payment to update.
     * @param paymentRequest The payment update request payload.
     * @return A Mono emitting the updated PaymentResponse.
     */
    @PutMapping("/{paymentId}")
    public Mono<PaymentResponse> updatePaymentStatus(
            @PathVariable UUID paymentId,
            @Validated(PaymentRequest.Update.class) @RequestBody PaymentRequest paymentRequest) {
        log.info("Received request to update payment with ID: {}", paymentId);
        return paymentService.updatePaymentStatus(paymentId, paymentRequest)
                .doOnSuccess(
                        response -> log.info("Successfully updated payment with ID: {}", paymentId))
                .doOnError(
                        error -> log.error("Error updating payment with ID: {}", paymentId, error));
    }

    /**
     * Deletes a payment transaction from the system.
     *
     * @param paymentId The ID of the payment to delete.
     * @return A Mono signaling completion.
     */
    @DeleteMapping("/{paymentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deletePayment(@PathVariable UUID paymentId) {
        return paymentService.deletePayment(paymentId);
    }

    /**
     * Retrieves a paginated list of all payment transactions based on optional filters.
     *
     * @param status    Optional payment status filter.
     * @param userId    Optional user ID filter.
     * @param orderId   Optional order ID filter.
     * @param startDate Optional start date for filtering.
     * @param endDate   Optional end date for filtering.
     * @param page      Page number for pagination.
     * @param size      Page size for pagination.
     * @return A Mono emitting the PaymentPage containing payment responses.
     */
    @GetMapping
    public Mono<PaymentPage> listPayments(@RequestParam(required = false) String status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) Instant startDate,
            @RequestParam(required = false) Instant endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return paymentService.listPayments(status, userId, orderId, startDate, endDate, page, size);
    }

    /**
     * Handles payment gateway callbacks (e.g., webhooks) to update payment statuses.
     *
     * @param callbackPayload The callback payload from the payment gateway.
     * @return A Mono signaling completion.
     */
    @PostMapping("/callback")
    @ResponseStatus(HttpStatus.OK)
    public Mono<Void> handlePaymentCallback(@RequestBody String callbackPayload) {
        return paymentService.handlePaymentCallback(callbackPayload);
    }

    @GetMapping("/searchByTrackId")
    public Flux<Payment> listPaymentsByTrackId(@RequestParam(required = false) UUID trackId) {
        if (trackId != null) {
            return paymentService.listPaymentsByTrackId(trackId);
        } else {
            // If no trackId param is provided, return all payments
            return paymentService.listPaymentsByTrackId(null)
                    .switchIfEmpty(paymentService.listPaymentsByTrackId(null));
        }
    }
}
