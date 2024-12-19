package com.lox.paymentservice.api.repositories.r2dbc;

import com.lox.paymentservice.api.models.Payment;
import java.util.UUID;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PaymentRepository extends ReactiveCrudRepository<Payment, UUID>,
        PaymentRepositoryCustom {
}
