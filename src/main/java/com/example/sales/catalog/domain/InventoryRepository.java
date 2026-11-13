package com.example.sales.catalog.domain;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;

/// Persistence port for stock levels.
public interface InventoryRepository {

    /// The stock of a product, `0` when it has no inventory row.
    int quantity(long productId);

    /// Sets the stock of a product.
    ///
    /// @return empty when there is no such product
    Optional<Inventory> set(long productId, int quantity);

    /// Locks the inventory rows of the products until the transaction ends and returns
    /// their stock. Products without a row are missing from the result.
    Map<Long, Integer> lock(Collection<Long> productIds);

    /// Adds `delta` (negative to take stock) to the stock of a product.
    void adjust(long productId, int delta);
}
