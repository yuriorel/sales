package com.example.sales.messaging.domain;

import java.time.Instant;
import java.util.UUID;

/// A product was created or updated; its user guide must be chunked and embedded again.
public record ProductGuideChanged(UUID eventId, Instant occurredAt, long productId) implements SalesEvent {

    public static ProductGuideChanged of(long productId) {
        return new ProductGuideChanged(UUID.randomUUID(), Instant.now(), productId);
    }

    @Override
    public String key() {
        return Long.toString(productId);
    }
}
