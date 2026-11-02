package com.example.sales.messaging.domain;

/// Persistence port for idempotent event handling.
public interface ProcessedEventRepository {

    /// Records the event as processed.
    ///
    /// @return `false` when it was already recorded, so this delivery is a duplicate
    boolean markProcessed(SalesEvent event);
}
