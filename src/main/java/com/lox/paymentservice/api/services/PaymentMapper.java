package com.lox.paymentservice.api.services;

import com.lox.paymentservice.api.models.Payment;
import com.lox.paymentservice.api.models.responses.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper
public interface PaymentMapper {

    @Mapping(source = "status", target = "status")
    PaymentResponse toPaymentResponse(Payment payment);
}
