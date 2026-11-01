package com.example.sales.catalog.api;

import com.example.sales.catalog.domain.GuideSummary;
import com.example.sales.catalog.domain.Product;
import com.example.sales.catalog.domain.ProductDetails;
import com.example.sales.catalog.domain.ProductQuery;
import com.example.sales.catalog.domain.ProductService;
import com.example.sales.catalog.domain.ProductSort;
import com.example.sales.common.api.Page;
import com.example.sales.common.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Request mapping, parameter parsing, validation and JSON of the product endpoints.
/// Security is covered by the integration tests, so the filters are off here.
@WebMvcTest(ProductController.class)
@AutoConfigureMockMvc(addFilters = false)
class ProductControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ProductService products;

    final Product product = new Product(7, "SKU-7", 1, "audio", "Audio", "Speaker", "Acme", "Loud",
            new BigDecimal("99.90"), "USD", null, List.of("bluetooth"), List.of(), null, Map.of("color", "black"),
            0, Instant.parse("2026-10-01T10:00:00Z"), Instant.parse("2026-10-01T10:00:00Z"));
    final ProductDetails details = new ProductDetails(product, 4, new GuideSummary(List.of("Overview"), 1500, 0));

    @Test
    void listParsesFilters() throws Exception {
        when(products.list(any())).thenReturn(Page.of(List.of(), 1, 10, 0));

        mvc.perform(get("/api/v1/products")
                        .param("category", "audio")
                        .param("tag", "wireless", "bluetooth")
                        .param("matchAll", "true")
                        .param("attr", "color:black", "size:XL")
                        .param("sort", "PRICE_DESC")
                        .param("page", "1")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.totalPages").value(0));

        verify(products).list(new ProductQuery("audio", List.of("bluetooth", "wireless"), true,
                Map.of("color", "black", "size", "XL"), ProductSort.PRICE_DESC, 1, 10));
    }

    @Test
    void listDefaults() throws Exception {
        when(products.list(any())).thenReturn(Page.of(List.of(), 0, 20, 0));

        mvc.perform(get("/api/v1/products")).andExpect(status().isOk());

        verify(products).list(new ProductQuery(null, List.of(), false, Map.of(), ProductSort.NAME, 0, 20));
    }

    @Test
    void malformedAttributeFilterIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/products").param("attr", "color"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("attr"));
    }

    @Test
    void outOfRangePageSizeIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/products").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void unknownSortIsBadRequest() throws Exception {
        mvc.perform(get("/api/v1/products").param("sort", "COLOR"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void detailsAreNested() throws Exception {
        when(products.details(7)).thenReturn(details);

        mvc.perform(get("/api/v1/products/7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.product.sku").value("SKU-7"))
                .andExpect(jsonPath("$.product.attributes.color").value("black"))
                .andExpect(jsonPath("$.stock").value(4))
                .andExpect(jsonPath("$.guide.wordCount").value(1500));
    }

    @Test
    void unknownProductIsNotFoundProblem() throws Exception {
        when(products.details(8)).thenThrow(new NotFoundException("Product", 8));

        mvc.perform(get("/api/v1/products/8"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void guideIsMarkdown() throws Exception {
        when(products.guide(7)).thenReturn("# Overview\nText");

        mvc.perform(get("/api/v1/products/7/guide"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_MARKDOWN))
                .andExpect(content().string("# Overview\nText"));
    }

    @Test
    void guideOfUnknownProductIsNotFoundProblem() throws Exception {
        when(products.guide(8)).thenThrow(new NotFoundException("Product", 8));

        mvc.perform(get("/api/v1/products/8/guide"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void createReturnsLocation() throws Exception {
        when(products.create(any())).thenReturn(details);

        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku": "SKU-7", "categoryId": 1, "name": "Speaker", "description": "Loud",
                         "price": 99.90, "userGuide": "# Overview"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/products/7"));
    }

    @Test
    void invalidProductListsErrors() throws Exception {
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content("""
                        {"sku": "bad sku", "categoryId": 1, "name": "Speaker", "description": "Loud",
                         "price": -1, "rating": 7, "currency": "usd", "userGuide": "# Overview",
                         "dimensions": {"width": 1, "height": 2}}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(5))
                .andExpect(jsonPath("$.errors[?(@.field == 'dimensions.depth')]").exists())
                .andExpect(content().string(containsString("\"field\":\"sku\"")));
    }

    @Test
    void negativeStockIsBadRequest() throws Exception {
        mvc.perform(put("/api/v1/products/7/inventory").contentType(MediaType.APPLICATION_JSON).content("""
                        {"quantity": -1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("quantity"));
    }
}
