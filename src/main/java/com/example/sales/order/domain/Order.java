package com.example.sales.order.domain;

import com.example.sales.user.domain.PostalAddress;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/// An order, read from the `v_order_summary` view.
public record Order(
        UUID id,
        UUID userId,
        String username,
        OrderStatus status,
        BigDecimal total,
        String currency,
        PostalAddress shippingAddress,
        List<OrderItem> items,
        List<StatusChange> statusHistory,
        Instant createdAt,
        Instant updatedAt) {
}
