package com.example.sales.messaging.domain;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.time.Instant;
import java.util.UUID;

/// An asynchronous request, published to the `sales_online` topic and handled by
/// [EventDispatcher]. The JSON carries the record name in a `type` property.
@JsonTypeInfo(use = JsonTypeInfo.Id.SIMPLE_NAME, property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(OrderPlaced.class),
        @JsonSubTypes.Type(OrderCancelled.class),
        @JsonSubTypes.Type(ProductGuideChanged.class)})
public sealed interface SalesEvent permits OrderPlaced, OrderCancelled, ProductGuideChanged {

    /// Unique per event; used to skip duplicate deliveries.
    UUID eventId();

    Instant occurredAt();

    /// The id of the aggregate the event is about. It is the message key, so the
    /// events of one order or product stay in order.
    String key();
}
