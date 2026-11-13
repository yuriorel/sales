package com.example.sales.order.domain;

import com.example.sales.cart.domain.CartItem;
import com.example.sales.cart.domain.CartService;
import com.example.sales.common.api.Page;
import com.example.sales.common.error.ConflictException;
import com.example.sales.common.error.NotFoundException;
import com.example.sales.common.error.ValidationException;
import com.example.sales.common.error.Violation;
import com.example.sales.messaging.domain.EventPublisher;
import com.example.sales.messaging.domain.OrderCancelled;
import com.example.sales.messaging.domain.OrderPlaced;
import com.example.sales.user.domain.PostalAddress;
import com.example.sales.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/// Placing, reading and cancelling orders. Placing and cancelling only record the
/// request and publish an event; [OrderProcessor] does the work asynchronously.
///
/// A customer sees only their own orders; an `ADMIN` sees all of them. Someone
/// else's order is reported as not found, so its existence is not revealed.
@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orders;
    private final CartService carts;
    private final EventPublisher events;

    public OrderService(OrderRepository orders, CartService carts, EventPublisher events) {
        this.orders = orders;
        this.carts = carts;
        this.events = events;
    }

    /// Turns the cart into a `PENDING` order with a price snapshot and empties the cart.
    ///
    /// The event is published inside the transaction: when the broker is down, the
    /// order is rolled back and the cart kept. The listener may see the event before
    /// the commit; it then finds no order and the delivery is retried.
    ///
    /// @param shippingAddress `null` to ship to the user's default address
    @Transactional
    public Order place(User user, PostalAddress shippingAddress) {
        log.debug("Placing order for {} shippingAddress={}", user.username(), shippingAddress);
        var address = shippingAddress != null ? shippingAddress : user.defaultAddress();
        if (address == null) {
            throw new ValidationException("A shipping address is required",
                    List.of(new Violation("shippingAddress", "is required when the profile has no default address")));
        }
        var cart = carts.get(user);
        if (cart.items().isEmpty()) {
            throw new ValidationException("The cart is empty", List.of(new Violation("cart", "must not be empty")));
        }
        var currencies = cart.items().stream().map(CartItem::currency).distinct().toList();
        if (currencies.size() > 1) {
            throw new ValidationException("The cart mixes currencies",
                    List.of(new Violation("cart", "must not mix currencies " + currencies)));
        }
        var items = cart.items().stream()
                .map(item -> new OrderItem(item.productId(), item.productName(), item.unitPrice(), item.currency(), item.quantity()))
                .toList();
        var id = orders.create(user.id(), address, cart.total(), currencies.getFirst(), items);
        carts.clear(user);
        events.publish(OrderPlaced.of(id));
        var order = orders.findById(id).orElseThrow();
        log.debug("Placed {}", order);
        return order;
    }

    public Order get(User user, boolean admin, UUID id) {
        log.debug("Reading order {} for {} admin={}", id, user.username(), admin);
        return orders.findById(id)
                .filter(order -> admin || order.userId().equals(user.id()))
                .orElseThrow(() -> new NotFoundException("Order", id));
    }

    public Page<Order> list(User user, boolean admin, int page, int size) {
        log.debug("Listing orders for {} admin={} page={} size={}", user.username(), admin, page, size);
        return orders.find(admin ? null : user.id(), page, size);
    }

    /// Requests the cancellation; stock of a confirmed order is released asynchronously.
    public void cancel(User user, boolean admin, UUID id) {
        var order = get(user, admin, id);
        if (!order.status().canBecome(OrderStatus.CANCELLED)) {
            throw new ConflictException("Order %s is %s and cannot be cancelled".formatted(id, order.status()));
        }
        log.debug("Requesting cancellation of {}", order);
        events.publish(OrderCancelled.of(id, "Cancelled by " + user.username()));
    }
}
