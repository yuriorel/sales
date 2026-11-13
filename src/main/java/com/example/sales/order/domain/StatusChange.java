package com.example.sales.order.domain;

import java.time.Instant;

/// One entry of the `orders.status_history` jsonb array.
public record StatusChange(OrderStatus status, Instant at, String reason) {
}
