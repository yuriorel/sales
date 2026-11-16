package com.example.sales.search.domain;

import java.math.BigDecimal;

/// A product found by a search.
///
/// @param score higher is better; its scale depends on the search mode
public record SearchHit(long productId, String name, String brand, BigDecimal price, String currency, double score) {

    public SearchHit withScore(double score) {
        return new SearchHit(productId, name, brand, price, currency, score);
    }
}
