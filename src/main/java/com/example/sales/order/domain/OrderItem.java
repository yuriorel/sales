package com.example.sales.order.domain;

import java.math.BigDecimal;

/// An order line. Name and price are a snapshot taken when the order was placed.
public record OrderItem(long productId, String productName, BigDecimal unitPrice, String currency, int quantity) {
}
