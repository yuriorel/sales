package com.example.sales.common.api;

import java.util.List;

/// One page of a list result.
///
/// @param page       zero-based page number
/// @param totalItems number of items across all pages
public record Page<T>(List<T> items, int page, int size, long totalItems, int totalPages) {

    public static <T> Page<T> of(List<T> items, int page, int size, long totalItems) {
        return new Page<>(items, page, size, totalItems, (int) ((totalItems + size - 1) / size));
    }
}
