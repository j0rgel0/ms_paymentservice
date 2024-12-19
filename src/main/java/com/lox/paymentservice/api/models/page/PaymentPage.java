package com.lox.paymentservice.api.models.page;

import com.lox.paymentservice.api.models.responses.PaymentResponse;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentPage {

    private List<PaymentResponse> payments;
    private long totalElements;
    private int totalPages;
    private int currentPage;
}
