package com.example.sales.catalog.domain;

import java.time.Instant;

public record Inventory(long productId, int quantity, Instant updatedAt) {
}
