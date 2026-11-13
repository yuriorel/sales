package com.example.sales.cart.domain;

import java.math.BigDecimal;
import java.util.List;

/// The signed-in user's cart. Prices are current; the order takes the price snapshot.
public record Cart(List<CartItem> items, BigDecimal total) {

    public static Cart of(List<CartItem> items) {
        return new Cart(items, items.stream().map(CartItem::lineTotal).reduce(BigDecimal.ZERO, BigDecimal::add));
    }
}
