package com.example.sales.catalog.api;

import com.example.sales.catalog.CatalogFixtures;
import com.example.sales.catalog.domain.ProductData;
import com.example.sales.catalog.domain.ProductService;
import com.example.sales.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// The catalog API end to end: admin writes with a bearer token carrying the `ADMIN`
/// scope, public reads, cache population and eviction in Redis (test database 1).
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwt.class)
class CatalogApiIT {

    static final String ADMIN = "Bearer " + TestJwt.token("catalog-admin", claims -> claims.claim("scope", "openid ADMIN"));
    static final String CUSTOMER = "Bearer " + TestJwt.token("catalog-customer", _ -> { });

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    StringRedisTemplate redis;

    @Test
    void adminManagesCatalogThatEveryoneCanRead() throws Exception {
        var slug = CatalogFixtures.slug();
        var categoryId = json.readTree(mvc.perform(post("/api/v1/categories").header(HttpHeaders.AUTHORIZATION, ADMIN)
                                .contentType(MediaType.APPLICATION_JSON).content("""
                                        {"slug": "%s", "name": "Name %s"}
                                        """.formatted(slug, slug)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .get("id").asLong();
        mvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].slug", hasItem(slug)));

        var data = CatalogFixtures.product(CatalogFixtures.sku(), categoryId, "49.99", List.of("wireless"), Map.of("color", "black"));
        var productId = json.readTree(mvc.perform(post("/api/v1/products").header(HttpHeaders.AUTHORIZATION, ADMIN)
                                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(data)))
                        .andExpect(status().isCreated())
                        .andReturn().getResponse().getContentAsString())
                .at("/product/id").asLong();
        var cacheKey = ProductService.cacheKey(productId);

        mvc.perform(get("/api/v1/products/{id}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.categorySlug").value(slug))
                .andExpect(jsonPath("$.stock").value(0))
                .andExpect(jsonPath("$.guide.sections[1]").value("Setup"));
        assertThat(redis.hasKey(cacheKey)).isTrue();

        mvc.perform(put("/api/v1/products/{id}/inventory", productId).header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quantity": 5}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.quantity").value(5));
        assertThat(redis.hasKey(cacheKey)).isFalse();
        mvc.perform(get("/api/v1/products/{id}", productId)).andExpect(jsonPath("$.stock").value(5));

        var changed = new ProductData(data.sku(), categoryId, data.name(), data.brand(), data.description(),
                new BigDecimal("39.99"), data.currency(), data.rating(), data.tags(), data.imageUrls(),
                data.dimensions(), data.attributes(), data.userGuide(), data.guideSections());
        mvc.perform(put("/api/v1/products/{id}", productId).header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(changed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.price").value(39.99))
                .andExpect(jsonPath("$.product.version").value(1));
        mvc.perform(get("/api/v1/products/{id}", productId)).andExpect(jsonPath("$.product.price").value(39.99));

        mvc.perform(get("/api/v1/products").param("category", slug).param("tag", "wireless").param("attr", "color:black"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(productId))
                .andExpect(jsonPath("$.items[0].inStock").value(true));
        mvc.perform(get("/api/v1/products/{id}/guide", productId))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_MARKDOWN));
        mvc.perform(get("/api/v1/admin/stats/categories").header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.slug == '%s')].productCount".formatted(slug)).value(1));

        mvc.perform(delete("/api/v1/categories/{id}", categoryId).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isConflict());
        mvc.perform(delete("/api/v1/products/{id}", productId).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNoContent());
        assertThat(redis.hasKey(cacheKey)).isFalse();
        mvc.perform(get("/api/v1/products/{id}", productId)).andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/categories/{id}", categoryId).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isNoContent());
    }

    @Test
    void writesNeedAdmin() throws Exception {
        var body = json.writeValueAsString(CatalogFixtures.product(CatalogFixtures.sku(), 1, "1", List.of(), Map.of()));

        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/products").header(HttpHeaders.AUTHORIZATION, CUSTOMER)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/admin/stats/categories").header(HttpHeaders.AUTHORIZATION, CUSTOMER))
                .andExpect(status().isForbidden());
    }

    @Test
    void productInUnknownCategoryIsConflict() throws Exception {
        var body = json.writeValueAsString(CatalogFixtures.product(CatalogFixtures.sku(), Long.MAX_VALUE, "1", List.of(), Map.of()));

        mvc.perform(post("/api/v1/products").header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void unknownIdsAreNotFound() throws Exception {
        mvc.perform(get("/api/v1/products/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/products/{id}/guide", Long.MAX_VALUE)).andExpect(status().isNotFound());
        mvc.perform(put("/api/v1/products/{id}/inventory", Long.MAX_VALUE).header(HttpHeaders.AUTHORIZATION, ADMIN)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quantity": 1}
                                """))
                .andExpect(status().isNotFound());
    }
}
