package com.example.sales.catalog.api;

import com.example.sales.catalog.domain.Category;
import com.example.sales.catalog.domain.CategoryData;
import com.example.sales.catalog.domain.CategoryService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

/// Categories: public reads, `ADMIN` writes (enforced in `SecurityConfig`).
@RestController
@RequestMapping("/api/v1/categories")
class CategoryController {

    private static final Logger log = LoggerFactory.getLogger(CategoryController.class);

    private final CategoryService categories;

    CategoryController(CategoryService categories) {
        this.categories = categories;
    }

    @GetMapping
    List<Category> list() {
        log.debug("GET /categories");
        return categories.list();
    }

    @PostMapping
    ResponseEntity<Category> create(@Valid @RequestBody CategoryData data) {
        log.debug("POST /categories {}", data);
        var category = categories.create(data);
        return ResponseEntity.created(URI.create("/api/v1/categories/" + category.id())).body(category);
    }

    @PutMapping("/{id}")
    Category update(@PathVariable long id, @Valid @RequestBody CategoryData data) {
        log.debug("PUT /categories/{} {}", id, data);
        return categories.update(id, data);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        log.debug("DELETE /categories/{}", id);
        categories.delete(id);
        return ResponseEntity.noContent().build();
    }
}
