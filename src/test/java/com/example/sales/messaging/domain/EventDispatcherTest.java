package com.example.sales.messaging.domain;

import com.example.sales.order.domain.OrderProcessor;
import com.example.sales.search.domain.GuideIndexer;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class EventDispatcherTest {

    final ProcessedEventRepository processedEvents = mock(ProcessedEventRepository.class);
    final OrderProcessor orders = mock(OrderProcessor.class);
    final GuideIndexer guides = mock(GuideIndexer.class);
    final EventDispatcher dispatcher = new EventDispatcher(processedEvents, orders, guides);

    @Test
    void orderPlacedIsProcessed() {
        var event = OrderPlaced.of(UUID.randomUUID());
        when(processedEvents.markProcessed(event)).thenReturn(true);

        dispatcher.dispatch(event);

        verify(orders).process(event.orderId());
        verifyNoInteractions(guides);
    }

    @Test
    void orderCancelledIsCancelled() {
        var event = OrderCancelled.of(UUID.randomUUID(), "customer request");
        when(processedEvents.markProcessed(event)).thenReturn(true);

        dispatcher.dispatch(event);

        verify(orders).cancel(event.orderId(), "customer request");
        verifyNoInteractions(guides);
    }

    @Test
    void productGuideChangedIsReindexed() {
        var event = ProductGuideChanged.of(42);
        when(processedEvents.markProcessed(event)).thenReturn(true);

        dispatcher.dispatch(event);

        verify(guides).reindex(42);
        verifyNoInteractions(orders);
    }

    @Test
    void duplicateDeliveryIsNoOp() {
        when(processedEvents.markProcessed(any())).thenReturn(false);

        dispatcher.dispatch(OrderPlaced.of(UUID.randomUUID()));
        dispatcher.dispatch(ProductGuideChanged.of(42));

        verifyNoInteractions(orders, guides);
    }
}
