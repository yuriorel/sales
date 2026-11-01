package com.example.sales.catalog.domain;

import com.example.sales.common.api.Page;

import java.util.Optional;

/// Persistence port for products.
public interface ProductRepository {

    Page<ProductSummary> find(ProductQuery query);

    Optional<Product> findById(long id);

    Optional<GuideSummary> guideSummary(long id);

    /// The full user guide (Markdown).
    Optional<String> guide(long id);

    /// Inserts the product with an empty inventory row.
    ///
    /// @return the new product id
    long create(ProductData data);

    /// Replaces all writable fields and increments the version.
    ///
    /// @return `false` when there is no such product
    boolean update(long id, ProductData data);

    /// @return `false` when there is no such product
    boolean delete(long id);
}
