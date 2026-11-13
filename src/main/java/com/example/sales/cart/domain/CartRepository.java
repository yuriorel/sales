package com.example.sales.cart.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// Persistence port for carts. A user has at most one cart, created on the first add.
public interface CartRepository {

    List<CartItem> items(UUID userId);

    /// The quantity of a product in the cart, empty when it is not in the cart.
    Optional<Integer> quantity(UUID userId, long productId);

    /// Sets the quantity of a product, adding the line when it is missing.
    void put(UUID userId, long productId, int quantity);

    /// @return `false` when the product is not in the cart
    boolean remove(UUID userId, long productId);

    void clear(UUID userId);
}
