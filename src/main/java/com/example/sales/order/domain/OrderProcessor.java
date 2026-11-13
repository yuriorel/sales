package com.example.sales.order.domain;

import com.example.sales.catalog.domain.InventoryRepository;
import com.example.sales.catalog.domain.ProductService;
import com.example.sales.common.cache.CacheAside;
import com.example.sales.common.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/// Handles order events inside the transaction of the event dispatcher, so the order
/// row and the inventory rows stay locked until the outcome is committed.
///
/// An unknown order fails the delivery, which is retried: the event can arrive before
/// the transaction that placed the order has committed.
@Service
public class OrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessor.class);

    private final OrderRepository orders;
    private final InventoryRepository inventory;
    private final PaymentGateway payments;
    private final CacheAside cache;

    public OrderProcessor(OrderRepository orders, InventoryRepository inventory, PaymentGateway payments, CacheAside cache) {
        this.orders = orders;
        this.inventory = inventory;
        this.payments = payments;
        this.cache = cache;
    }

    /// Reserves the stock of every item and charges the total. The order becomes
    /// `CONFIRMED`, or `REJECTED` when stock is short or the payment is declined.
    public void process(UUID orderId) {
        var status = lockStatus(orderId);
        if (status != OrderStatus.PENDING) {
            log.debug("Order {} is {}, nothing to process", orderId, status);
            return;
        }
        var order = orders.findById(orderId).orElseThrow();
        var stock = inventory.lock(order.items().stream().map(OrderItem::productId).toList());
        var shortages = order.items().stream()
                .filter(item -> stock.getOrDefault(item.productId(), 0) < item.quantity())
                .map(item -> "%s (%d requested, %d in stock)".formatted(
                        item.productName(), item.quantity(), stock.getOrDefault(item.productId(), 0)))
                .toList();
        if (!shortages.isEmpty()) {
            log.debug("Order {} rejected, insufficient stock: {}", orderId, shortages);
            orders.changeStatus(orderId, OrderStatus.REJECTED, "Insufficient stock: " + String.join(", ", shortages));
            return;
        }

        var payment = payments.charge(orderId, order.total(), order.currency());
        orders.savePayment(orderId, order.total(), payment);
        switch (payment) {
            case PaymentResult.Approved(var reference, _) -> {
                adjustStock(order, -1);
                orders.changeStatus(orderId, OrderStatus.CONFIRMED, "Payment approved, reference " + reference);
                log.debug("Order {} confirmed", orderId);
            }
            case PaymentResult.Declined(var reason, _) -> {
                orders.changeStatus(orderId, OrderStatus.REJECTED, "Payment declined: " + reason);
                log.debug("Order {} rejected, payment declined: {}", orderId, reason);
            }
        }
    }

    /// Cancels a `PENDING` or `CONFIRMED` order; a confirmed order gives its stock back.
    /// An order that is already final is left as it is.
    public void cancel(UUID orderId, String reason) {
        var status = lockStatus(orderId);
        if (!status.canBecome(OrderStatus.CANCELLED)) {
            log.debug("Order {} is {}, cancellation ignored", orderId, status);
            return;
        }
        if (status == OrderStatus.CONFIRMED) {
            var order = orders.findById(orderId).orElseThrow();
            // take the row locks in the same order as process() does, so the two cannot deadlock
            inventory.lock(order.items().stream().map(OrderItem::productId).toList());
            adjustStock(order, 1);
        }
        orders.changeStatus(orderId, OrderStatus.CANCELLED, reason);
        log.debug("Order {} cancelled: {}", orderId, reason);
    }

    private OrderStatus lockStatus(UUID orderId) {
        var status = orders.lockStatus(orderId).orElseThrow(() -> new NotFoundException("Order", orderId));
        log.debug("Locked order {} with status {}", orderId, status);
        return status;
    }

    /// Changes the stock of every item by `sign * quantity` and evicts the cached product details.
    private void adjustStock(Order order, int sign) {
        for (var item : order.items()) {
            inventory.adjust(item.productId(), sign * item.quantity());
        }
        cache.evict(order.items().stream().map(item -> ProductService.cacheKey(item.productId())).toArray(String[]::new));
    }
}
