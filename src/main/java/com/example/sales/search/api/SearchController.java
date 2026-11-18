package com.example.sales.search.api;

import com.example.sales.search.domain.SearchHit;
import com.example.sales.search.domain.SearchMode;
import com.example.sales.search.domain.SearchService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/// Public product search.
@RestController
@RequestMapping(path = "/search", version = "1")
class SearchController {

    private static final Logger log = LoggerFactory.getLogger(SearchController.class);

    private final SearchService search;

    SearchController(SearchService search) {
        this.search = search;
    }

    record SearchResponse(String query, SearchMode mode, List<SearchHit> hits) {
    }

    @GetMapping
    SearchResponse search(
            @RequestParam @NotBlank @Size(max = 500) String q,
            @RequestParam(defaultValue = "HYBRID") SearchMode mode,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit) {
        log.debug("GET /search q='{}' mode={} limit={}", q, mode, limit);
        return new SearchResponse(q, mode, search.search(q, mode, limit));
    }
}
