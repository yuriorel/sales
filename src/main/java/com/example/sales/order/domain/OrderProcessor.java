package com.example.sales.order.domain;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/// Handles order events. Stock reservation, payment and cancellation are built in Phase 7.
@Service
public class OrderProcessor {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessor.class);

    public void process(UUID orderId) {
        log.info("Order {} placed; processing arrives in Phase 7", orderId);
    }

    public void cancel(UUID orderId, String reason) {
        log.info("Order {} cancelled ({}); processing arrives in Phase 7", orderId, reason);
    }
}
