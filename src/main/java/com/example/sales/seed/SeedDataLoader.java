package com.example.sales.seed;

import com.example.sales.catalog.domain.Category;
import com.example.sales.catalog.domain.CategoryService;
import com.example.sales.catalog.domain.InventoryRepository;
import com.example.sales.catalog.domain.ProductQuery;
import com.example.sales.catalog.domain.ProductRepository;
import com.example.sales.catalog.domain.ProductSort;
import com.example.sales.search.domain.GuideIndexer;
import com.example.sales.search.domain.GuideVectorStore;
import com.example.sales.user.domain.Role;
import com.example.sales.user.domain.UserRepository;
import com.example.sales.user.domain.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/// Prepares a development database on startup (`sales.seed.enabled`):
///
/// - demo users `admin`/`admin123` (`ADMIN`) and `customer`/`customer123`, when missing;
/// - categories, products and stock from `seed/products.json`, when the catalog is empty (one transaction);
/// - embeddings for every product guide that has none yet, on virtual threads.
///
/// Each step checks what is already there, so a restart after a failure completes the work.
@Component
@ConditionalOnProperty(name = "sales.seed.enabled", havingValue = "true")
class SeedDataLoader implements ApplicationRunner {

    static final String SEED_FILE = "seed/products.json";
    static final int EMBEDDING_CONCURRENCY = 4;

    private static final Logger log = LoggerFactory.getLogger(SeedDataLoader.class);

    private final JsonMapper json;
    private final CategoryService categories;
    private final ProductRepository products;
    private final InventoryRepository inventory;
    private final GuideVectorStore vectors;
    private final GuideIndexer indexer;
    private final UserService userService;
    private final UserRepository users;
    private final TransactionTemplate transaction;

    SeedDataLoader(JsonMapper json, CategoryService categories, ProductRepository products, InventoryRepository inventory,
                   GuideVectorStore vectors, GuideIndexer indexer, UserService userService, UserRepository users,
                   TransactionTemplate transaction) {
        this.json = json;
        this.categories = categories;
        this.products = products;
        this.inventory = inventory;
        this.vectors = vectors;
        this.indexer = indexer;
        this.userService = userService;
        this.users = users;
        this.transaction = transaction;
    }

    @Override
    public void run(ApplicationArguments args) throws IOException, InterruptedException {
        seedUser("admin", "admin123", Set.of(Role.CUSTOMER, Role.ADMIN));
        seedUser("customer", "customer123", Set.of(Role.CUSTOMER));
        if (catalogIsEmpty()) {
            loadCatalog();
        }
        embedMissingGuides();
    }

    private void seedUser(String username, String password, Set<Role> roles) {
        if (users.findLocal(username).isPresent()) {
            return;
        }
        var user = userService.register(username, username + "@example.com", password);
        if (!user.roles().equals(roles)) {
            users.setRoles(user.id(), roles);
        }
        log.info("Created demo user {} with roles {}", username, roles);
    }

    private boolean catalogIsEmpty() {
        var any = new ProductQuery(null, null, false, Map.of(), ProductSort.NAME, 0, 1);
        return products.find(any).totalItems() == 0;
    }

    private void loadCatalog() throws IOException {
        SeedData seed;
        try (var in = new ClassPathResource(SEED_FILE).getInputStream()) {
            seed = json.readValue(in, SeedData.class);
        }
        log.info("Loading {} categories and {} products from {}", seed.categories().size(), seed.products().size(), SEED_FILE);
        transaction.executeWithoutResult(_ -> {
            var categoryIds = seed.categories().stream()
                    .map(category -> categories.create(category.toCategoryData()))
                    .collect(Collectors.toMap(Category::slug, Category::id));
            for (var product : seed.products()) {
                var id = products.create(product.toProductData(categoryIds.get(product.categorySlug())));
                inventory.set(id, product.stock());
                log.debug("Loaded {} as product {}", product, id);
            }
        });
        log.info("Catalog loaded");
    }

    /// Embeds guides on virtual threads; the semaphore keeps the embedding API within its rate limits.
    private void embedMissingGuides() throws InterruptedException {
        var missing = vectors.productsWithoutChunks();
        if (missing.isEmpty()) {
            log.info("All product guides are embedded");
            return;
        }
        log.info("Embedding the guides of {} products", missing.size());
        var permits = new Semaphore(EMBEDDING_CONCURRENCY);
        var failed = new AtomicInteger();
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (var productId : missing) {
                executor.submit(() -> {
                    permits.acquire();
                    try {
                        indexer.reindex(productId);
                    } catch (RuntimeException e) {
                        failed.incrementAndGet();
                        log.error("Embedding the guide of product {} failed", productId, e);
                    } finally {
                        permits.release();
                    }
                    return null;
                });
            }
        }
        log.info("Embedded {} guides, {} failed (they are retried on the next start)",
                missing.size() - failed.get(), failed.get());
    }
}
