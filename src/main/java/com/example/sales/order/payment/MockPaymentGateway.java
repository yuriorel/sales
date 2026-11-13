package com.example.sales.order.payment;

import com.example.sales.order.domain.PaymentGateway;
import com.example.sales.order.domain.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/// Stands in for a payment provider: approves every charge up to
/// `sales.payment.decline-above` and declines larger ones.
@Component
class MockPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentGateway.class);

    private final BigDecimal declineAbove;

    MockPaymentGateway(@Value("${sales.payment.decline-above}") BigDecimal declineAbove) {
        this.declineAbove = declineAbove;
    }

    @Override
    public PaymentResult charge(UUID orderId, BigDecimal amount, String currency) {
        var response = new HashMap<String, Object>(Map.of("gateway", "mock", "orderId", orderId, "amount", amount,
                "currency", currency, "processedAt", Instant.now().toString()));
        PaymentResult result;
        if (amount.compareTo(declineAbove) > 0) {
            response.put("code", "LIMIT_EXCEEDED");
            result = new PaymentResult.Declined("amount exceeds the limit of %s %s".formatted(declineAbove, currency), response);
        } else {
            var reference = "MOCK-" + UUID.randomUUID();
            response.put("code", "APPROVED");
            response.put("reference", reference);
            result = new PaymentResult.Approved(reference, response);
        }
        log.debug("Charged {} {} for order {}: {}", amount, currency, orderId, result);
        return result;
    }
}
