package com.example.sales.order.domain;

/// The life cycle of an order. `PENDING` until the order is processed, then
/// `CONFIRMED` or `REJECTED`. `PENDING` and `CONFIRMED` orders can be cancelled.
public enum OrderStatus {
    PENDING,
    CONFIRMED,
    REJECTED,
    CANCELLED;

    public boolean canBecome(OrderStatus next) {
        return switch (this) {
            case PENDING -> next != PENDING;
            case CONFIRMED -> next == CANCELLED;
            case REJECTED, CANCELLED -> false;
        };
    }
}
