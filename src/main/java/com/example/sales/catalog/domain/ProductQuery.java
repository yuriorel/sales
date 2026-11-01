package com.example.sales.catalog.domain;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/// Product list filters.
///
/// @param category   category slug, or `null` for all categories
/// @param tags       products with any of these tags, or with all of them when `matchAll`
/// @param attributes products whose `attributes` contain all these key/value pairs
public record ProductQuery(
        String category,
        List<String> tags,
        boolean matchAll,
        Map<String, String> attributes,
        ProductSort sort,
        int page,
        int size) {

    public ProductQuery {
        tags = tags == null ? List.of() : tags.stream().sorted().distinct().toList();
        attributes = attributes == null ? Map.of() : new TreeMap<>(attributes);
        sort = sort == null ? ProductSort.NAME : sort;
    }

    /// The Redis key of this page. Tags and attributes are sorted, so equal queries share a key.
    public String cacheKey() {
        return "product-page:%s:%s:%s:%s:%s:%d:%d".formatted(
                category, String.join(",", tags), matchAll, attributes, sort, page, size);
    }
}
