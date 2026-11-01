package com.example.sales.catalog.api;

import com.example.sales.catalog.domain.Inventory;
import com.example.sales.catalog.domain.ProductData;
import com.example.sales.catalog.domain.ProductDetails;
import com.example.sales.catalog.domain.ProductQuery;
import com.example.sales.catalog.domain.ProductService;
import com.example.sales.catalog.domain.ProductSort;
import com.example.sales.catalog.domain.ProductSummary;
import com.example.sales.common.api.Page;
import com.example.sales.common.error.ValidationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/// Products and stock: public reads, `ADMIN` writes (enforced in `SecurityConfig`).
@RestController
@RequestMapping("/api/v1/products")
class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService products;

    ProductController(ProductService products) {
        this.products = products;
    }

    record StockRequest(@NotNull @Min(0) Integer quantity) {
    }

    /// Lists products, filtered by category slug, tags (`tag=a&tag=b`, any of them, or
    /// all with `matchAll=true`) and attributes (`attr=color:black`, all must match).
    @GetMapping
    Page<ProductSummary> list(
            @RequestParam(required = false) String category,
            @RequestParam(name = "tag", required = false) List<String> tags,
            @RequestParam(defaultValue = "false") boolean matchAll,
            @RequestParam(name = "attr", required = false) List<String> attributes,
            @RequestParam(defaultValue = "NAME") ProductSort sort,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var query = new ProductQuery(category, tags, matchAll, attributeFilter(attributes), sort, page, size);
        log.debug("GET /products {}", query);
        return products.list(query);
    }

    @GetMapping("/{id}")
    ProductDetails details(@PathVariable long id) {
        log.debug("GET /products/{}", id);
        return products.details(id);
    }

    @GetMapping(path = "/{id}/guide", produces = MediaType.TEXT_MARKDOWN_VALUE)
    String guide(@PathVariable long id) {
        log.debug("GET /products/{}/guide", id);
        return products.guide(id);
    }

    @PostMapping
    ResponseEntity<ProductDetails> create(@Valid @RequestBody ProductData data) {
        log.debug("POST /products {}", data);
        var details = products.create(data);
        return ResponseEntity.created(URI.create("/api/v1/products/" + details.product().id())).body(details);
    }

    @PutMapping("/{id}")
    ProductDetails update(@PathVariable long id, @Valid @RequestBody ProductData data) {
        log.debug("PUT /products/{} {}", id, data);
        return products.update(id, data);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable long id) {
        log.debug("DELETE /products/{}", id);
        products.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/inventory")
    Inventory setStock(@PathVariable long id, @Valid @RequestBody StockRequest request) {
        log.debug("PUT /products/{}/inventory {}", id, request);
        return products.setStock(id, request.quantity());
    }

    private static Map<String, String> attributeFilter(List<String> attributes) {
        if (attributes == null) {
            return Map.of();
        }
        return attributes.stream()
                .map(attribute -> {
                    var pair = attribute.split(":", 2);
                    if (pair.length != 2 || pair[0].isBlank()) {
                        throw new ValidationException("attr", "must be key:value, got '" + attribute + "'");
                    }
                    return pair;
                })
                .collect(Collectors.toMap(pair -> pair[0], pair -> pair[1], (_, last) -> last));
    }
}
