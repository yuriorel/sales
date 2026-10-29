package com.example.sales.common.error;

/// Base type of all business exceptions. The hierarchy is sealed, so the error
/// handler can map it to HTTP statuses with an exhaustive `switch`.
public sealed abstract class SalesException extends RuntimeException
        permits NotFoundException, ConflictException, ValidationException {

    protected SalesException(String message) {
        super(message);
    }
}
