package com.example.sales.catalog.domain;

/// The product details page: product, current stock and guide summary.
public record ProductDetails(Product product, int stock, GuideSummary guide) {
}
