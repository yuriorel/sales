package com.example.sales.order.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class OrderStatusTest {

    @ParameterizedTest
    @CsvSource({
            "PENDING, PENDING, false",
            "PENDING, CONFIRMED, true",
            "PENDING, REJECTED, true",
            "PENDING, CANCELLED, true",
            "CONFIRMED, PENDING, false",
            "CONFIRMED, REJECTED, false",
            "CONFIRMED, CANCELLED, true",
            "REJECTED, CONFIRMED, false",
            "REJECTED, CANCELLED, false",
            "CANCELLED, PENDING, false",
            "CANCELLED, CONFIRMED, false"})
    void transitions(OrderStatus from, OrderStatus to, boolean allowed) {
        assertThat(from.canBecome(to)).isEqualTo(allowed);
    }
}
