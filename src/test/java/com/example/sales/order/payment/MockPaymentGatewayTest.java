package com.example.sales.order.payment;

import com.example.sales.order.domain.PaymentResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayTest {

    final MockPaymentGateway gateway = new MockPaymentGateway(new BigDecimal("100.00"));

    @Test
    void approvesUpToTheLimit() {
        var result = gateway.charge(UUID.randomUUID(), new BigDecimal("100.00"), "USD");

        assertThat(result).isInstanceOfSatisfying(PaymentResult.Approved.class, approved -> {
            assertThat(approved.reference()).startsWith("MOCK-");
            assertThat(approved.gatewayResponse()).containsEntry("code", "APPROVED")
                    .containsEntry("reference", approved.reference());
        });
    }

    @Test
    void declinesAboveTheLimit() {
        var result = gateway.charge(UUID.randomUUID(), new BigDecimal("100.01"), "USD");

        assertThat(result).isInstanceOfSatisfying(PaymentResult.Declined.class, declined -> {
            assertThat(declined.reason()).isEqualTo("amount exceeds the limit of 100.00 USD");
            assertThat(declined.gatewayResponse()).containsEntry("code", "LIMIT_EXCEEDED");
        });
    }
}
