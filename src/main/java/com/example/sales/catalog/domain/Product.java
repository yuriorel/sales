package com.example.sales.catalog.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/// A product with its category, without stock and without the user guide text.
///
/// @param attributes specifications (`jsonb`), for example weight, warranty or colour
public record Product(
        long id,
        String sku,
        long categoryId,
        String categorySlug,
        String categoryName,
        String name,
        String brand,
        String description,
        BigDecimal price,
        String currency,
        BigDecimal rating,
        List<String> tags,
        List<String> imageUrls,
        Dimensions dimensions,
        Map<String, Object> attributes,
        long version,
        Instant createdAt,
        Instant updatedAt) {
}
