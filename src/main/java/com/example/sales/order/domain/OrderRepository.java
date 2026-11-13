package com.example.sales.order.domain;

import com.example.sales.common.api.Page;
import com.example.sales.user.domain.PostalAddress;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Persistence port for orders and their payments.
public interface OrderRepository {

    /// Inserts a `PENDING` order with its items.
    ///
    /// @return the new order id
    UUID create(UUID userId, PostalAddress shippingAddress, BigDecimal total, String currency, List<OrderItem> items);

    Optional<Order> findById(UUID id);

    /// Newest first.
    ///
    /// @param userId the owner, or `null` for the orders of all users
    Page<Order> find(UUID userId, int page, int size);

    /// Locks the order row until the transaction ends and returns its status.
    Optional<OrderStatus> lockStatus(UUID id);

    /// Sets the status and appends it to the status history.
    void changeStatus(UUID id, OrderStatus status, String reason);

    void savePayment(UUID orderId, BigDecimal amount, PaymentResult result);
}
