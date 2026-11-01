package com.example.sales.catalog.domain;

import java.math.BigDecimal;
import java.util.List;

/// A product list entry, read from the `v_product_catalog` view.
public record ProductSummary(
        long id,
        String sku,
        String name,
        String brand,
        BigDecimal price,
        String currency,
        BigDecimal rating,
        List<String> tags,
        List<String> imageUrls,
        String categorySlug,
        int stock,
        boolean inStock) {
}
