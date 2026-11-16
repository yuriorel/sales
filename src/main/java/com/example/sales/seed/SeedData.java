package com.example.sales.seed;

import com.example.sales.catalog.domain.CategoryData;
import com.example.sales.catalog.domain.Dimensions;
import com.example.sales.catalog.domain.ProductData;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/// The content of `seed/products.json`, written by [SeedDataGenerator] and read by [SeedDataLoader].
public record SeedData(List<SeedCategory> categories, List<SeedProduct> products) {

    public record SeedCategory(String slug, String name, String description) {

        public CategoryData toCategoryData() {
            return new CategoryData(slug, name, description);
        }
    }

    /// @param attributes specifications stored as `jsonb`: weight, warranty, shipping, return policy and more
    public record SeedProduct(
            String sku,
            String categorySlug,
            String name,
            String brand,
            String description,
            BigDecimal price,
            BigDecimal rating,
            List<String> tags,
            List<String> imageUrls,
            Dimensions dimensions,
            Map<String, Object> attributes,
            int stock,
            String userGuide,
            List<String> guideSections) {

        public ProductData toProductData(long categoryId) {
            return new ProductData(sku, categoryId, name, brand, description, price, "USD", rating, tags, imageUrls,
                    dimensions, attributes, userGuide, guideSections);
        }

        public int guideWords() {
            return userGuide.strip().split("\\s+").length;
        }

        /// Keeps the (long) user guide out of log output.
        @Override
        public String toString() {
            return "SeedProduct[sku=%s, category=%s, name=%s, guide=%d words]".formatted(sku, categorySlug, name, guideWords());
        }
    }
}
