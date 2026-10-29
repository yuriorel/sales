package com.example.sales.common.error;

import java.util.List;

/// A business rule rejected the request (HTTP 400). Carries the offending fields.
public final class ValidationException extends SalesException {

    private final List<Violation> errors;

    /// Uses a flexible constructor body: the defensive copy is made before `super(...)`.
    public ValidationException(String message, List<Violation> errors) {
        this.errors = List.copyOf(errors);
        super(message);
    }

    public ValidationException(String field, String message) {
        this(message, List.of(new Violation(field, message)));
    }

    public List<Violation> errors() {
        return errors;
    }
}
