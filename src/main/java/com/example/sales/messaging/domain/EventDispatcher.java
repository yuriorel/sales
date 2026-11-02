package com.example.sales.messaging.domain;

import com.example.sales.order.domain.OrderProcessor;
import com.example.sales.search.domain.GuideIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/// Routes a consumed event to its handler. Recording the event and handling it share
/// one database transaction, so an event is either fully processed once or, after a
/// failure, not recorded and retried.
@Service
public class EventDispatcher {

    private static final Logger log = LoggerFactory.getLogger(EventDispatcher.class);

    private final ProcessedEventRepository processedEvents;
    private final OrderProcessor orders;
    private final GuideIndexer guides;

    public EventDispatcher(ProcessedEventRepository processedEvents, OrderProcessor orders, GuideIndexer guides) {
        this.processedEvents = processedEvents;
        this.orders = orders;
        this.guides = guides;
    }

    @Transactional
    public void dispatch(SalesEvent event) {
        if (!processedEvents.markProcessed(event)) {
            log.debug("Duplicate delivery of event {}, skipped", event.eventId());
            return;
        }
        log.debug("Dispatching {}", event);
        switch (event) {
            case OrderPlaced(_, _, var orderId) -> orders.process(orderId);
            case OrderCancelled(_, _, var orderId, var reason) -> orders.cancel(orderId, reason);
            case ProductGuideChanged(_, _, var productId) -> guides.reindex(productId);
        }
    }
}
