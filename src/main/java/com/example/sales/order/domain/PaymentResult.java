package com.example.sales.order.domain;

import java.util.Map;

/// The outcome of a charge. `gatewayResponse` is stored as jsonb in `payments.gateway_response`.
public sealed interface PaymentResult {

    Map<String, Object> gatewayResponse();

    record Approved(String reference, Map<String, Object> gatewayResponse) implements PaymentResult {
    }

    record Declined(String reason, Map<String, Object> gatewayResponse) implements PaymentResult {
    }
}
