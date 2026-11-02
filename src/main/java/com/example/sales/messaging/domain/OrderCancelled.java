package com.example.sales.messaging.domain;

import java.time.Instant;
import java.util.UUID;

/// Cancellation of an order was requested; reserved stock is released asynchronously.
public record OrderCancelled(UUID eventId, Instant occurredAt, UUID orderId, String reason) implements SalesEvent {

    public static OrderCancelled of(UUID orderId, String reason) {
        return new OrderCancelled(UUID.randomUUID(), Instant.now(), orderId, reason);
    }

    @Override
    public String key() {
        return orderId.toString();
    }
}
