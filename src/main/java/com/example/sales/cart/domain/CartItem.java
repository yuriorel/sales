package com.example.sales.cart.domain;

import java.math.BigDecimal;

/// A cart line with the product's current name, price and stock.
public record CartItem(long productId, String productName, BigDecimal unitPrice, String currency, int quantity, int stock) {

    public BigDecimal lineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
