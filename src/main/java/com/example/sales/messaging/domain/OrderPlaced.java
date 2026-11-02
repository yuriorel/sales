package com.example.sales.messaging.domain;

import java.time.Instant;
import java.util.UUID;

/// An order was stored as `PENDING`; stock reservation and payment happen asynchronously.
public record OrderPlaced(UUID eventId, Instant occurredAt, UUID orderId) implements SalesEvent {

    public static OrderPlaced of(UUID orderId) {
        return new OrderPlaced(UUID.randomUUID(), Instant.now(), orderId);
    }

    @Override
    public String key() {
        return orderId.toString();
    }
}
