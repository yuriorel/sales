package com.example.sales.order.domain;

import com.example.sales.catalog.domain.InventoryRepository;
import com.example.sales.common.cache.CacheAside;
import com.example.sales.common.error.NotFoundException;
import com.example.sales.order.OrderFixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderProcessorTest {

    final OrderRepository orders = mock(OrderRepository.class);
    final InventoryRepository inventory = mock(InventoryRepository.class);
    final PaymentGateway payments = mock(PaymentGateway.class);
    final CacheAside cache = mock(CacheAside.class);
    final OrderProcessor processor = new OrderProcessor(orders, inventory, payments, cache);

    final List<OrderItem> items = List.of(
            new OrderItem(1, "Speaker", new BigDecimal("50.00"), "USD", 2),
            new OrderItem(2, "Cable", new BigDecimal("5.00"), "USD", 1));

    Order given(OrderStatus status) {
        var order = OrderFixtures.order(UUID.randomUUID(), status, items);
        when(orders.lockStatus(order.id())).thenReturn(Optional.of(status));
        when(orders.findById(order.id())).thenReturn(Optional.of(order));
        return order;
    }

    @Test
    void approvedPaymentReservesStockAndConfirms() {
        var order = given(OrderStatus.PENDING);
        when(inventory.lock(List.of(1L, 2L))).thenReturn(Map.of(1L, 2, 2L, 5));
        var approved = new PaymentResult.Approved("MOCK-1", Map.of());
        when(payments.charge(order.id(), new BigDecimal("105.00"), "USD")).thenReturn(approved);

        processor.process(order.id());

        verify(orders).savePayment(order.id(), new BigDecimal("105.00"), approved);
        verify(inventory).adjust(1, -2);
        verify(inventory).adjust(2, -1);
        verify(cache).evict("product:1", "product:2");
        verify(orders).changeStatus(order.id(), OrderStatus.CONFIRMED, "Payment approved, reference MOCK-1");
    }

    @Test
    void insufficientStockRejectsWithoutCharging() {
        var order = given(OrderStatus.PENDING);
        when(inventory.lock(List.of(1L, 2L))).thenReturn(Map.of(1L, 1));

        processor.process(order.id());

        verify(orders).changeStatus(order.id(), OrderStatus.REJECTED,
                "Insufficient stock: Speaker (2 requested, 1 in stock), Cable (1 requested, 0 in stock)");
        verifyNoInteractions(payments);
        verify(inventory, never()).adjust(anyLong(), anyInt());
    }

    @Test
    void declinedPaymentRejectsAndKeepsStock() {
        var order = given(OrderStatus.PENDING);
        when(inventory.lock(List.of(1L, 2L))).thenReturn(Map.of(1L, 9, 2L, 9));
        var declined = new PaymentResult.Declined("card expired", Map.of());
        when(payments.charge(any(), any(), any())).thenReturn(declined);

        processor.process(order.id());

        verify(orders).savePayment(order.id(), new BigDecimal("105.00"), declined);
        verify(orders).changeStatus(order.id(), OrderStatus.REJECTED, "Payment declined: card expired");
        verify(inventory, never()).adjust(anyLong(), anyInt());
    }

    @Test
    void orderThatIsNoLongerPendingIsNotProcessed() {
        var order = given(OrderStatus.CANCELLED);

        processor.process(order.id());

        verifyNoInteractions(inventory, payments);
        verify(orders, never()).changeStatus(any(), any(), any());
    }

    @Test
    void unknownOrderFailsSoTheDeliveryIsRetried() {
        var id = UUID.randomUUID();
        when(orders.lockStatus(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> processor.process(id)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cancellingConfirmedOrderReleasesStock() {
        var order = given(OrderStatus.CONFIRMED);

        processor.cancel(order.id(), "Cancelled by alice");

        verify(inventory).lock(List.of(1L, 2L));
        verify(inventory).adjust(1, 2);
        verify(inventory).adjust(2, 1);
        verify(orders).changeStatus(order.id(), OrderStatus.CANCELLED, "Cancelled by alice");
    }

    @Test
    void cancellingPendingOrderReleasesNothing() {
        var order = given(OrderStatus.PENDING);

        processor.cancel(order.id(), "Cancelled by alice");

        verifyNoInteractions(inventory);
        verify(orders).changeStatus(eq(order.id()), eq(OrderStatus.CANCELLED), startsWith("Cancelled"));
    }

    @Test
    void cancellingFinalOrderIsIgnored() {
        var order = given(OrderStatus.REJECTED);

        processor.cancel(order.id(), "Cancelled by alice");

        verifyNoInteractions(inventory);
        verify(orders, never()).changeStatus(any(), any(), any());
    }
}
