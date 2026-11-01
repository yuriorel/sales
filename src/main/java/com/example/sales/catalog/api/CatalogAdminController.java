package com.example.sales.catalog.api;

import com.example.sales.catalog.domain.CategoryService;
import com.example.sales.catalog.domain.CategoryStats;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Catalog statistics for `ADMIN` users.
@RestController
@RequestMapping("/api/v1/admin/stats")
class CatalogAdminController {

    private static final Logger log = LoggerFactory.getLogger(CatalogAdminController.class);

    private final CategoryService categories;

    CatalogAdminController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping("/categories")
    List<CategoryStats> categoryStats() {
        log.debug("GET /admin/stats/categories");
        return categories.stats();
    }
}
