package com.example.sales.order.domain;

import java.math.BigDecimal;
import java.util.UUID;

/// Port to the payment provider.
public interface PaymentGateway {

    PaymentResult charge(UUID orderId, BigDecimal amount, String currency);
}
