package com.example.sales.order.domain;

import com.example.sales.cart.domain.Cart;
import com.example.sales.cart.domain.CartItem;
import com.example.sales.cart.domain.CartService;
import com.example.sales.common.error.ConflictException;
import com.example.sales.common.error.NotFoundException;
import com.example.sales.common.error.ValidationException;
import com.example.sales.messaging.domain.EventPublisher;
import com.example.sales.messaging.domain.OrderCancelled;
import com.example.sales.messaging.domain.OrderPlaced;
import com.example.sales.order.OrderFixtures;
import com.example.sales.user.domain.PostalAddress;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static com.example.sales.order.OrderFixtures.ADDRESS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    final OrderRepository orders = mock(OrderRepository.class);
    final CartService carts = mock(CartService.class);
    final EventPublisher events = mock(EventPublisher.class);
    final OrderService service = new OrderService(orders, carts, events);

    final CartItem speaker = new CartItem(7, "Speaker", new BigDecimal("50.00"), "USD", 2, 10);

    @Test
    void placeSnapshotsCartShipsToDefaultAddressAndPublishes() {
        var user = OrderFixtures.user(ADDRESS);
        var id = UUID.randomUUID();
        var stored = OrderFixtures.order(user.id(), OrderStatus.PENDING, List.of());
        when(carts.get(user)).thenReturn(Cart.of(List.of(speaker)));
        when(orders.create(any(), any(), any(), any(), any())).thenReturn(id);
        when(orders.findById(id)).thenReturn(Optional.of(stored));

        assertThat(service.place(user, null)).isEqualTo(stored);

        verify(orders).create(user.id(), ADDRESS, new BigDecimal("100.00"), "USD",
                List.of(new OrderItem(7, "Speaker", new BigDecimal("50.00"), "USD", 2)));
        verify(carts).clear(user);
        verify(events).publish(argThat(event -> event instanceof OrderPlaced(_, _, var orderId) && orderId.equals(id)));
    }

    @Test
    void requestAddressWinsOverDefault() {
        var user = OrderFixtures.user(ADDRESS);
        var other = new PostalAddress("2 Side St", null, "Paris", null, "75001", "FR");
        var id = UUID.randomUUID();
        when(carts.get(user)).thenReturn(Cart.of(List.of(speaker)));
        when(orders.create(any(), any(), any(), any(), any())).thenReturn(id);
        when(orders.findById(id)).thenReturn(Optional.of(OrderFixtures.order(user.id(), OrderStatus.PENDING, List.of())));

        service.place(user, other);

        verify(orders).create(eq(user.id()), eq(other), any(), any(), any());
    }

    @Test
    void placeWithoutAnyAddressIsRejected() {
        var user = OrderFixtures.user(null);

        assertThatThrownBy(() -> service.place(user, null))
                .isInstanceOf(ValidationException.class)
                .hasMessage("A shipping address is required");
        verifyNoInteractions(orders, events);
    }

    @Test
    void placeWithEmptyCartIsRejected() {
        var user = OrderFixtures.user(ADDRESS);
        when(carts.get(user)).thenReturn(Cart.of(List.of()));

        assertThatThrownBy(() -> service.place(user, null)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(orders, events);
    }

    @Test
    void placeWithMixedCurrenciesIsRejected() {
        var user = OrderFixtures.user(ADDRESS);
        var euro = new CartItem(8, "Lamp", new BigDecimal("20.00"), "EUR", 1, 10);
        when(carts.get(user)).thenReturn(Cart.of(List.of(speaker, euro)));

        assertThatThrownBy(() -> service.place(user, null)).isInstanceOf(ValidationException.class);
        verifyNoInteractions(orders, events);
    }

    @Test
    void ordersOfOtherUsersAreNotFoundExceptForAdmin() {
        var owner = OrderFixtures.user(null);
        var stranger = OrderFixtures.user(null);
        var order = OrderFixtures.order(owner.id(), OrderStatus.CONFIRMED, List.of());
        when(orders.findById(order.id())).thenReturn(Optional.of(order));

        assertThat(service.get(owner, false, order.id())).isEqualTo(order);
        assertThat(service.get(stranger, true, order.id())).isEqualTo(order);
        assertThatThrownBy(() -> service.get(stranger, false, order.id())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void listIsLimitedToOwnOrdersExceptForAdmin() {
        var user = OrderFixtures.user(null);

        service.list(user, false, 0, 20);
        service.list(user, true, 1, 10);

        verify(orders).find(user.id(), 0, 20);
        verify(orders).find(null, 1, 10);
    }

    @Test
    void cancelPublishesEventForCancellableOrder() {
        var user = OrderFixtures.user(null);
        var order = OrderFixtures.order(user.id(), OrderStatus.CONFIRMED, List.of());
        when(orders.findById(order.id())).thenReturn(Optional.of(order));

        service.cancel(user, false, order.id());

        verify(events).publish(argThat(event -> event instanceof OrderCancelled(_, _, var orderId, var reason)
                && orderId.equals(order.id()) && reason.equals("Cancelled by alice")));
    }

    @Test
    void cancelOfFinalOrderIsConflict() {
        var user = OrderFixtures.user(null);
        var order = OrderFixtures.order(user.id(), OrderStatus.REJECTED, List.of());
        when(orders.findById(order.id())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(user, false, order.id())).isInstanceOf(ConflictException.class);
        verifyNoInteractions(events);
    }
}
