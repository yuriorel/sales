package com.example.sales.catalog;

import com.example.sales.catalog.domain.Dimensions;
import com.example.sales.catalog.domain.ProductData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public final class CatalogFixtures {

    private CatalogFixtures() {
    }

    public static ProductData product(String sku, long categoryId, String price, List<String> tags, Map<String, Object> attributes) {
        return new ProductData(sku, categoryId, "Product " + sku, "Acme", "A product for tests",
                new BigDecimal(price), "USD", new BigDecimal("4.5"), tags, List.of("https://img.example.com/" + sku + ".png"),
                new Dimensions(new BigDecimal("10.00"), new BigDecimal("20.50"), new BigDecimal("3.25")), attributes,
                """
                        # Overview
                        This guide explains how to use the product.

                        # Setup
                        Plug it in and switch it on.
                        """,
                List.of("Overview", "Setup"));
    }

    /// A unique upper-case SKU.
    public static String sku() {
        return "T-" + Long.toString(System.nanoTime(), 36).toUpperCase();
    }

    /// A unique category slug.
    public static String slug() {
        return "test-" + Long.toString(System.nanoTime(), 36);
    }
}
