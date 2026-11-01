package com.example.sales.catalog.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/// A row of the `v_category_stats` view.
///
/// @param tags    distinct tags of the category's products
/// @param summary JSON summary built in SQL with `jsonb_build_object`
public record CategoryStats(
        long categoryId,
        String slug,
        String name,
        long productCount,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        BigDecimal avgPrice,
        long totalStock,
        List<String> tags,
        Map<String, Object> summary) {
}
