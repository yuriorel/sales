package com.example.sales.catalog.persistence.postgres;

import com.example.sales.catalog.CatalogFixtures;
import com.example.sales.catalog.domain.Category;
import com.example.sales.catalog.domain.CategoryData;
import com.example.sales.catalog.domain.CategoryRepository;
import com.example.sales.catalog.domain.Dimensions;
import com.example.sales.catalog.domain.InventoryRepository;
import com.example.sales.catalog.domain.ProductData;
import com.example.sales.catalog.domain.ProductQuery;
import com.example.sales.catalog.domain.ProductRepository;
import com.example.sales.catalog.domain.ProductSort;
import com.example.sales.catalog.domain.ProductSummary;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The catalog adapters against the test schema. Every test runs in a transaction that is rolled back.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PostgresCatalogRepositoryIT {

    @Autowired
    CategoryRepository categories;

    @Autowired
    ProductRepository products;

    @Autowired
    InventoryRepository inventory;

    Category category;

    @BeforeEach
    void createCategory() {
        category = categories.create(new CategoryData(CatalogFixtures.slug(), "Category " + System.nanoTime(), "For tests"));
    }

    @Test
    void productRoundTripsArraysCompositeAndJsonb() {
        var attributes = Map.<String, Object>of("color", "black", "warranty", Map.of("months", 24));
        var id = products.create(CatalogFixtures.product(CatalogFixtures.sku(), category.id(), "19.99", List.of("a", "b"), attributes));

        var product = products.findById(id).orElseThrow();

        assertThat(product.categorySlug()).isEqualTo(category.slug());
        assertThat(product.price()).isEqualByComparingTo("19.99");
        assertThat(product.tags()).containsExactly("a", "b");
        assertThat(product.dimensions()).isEqualTo(
                new Dimensions(new BigDecimal("10.00"), new BigDecimal("20.50"), new BigDecimal("3.25")));
        assertThat(product.attributes()).isEqualTo(attributes);
        assertThat(product.version()).isZero();
        assertThat(inventory.quantity(id)).isZero();
        assertThat(products.guide(id)).hasValueSatisfying(guide -> assertThat(guide).startsWith("# Overview"));
        assertThat(products.guideSummary(id)).hasValueSatisfying(summary -> {
            assertThat(summary.sections()).containsExactly("Overview", "Setup");
            assertThat(summary.wordCount()).isEqualTo(19);
            assertThat(summary.embeddedChunks()).isZero();
        });
    }

    @Test
    void optionalFieldsMayBeMissing() {
        var data = new ProductData(CatalogFixtures.sku(), category.id(), "Plain", null, "No extras",
                new BigDecimal("5"), null, null, null, null, null, null, "# Guide", null);

        var product = products.findById(products.create(data)).orElseThrow();

        assertThat(product.brand()).isNull();
        assertThat(product.rating()).isNull();
        assertThat(product.dimensions()).isNull();
        assertThat(product.currency()).isEqualTo("USD");
        assertThat(product.tags()).isEmpty();
        assertThat(product.attributes()).isEmpty();
    }

    @Test
    void tagFiltersUseAnyOrAll() {
        var both = create("30", List.of("wireless", "bluetooth"), Map.of());
        var one = create("20", List.of("wireless"), Map.of());
        create("10", List.of("wired"), Map.of());

        assertThat(ids(query(List.of("wireless", "bluetooth"), false, Map.of(), ProductSort.PRICE))).containsExactly(one, both);
        assertThat(ids(query(List.of("wireless", "bluetooth"), true, Map.of(), ProductSort.PRICE))).containsExactly(both);
    }

    @Test
    void attributeFilterUsesJsonbContainment() {
        var black = create("10", List.of(), Map.of("color", "black", "size", "XL"));
        create("10", List.of(), Map.of("color", "white", "size", "XL"));

        assertThat(ids(query(List.of(), false, Map.of("color", "black"), ProductSort.NAME))).containsExactly(black);
        assertThat(ids(query(List.of(), false, Map.of("color", "black", "size", "S"), ProductSort.NAME))).isEmpty();
    }

    @Test
    void pagesAreSortedAndCounted() {
        var cheap = create("1", List.of(), Map.of());
        var middle = create("2", List.of(), Map.of());
        var expensive = create("3", List.of(), Map.of());
        inventory.set(middle, 4);

        var first = products.find(new ProductQuery(category.slug(), null, false, null, ProductSort.PRICE_DESC, 0, 2));
        var second = products.find(new ProductQuery(category.slug(), null, false, null, ProductSort.PRICE_DESC, 1, 2));

        assertThat(ids(first.items())).containsExactly(expensive, middle);
        assertThat(ids(second.items())).containsExactly(cheap);
        assertThat(first.totalItems()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.items().get(1).stock()).isEqualTo(4);
        assertThat(first.items().get(1).inStock()).isTrue();
        assertThat(first.items().getFirst().inStock()).isFalse();
    }

    @Test
    void updateReplacesFieldsAndIncrementsVersion() {
        var id = create("10", List.of("old"), Map.of());
        var changed = CatalogFixtures.product(CatalogFixtures.sku(), category.id(), "12.50", List.of("new"), Map.of("color", "red"));

        assertThat(products.update(id, changed)).isTrue();

        var product = products.findById(id).orElseThrow();
        assertThat(product.price()).isEqualByComparingTo("12.50");
        assertThat(product.tags()).containsExactly("new");
        assertThat(product.attributes()).containsEntry("color", "red");
        assertThat(product.version()).isEqualTo(1);
        assertThat(products.update(Long.MAX_VALUE, changed)).isFalse();
    }

    @Test
    void inventoryIsUpserted() {
        var id = create("10", List.of(), Map.of());

        assertThat(inventory.set(id, 7)).hasValueSatisfying(stock -> assertThat(stock.quantity()).isEqualTo(7));
        assertThat(inventory.set(id, 2)).hasValueSatisfying(stock -> assertThat(stock.quantity()).isEqualTo(2));
        assertThat(inventory.quantity(id)).isEqualTo(2);
        assertThat(inventory.set(Long.MAX_VALUE, 1)).isEmpty();
        assertThat(inventory.quantity(Long.MAX_VALUE)).isZero();
    }

    @Test
    void deleteRemovesProductAndStock() {
        var id = create("10", List.of(), Map.of());
        inventory.set(id, 3);

        assertThat(products.delete(id)).isTrue();

        assertThat(products.findById(id)).isEmpty();
        assertThat(inventory.quantity(id)).isZero();
        assertThat(products.delete(id)).isFalse();
    }

    @Test
    void categoryStatsAggregateProducts() {
        create("10", List.of("b", "a"), Map.of());
        var second = create("30", List.of("a", "c"), Map.of());
        inventory.set(second, 5);

        var stats = categories.stats().stream().filter(row -> row.categoryId() == category.id()).findFirst().orElseThrow();

        assertThat(stats.productCount()).isEqualTo(2);
        assertThat(stats.minPrice()).isEqualByComparingTo("10");
        assertThat(stats.maxPrice()).isEqualByComparingTo("30");
        assertThat(stats.avgPrice()).isEqualByComparingTo("20");
        assertThat(stats.totalStock()).isEqualTo(5);
        assertThat(stats.tags()).containsExactly("a", "b", "c");
        assertThat(stats.summary()).containsEntry("productCount", 2).containsEntry("tagCount", 3);
    }

    @Test
    void categoryUpdateAndDelete() {
        var renamed = new CategoryData(category.slug() + "-x", category.name() + " X", null);

        assertThat(categories.update(category.id(), renamed))
                .hasValueSatisfying(updated -> assertThat(updated.slug()).isEqualTo(renamed.slug()));
        assertThat(categories.findAll()).extracting(Category::id).contains(category.id());
        assertThat(categories.update(Long.MAX_VALUE, renamed)).isEmpty();
        assertThat(categories.delete(category.id())).isTrue();
        assertThat(categories.delete(category.id())).isFalse();
    }

    @Test
    void duplicateCategorySlugIsRejected() {
        assertThatThrownBy(() -> categories.create(new CategoryData(category.slug(), "Other " + System.nanoTime(), null)))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void categoryWithProductsCannotBeDeleted() {
        create("10", List.of(), Map.of());

        assertThatThrownBy(() -> categories.delete(category.id())).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void productInUnknownCategoryIsRejected() {
        assertThatThrownBy(() -> products.create(CatalogFixtures.product(CatalogFixtures.sku(), Long.MAX_VALUE, "1", List.of(), Map.of())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private long create(String price, List<String> tags, Map<String, Object> attributes) {
        return products.create(CatalogFixtures.product(CatalogFixtures.sku(), category.id(), price, tags, attributes));
    }

    private List<ProductSummary> query(List<String> tags, boolean matchAll, Map<String, String> attributes, ProductSort sort) {
        return products.find(new ProductQuery(category.slug(), tags, matchAll, attributes, sort, 0, 20)).items();
    }

    private static List<Long> ids(List<ProductSummary> items) {
        return items.stream().map(ProductSummary::id).toList();
    }
}
