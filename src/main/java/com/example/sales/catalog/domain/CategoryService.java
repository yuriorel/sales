package com.example.sales.catalog.domain;

import com.example.sales.common.cache.CacheAside;
import com.example.sales.common.error.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;

import java.time.Duration;
import java.util.List;

/// Categories, with the full list cached under `category:all`.
@Service
public class CategoryService {

    static final String CACHE_KEY = "category:all";
    static final Duration TTL = Duration.ofMinutes(30);

    private static final Logger log = LoggerFactory.getLogger(CategoryService.class);
    private static final TypeReference<List<Category>> CATEGORIES = new TypeReference<>() { };

    private final CategoryRepository categories;
    private final CacheAside cache;

    public CategoryService(CategoryRepository categories, CacheAside cache) {
        this.categories = categories;
        this.cache = cache;
    }

    public List<Category> list() {
        return cache.get(CACHE_KEY, CATEGORIES, TTL, categories::findAll);
    }

    public Category create(CategoryData data) {
        log.debug("Creating category {}", data);
        var category = categories.create(data);
        cache.evict(CACHE_KEY);
        return category;
    }

    public Category update(long id, CategoryData data) {
        log.debug("Updating category {}: {}", id, data);
        var category = categories.update(id, data).orElseThrow(() -> new NotFoundException("Category", id));
        cache.evict(CACHE_KEY);
        return category;
    }

    public void delete(long id) {
        log.debug("Deleting category {}", id);
        if (!categories.delete(id)) {
            throw new NotFoundException("Category", id);
        }
        cache.evict(CACHE_KEY);
    }

    public List<CategoryStats> stats() {
        return categories.stats();
    }
}
