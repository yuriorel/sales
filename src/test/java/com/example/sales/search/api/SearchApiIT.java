package com.example.sales.search.api;

import com.example.sales.catalog.CatalogFixtures;
import com.example.sales.catalog.domain.CategoryData;
import com.example.sales.catalog.domain.CategoryService;
import com.example.sales.catalog.domain.ProductData;
import com.example.sales.catalog.domain.ProductService;
import com.example.sales.support.FakeAiConfig;
import com.example.sales.support.TestJwt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Search and AI endpoints against the test schema. Guides are embedded through the
/// real path (product created -> `ProductGuideChanged` on Kafka -> `GuideIndexer`),
/// with the deterministic fake embedding model of the `test` profile.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwt.class)
class SearchApiIT {

    static final String CUSTOMER = "Bearer " + TestJwt.token("search-customer", _ -> { });

    @Autowired
    MockMvc mvc;

    @Autowired
    CategoryService categories;

    @Autowired
    ProductService products;

    @Autowired
    JdbcClient jdbc;

    String token;
    long productId;
    String productName;

    /// A product whose guide repeats a unique word, so it is the best match for that word in every mode.
    @BeforeEach
    void productWithUniqueWordInItsGuide() {
        token = "zorb" + Long.toString(System.nanoTime(), 36);
        var category = categories.create(new CategoryData(CatalogFixtures.slug(), "Search " + UUID.randomUUID(), null));
        var base = CatalogFixtures.product(CatalogFixtures.sku(), category.id(), "19.99", List.of(), Map.of());
        var guide = """
                # Overview
                The %1$s seal keeps water out.

                # Maintenance
                Clean the %1$s seal monthly: %1$s %1$s %1$s %1$s %1$s %1$s %1$s %1$s.
                """.formatted(token);
        var data = new ProductData(base.sku(), base.categoryId(), base.name(), base.brand(), base.description(),
                base.price(), base.currency(), base.rating(), base.tags(), base.imageUrls(), base.dimensions(),
                base.attributes(), guide, List.of("Overview", "Maintenance"));
        var product = products.create(data).product();
        productId = product.id();
        productName = product.name();
        await().atMost(Duration.ofSeconds(20)).until(() -> jdbc.sql(
                "select count(*) from product_guide_chunks where product_id = :id").param("id", productId)
                .query(Long.class).single() > 0);
    }

    @Test
    void everyModeFindsTheProduct() throws Exception {
        for (var mode : List.of("FULLTEXT", "SEMANTIC", "HYBRID")) {
            mvc.perform(get("/api/v1/search").param("q", token).param("mode", mode))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.mode").value(mode))
                    .andExpect(jsonPath("$.hits[0].productId").value(productId))
                    .andExpect(jsonPath("$.hits[0].name").value(productName));
        }
        mvc.perform(get("/api/v1/search").param("q", token))
                .andExpect(jsonPath("$.mode").value("HYBRID"))
                .andExpect(jsonPath("$.hits[0].productId").value(productId));
    }

    @Test
    void invalidSearchIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/search")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/search").param("q", " ")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/search").param("q", token).param("limit", "0")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/search").param("q", token).param("mode", "FUZZY")).andExpect(status().isBadRequest());
    }

    @Test
    void assistantAnswersFromTheProductGuide() throws Exception {
        mvc.perform(post("/api/v1/ai/ask").header(HttpHeaders.AUTHORIZATION, CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"question": "How do I clean the %s seal?", "productId": %d}
                                """.formatted(token, productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer", startsWith(FakeAiConfig.ANSWER_PREFIX)))
                .andExpect(jsonPath("$.sources[*].productId", everyItem(is((int) productId))))
                // the fake model echoes its prompt: the retrieved guide excerpt was passed as context
                .andExpect(jsonPath("$.answer", containsString(productName)))
                .andExpect(jsonPath("$.answer", containsString("Clean the " + token + " seal monthly")));
    }

    @Test
    void assistantNeedsAuthenticationAndAKnownProduct() throws Exception {
        var body = """
                {"question": "How do I clean it?", "productId": %d}
                """;
        mvc.perform(post("/api/v1/ai/ask").contentType(MediaType.APPLICATION_JSON).content(body.formatted(productId)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/ai/ask").header(HttpHeaders.AUTHORIZATION, CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON).content(body.formatted(Long.MAX_VALUE)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/ai/ask").header(HttpHeaders.AUTHORIZATION, CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"question": ""}
                                """))
                .andExpect(status().isBadRequest());
    }
}
