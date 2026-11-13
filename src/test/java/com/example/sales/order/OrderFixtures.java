package com.example.sales.order;

import com.example.sales.order.domain.Order;
import com.example.sales.order.domain.OrderItem;
import com.example.sales.order.domain.OrderStatus;
import com.example.sales.user.domain.AuthProvider;
import com.example.sales.user.domain.PostalAddress;
import com.example.sales.user.domain.Role;
import com.example.sales.user.domain.User;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class OrderFixtures {

    public static final PostalAddress ADDRESS = new PostalAddress("1 Main St", null, "Springfield", "IL", "62701", "US");

    private OrderFixtures() {
    }

    public static User user(PostalAddress defaultAddress) {
        return new User(UUID.randomUUID(), "alice", "alice@example.com", AuthProvider.LOCAL, null,
                Set.of(Role.CUSTOMER), defaultAddress, Map.of(), Instant.now());
    }

    public static Order order(UUID userId, OrderStatus status, List<OrderItem> items) {
        var total = items.stream()
                .map(item -> item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Order(UUID.randomUUID(), userId, "alice", status, total, "USD", ADDRESS, items, List.of(),
                Instant.now(), Instant.now());
    }
}
