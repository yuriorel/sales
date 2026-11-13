package com.example.sales.order.api;

import com.example.sales.common.api.Page;
import com.example.sales.order.domain.Order;
import com.example.sales.order.domain.OrderService;
import com.example.sales.user.domain.PostalAddress;
import com.example.sales.user.domain.Role;
import com.example.sales.user.domain.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/// Orders of the signed-in user (all orders for `ADMIN`). Placing and cancelling are
/// asynchronous: they answer `202 Accepted` and the client polls the order's status.
@RestController
@RequestMapping("/api/v1/orders")
class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderService orders;
    private final UserService users;

    OrderController(OrderService orders, UserService users) {
        this.orders = orders;
        this.users = users;
    }

    /// @param shippingAddress `null` to ship to the default address of the profile
    record PlaceOrderRequest(@Valid PostalAddress shippingAddress) {
    }

    /// Places an order for the content of the cart. The body is optional.
    @PostMapping
    ResponseEntity<Order> place(Authentication authentication, @Valid @RequestBody(required = false) PlaceOrderRequest request) {
        log.debug("POST /orders as {}: {}", authentication.getName(), request);
        var order = orders.place(users.current(authentication), request == null ? null : request.shippingAddress());
        return ResponseEntity.accepted().location(location(order.id())).body(order);
    }

    @GetMapping
    Page<Order> list(
            Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        log.debug("GET /orders as {} page={} size={}", authentication.getName(), page, size);
        return orders.list(users.current(authentication), isAdmin(authentication), page, size);
    }

    @GetMapping("/{id}")
    Order get(Authentication authentication, @PathVariable UUID id) {
        log.debug("GET /orders/{} as {}", id, authentication.getName());
        return orders.get(users.current(authentication), isAdmin(authentication), id);
    }

    @PostMapping("/{id}/cancel")
    ResponseEntity<Void> cancel(Authentication authentication, @PathVariable UUID id) {
        log.debug("POST /orders/{}/cancel as {}", id, authentication.getName());
        orders.cancel(users.current(authentication), isAdmin(authentication), id);
        return ResponseEntity.accepted().location(location(id)).build();
    }

    private static URI location(UUID id) {
        return URI.create("/api/v1/orders/" + id);
    }

    private static boolean isAdmin(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> Role.ADMIN.authority().equals(authority.getAuthority()));
    }
}
