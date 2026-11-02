package com.example.sales.catalog.domain;

import com.example.sales.common.api.Page;
import com.example.sales.common.cache.CacheAside;
import com.example.sales.common.concurrency.Structured;
import com.example.sales.common.error.NotFoundException;
import com.example.sales.messaging.domain.EventPublisher;
import com.example.sales.messaging.domain.ProductGuideChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;

/// Products and stock levels, read through the cache.
///
/// `product:{id}` holds the details and is evicted on every product or stock change.
/// List pages (`product-page:...`) are not evicted; their short TTL bounds staleness.
/// Creating or updating a product publishes [ProductGuideChanged], so the guide is embedded again.
@Service
public class ProductService {

    static final Duration DETAILS_TTL = Duration.ofMinutes(10);
    static final Duration PAGE_TTL = Duration.ofSeconds(60);

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final TypeReference<Page<ProductSummary>> PAGE = new TypeReference<>() { };

    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final CacheAside cache;
    private final EventPublisher events;

    public ProductService(ProductRepository products, InventoryRepository inventory, CacheAside cache,
                          EventPublisher events) {
        this.products = products;
        this.inventory = inventory;
        this.cache = cache;
        this.events = events;
    }

    public static String cacheKey(long productId) {
        return "product:" + productId;
    }

    public Page<ProductSummary> list(ProductQuery query) {
        log.debug("Listing products {}", query);
        return cache.get(query.cacheKey(), PAGE, PAGE_TTL, () -> products.find(query));
    }

    public ProductDetails details(long id) {
        return cache.get(cacheKey(id), ProductDetails.class, DETAILS_TTL, () -> loadDetails(id));
    }

    public String guide(long id) {
        return products.guide(id).orElseThrow(() -> new NotFoundException("Product", id));
    }

    public ProductDetails create(ProductData data) {
        log.debug("Creating product {}", data);
        var id = products.create(data);
        // a row cached under a recycled id (for example after a schema reset) must not survive
        cache.evict(cacheKey(id));
        log.debug("Created product {}", id);
        events.publish(ProductGuideChanged.of(id));
        return details(id);
    }

    public ProductDetails update(long id, ProductData data) {
        log.debug("Updating product {}: {}", id, data);
        if (!products.update(id, data)) {
            throw new NotFoundException("Product", id);
        }
        cache.evict(cacheKey(id));
        events.publish(ProductGuideChanged.of(id));
        return details(id);
    }

    public void delete(long id) {
        log.debug("Deleting product {}", id);
        if (!products.delete(id)) {
            throw new NotFoundException("Product", id);
        }
        cache.evict(cacheKey(id));
    }

    public Inventory setStock(long id, int quantity) {
        log.debug("Setting stock of product {} to {}", id, quantity);
        var stock = inventory.set(id, quantity).orElseThrow(() -> new NotFoundException("Product", id));
        cache.evict(cacheKey(id));
        return stock;
    }

    /// Loads product, stock and guide summary in parallel. A missing product is an
    /// expected outcome, so the subtasks return `Optional`s and the 404 is raised after
    /// the join: a failing subtask would cancel its siblings mid-query (see [Structured]).
    private ProductDetails loadDetails(long id) {
        try (var scope = Structured.open()) {
            var product = scope.fork(() -> products.findById(id));
            var stock = scope.fork(() -> inventory.quantity(id));
            var guide = scope.fork(() -> products.guideSummary(id));
            Structured.join(scope);
            var details = new ProductDetails(
                    product.get().orElseThrow(() -> new NotFoundException("Product", id)),
                    stock.get(),
                    guide.get().orElseThrow(() -> new NotFoundException("Product", id)));
            log.debug("Loaded product details {}", details);
            return details;
        }
    }
}
