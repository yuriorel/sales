package com.example.sales.common.logging;

import java.util.Optional;

/// Per-request context, bound as a [ScopedValue] for the duration of a request
/// (or a Kafka message). Structured-concurrency subtasks inherit the binding.
///
/// @param correlationId id that ties together all log lines of one request
public record RequestContext(String correlationId) {

    /// The binding for the current request.
    public static final ScopedValue<RequestContext> CURRENT = ScopedValue.newInstance();

    /// Returns the context of the current request, if one is bound.
    public static Optional<RequestContext> current() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.empty();
    }

    /// Returns the current correlation id, or `null` outside a request.
    public static String currentCorrelationId() {
        return current().map(RequestContext::correlationId).orElse(null);
    }
}
