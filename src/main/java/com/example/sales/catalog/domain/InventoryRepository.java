package com.example.sales.catalog.domain;

import java.util.Optional;

/// Persistence port for stock levels.
public interface InventoryRepository {

    /// The stock of a product, `0` when it has no inventory row.
    int quantity(long productId);

    /// Sets the stock of a product.
    ///
    /// @return empty when there is no such product
    Optional<Inventory> set(long productId, int quantity);
}
