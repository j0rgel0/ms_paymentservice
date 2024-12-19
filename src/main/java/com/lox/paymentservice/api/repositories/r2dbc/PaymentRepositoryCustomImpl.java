package com.lox.paymentservice.api.repositories.r2dbc;

import com.lox.paymentservice.api.models.Payment;
import org.springframework.data.domain.Pageable;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.data.relational.core.query.Criteria;
import org.springframework.data.relational.core.query.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.UUID;

@Repository
public class PaymentRepositoryCustomImpl implements PaymentRepositoryCustom {

    private final R2dbcEntityTemplate template;

    public PaymentRepositoryCustomImpl(R2dbcEntityTemplate template) {
        this.template = template;
    }

    @Override
    public Flux<Payment> findPaymentsByFilters(String status, UUID userId, UUID orderId, String dateRange, Pageable pageable) {
        // Start with empty criteria
        Criteria criteria = Criteria.empty();

        if (status != null && !status.isEmpty()) {
            criteria = criteria.and("status").is(status);
        }
        if (userId != null) {
            criteria = criteria.and("user_id").is(userId);
        }
        if (orderId != null) {
            criteria = criteria.and("order_id").is(orderId);
        }
        if (dateRange != null && !dateRange.isEmpty()) {
            String[] dates = dateRange.split(":");
            if (dates.length == 2) {
                try {
                    Instant startDate = Instant.parse(dates[0]);
                    Instant endDate = Instant.parse(dates[1]);
                    criteria = criteria.and("created_at").between(startDate, endDate);
                } catch (Exception e) {
                    // Handle parsing exceptions (e.g., log the error)
                    // For simplicity, we'll skip adding date criteria if parsing fails
                }
            }
        }

        // Build the Query object with pagination
        Query query = Query.query(criteria)
                .limit(pageable.getPageSize())
                .offset(pageable.getOffset());

        // Execute the query using R2dbcEntityTemplate
        return template.select(Payment.class)
                .matching(query)
                .all();
    }

    @Override
    public Mono<Long> countPaymentsByFilters(String status, UUID userId, UUID orderId, String dateRange) {
        // Start with empty criteria
        Criteria criteria = Criteria.empty();

        if (status != null && !status.isEmpty()) {
            criteria = criteria.and("status").is(status);
        }
        if (userId != null) {
            criteria = criteria.and("user_id").is(userId);
        }
        if (orderId != null) {
            criteria = criteria.and("order_id").is(orderId);
        }
        if (dateRange != null && !dateRange.isEmpty()) {
            String[] dates = dateRange.split(":");
            if (dates.length == 2) {
                try {
                    Instant startDate = Instant.parse(dates[0]);
                    Instant endDate = Instant.parse(dates[1]);
                    criteria = criteria.and("created_at").between(startDate, endDate);
                } catch (Exception e) {
                    // Handle parsing exceptions (e.g., log the error)
                    // For simplicity, we'll skip adding date criteria if parsing fails
                }
            }
        }

        // Build the Query object
        Query query = Query.query(criteria);

        // Execute the count query using R2dbcEntityTemplate
        return template.select(Payment.class)
                .matching(query)
                .count();
    }
}
