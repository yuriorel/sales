package com.example.sales.messaging.domain;

/// Streaming port: publishes events with the current correlation id.
public interface EventPublisher {

    /// Returns once the broker has accepted the event.
    ///
    /// @throws EventPublishingException when the broker cannot be reached in time
    void publish(SalesEvent event);
}
