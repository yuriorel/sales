package com.example.sales.catalog.domain;

import com.example.sales.catalog.CatalogFixtures;
import com.example.sales.common.api.Page;
import com.example.sales.common.cache.CacheAside;
import com.example.sales.common.error.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.core.type.TypeReference;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    final ProductRepository products = mock(ProductRepository.class);
    final InventoryRepository inventory = mock(InventoryRepository.class);
    final CacheAside cache = mock(CacheAside.class);
    final ProductService service = new ProductService(products, inventory, cache);

    final Product product = new Product(7, "SKU-7", 1, "audio", "Audio", "Speaker", "Acme", "Loud",
            new BigDecimal("99.90"), "USD", new BigDecimal("4.5"), List.of("bluetooth"), List.of(), null, Map.of(),
            0, Instant.now(), Instant.now());
    final GuideSummary guide = new GuideSummary(List.of("Overview"), 1800, 12);

    @BeforeEach
    @SuppressWarnings("unchecked")
    void cacheAlwaysMisses() {
        when(cache.get(anyString(), any(Class.class), any(Duration.class), any(Supplier.class)))
                .thenAnswer(call -> call.<Supplier<?>>getArgument(3).get());
        when(cache.get(anyString(), any(TypeReference.class), any(Duration.class), any(Supplier.class)))
                .thenAnswer(call -> call.<Supplier<?>>getArgument(3).get());
    }

    @Test
    void detailsCombineProductStockAndGuide() {
        when(products.findById(7)).thenReturn(Optional.of(product));
        when(inventory.quantity(7)).thenReturn(5);
        when(products.guideSummary(7)).thenReturn(Optional.of(guide));

        assertThat(service.details(7)).isEqualTo(new ProductDetails(product, 5, guide));
        verify(cache).get(eq("product:7"), eq(ProductDetails.class), eq(ProductService.DETAILS_TTL), any());
    }

    @Test
    void detailsOfUnknownProductIsNotFound() {
        when(products.findById(8)).thenReturn(Optional.empty());
        when(products.guideSummary(8)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.details(8))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Product '8' was not found");
    }

    @Test
    void listIsCachedPerQuery() {
        var query = new ProductQuery("audio", List.of("b", "a"), false, Map.of(), null, 0, 20);
        var page = Page.<ProductSummary>of(List.of(), 0, 20, 0);
        when(products.find(query)).thenReturn(page);

        assertThat(service.list(query)).isEqualTo(page);
        verify(cache).get(eq("product-page:audio:a,b:false:{}:NAME:0:20"), any(TypeReference.class),
                eq(ProductService.PAGE_TTL), any());
    }

    @Test
    void createEvictsAndReturnsDetails() {
        var data = CatalogFixtures.product("SKU-7", 1, "99.90", List.of(), Map.of());
        when(products.create(data)).thenReturn(7L);
        when(products.findById(7)).thenReturn(Optional.of(product));
        when(products.guideSummary(7)).thenReturn(Optional.of(guide));

        assertThat(service.create(data).product()).isEqualTo(product);
        verify(cache).evict("product:7");
    }

    @Test
    void updateEvictsCachedDetails() {
        var data = CatalogFixtures.product("SKU-7", 1, "89.90", List.of(), Map.of());
        when(products.update(7, data)).thenReturn(true);
        when(products.findById(7)).thenReturn(Optional.of(product));
        when(products.guideSummary(7)).thenReturn(Optional.of(guide));

        service.update(7, data);

        verify(cache).evict("product:7");
    }

    @Test
    void updateOfUnknownProductIsNotFound() {
        when(products.update(eq(8L), any())).thenReturn(false);

        assertThatThrownBy(() -> service.update(8, CatalogFixtures.product("SKU-8", 1, "1", List.of(), Map.of())))
                .isInstanceOf(NotFoundException.class);
        verify(cache, never()).evict(any(String[].class));
    }

    @Test
    void setStockEvictsCachedDetails() {
        var stock = new Inventory(7, 3, Instant.now());
        when(inventory.set(7, 3)).thenReturn(Optional.of(stock));

        assertThat(service.setStock(7, 3)).isEqualTo(stock);
        verify(cache).evict("product:7");
    }

    @Test
    void setStockOfUnknownProductIsNotFound() {
        when(inventory.set(8, 3)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setStock(8, 3)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void deleteOfUnknownProductIsNotFound() {
        when(products.delete(8)).thenReturn(false);

        assertThatThrownBy(() -> service.delete(8)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void guideOfUnknownProductIsNotFound() {
        when(products.guide(8)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.guide(8)).isInstanceOf(NotFoundException.class);
    }
}
