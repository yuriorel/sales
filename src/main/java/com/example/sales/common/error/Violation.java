package com.example.sales.common.error;

/// One rejected field, reported in the `errors` property of a problem response.
public record Violation(String field, String message) {
}
