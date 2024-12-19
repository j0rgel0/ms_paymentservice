package com.lox.paymentservice.api.repositories.r2dbc;

import com.lox.paymentservice.api.models.Payment;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.UUID;

public interface PaymentRepositoryCustom {
    /**
     * Finds payments based on provided filters.
     *
     * @param status    Payment status filter.
     * @param userId    User ID filter.
     * @param orderId   Order ID filter.
     * @param dateRange Date range filter in the format "start:end".
     * @param pageable  Pagination information.
     * @return A Flux of Payment entities matching the criteria.
     */
    Flux<Payment> findPaymentsByFilters(String status, UUID userId, UUID orderId, String dateRange, Pageable pageable);

    /**
     * Counts the number of payments based on provided filters.
     *
     * @param status    Payment status filter.
     * @param userId    User ID filter.
     * @param orderId   Payment order ID filter.
     * @param dateRange Date range filter in the format "start:end".
     * @return A Mono containing the count of matching payments.
     */
    Mono<Long> countPaymentsByFilters(String status, UUID userId, UUID orderId, String dateRange);
}
