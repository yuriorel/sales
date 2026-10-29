package com.example.sales.common.error;

/// The request conflicts with the current state of a resource (HTTP 409).
public final class ConflictException extends SalesException {

    public ConflictException(String message) {
        super(message);
    }
}
