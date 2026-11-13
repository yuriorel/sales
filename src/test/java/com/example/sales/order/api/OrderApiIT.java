package com.example.sales.order.api;

import com.example.sales.catalog.CatalogFixtures;
import com.example.sales.catalog.domain.CategoryData;
import com.example.sales.catalog.domain.CategoryService;
import com.example.sales.catalog.domain.InventoryRepository;
import com.example.sales.catalog.domain.ProductService;
import com.example.sales.support.TestJwt;
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
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Cart and the asynchronous order flow end to end: REST -> Postgres -> Kafka ->
/// listener -> `OrderProcessor` -> Postgres, polled with Awaitility.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwt.class)
class OrderApiIT {

    static final Duration WAIT = Duration.ofSeconds(20);
    static final String ADMIN = "Bearer " + TestJwt.token("order-admin", claims -> claims.claim("scope", "openid ADMIN"));
    static final String ADDRESS = """
            {"shippingAddress": {"line1": "1 Main St", "city": "Springfield", "postalCode": "62701", "countryCode": "US"}}
            """;

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcClient jdbc;

    @Autowired
    CategoryService categories;

    @Autowired
    ProductService products;

    @Autowired
    InventoryRepository inventory;

    @Test
    void orderIsConfirmedAsynchronouslyAndCancelReleasesStock() throws Exception {
        var customer = customer();
        var productId = product("25.00", 5);

        addToCart(customer, productId, 2).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(50.00));
        addToCart(customer, productId, 4)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Only 5 of product %d in stock, requested 6".formatted(productId)));
        mvc.perform(patch("/api/v1/cart/items/{id}", productId).header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"quantity": 3}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].quantity").value(3))
                .andExpect(jsonPath("$.total").value(75.00));

        var placed = placeOrder(customer, ADDRESS)
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith("/api/v1/orders/")))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.total").value(75.00))
                .andExpect(jsonPath("$.shippingAddress.city").value("Springfield"))
                .andExpect(jsonPath("$.items[0].unitPrice").value(25.00));
        var orderId = body(placed).get("id").asString();
        mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(jsonPath("$.items").isEmpty());

        var confirmed = awaitStatus(customer, orderId, "CONFIRMED");
        assertThat(confirmed.at("/statusHistory/1/reason").asString()).startsWith("Payment approved, reference MOCK-");
        assertThat(inventory.quantity(productId)).isEqualTo(2);
        assertThat(jdbc.sql("select status || ':' || (gateway_response ->> 'code') from payments where order_id = :id")
                .param("id", UUID.fromString(orderId)).query(String.class).single()).isEqualTo("APPROVED:APPROVED");

        mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, customer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, ADMIN))
                .andExpect(status().isOk());
        mvc.perform(get("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(orderId));

        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isAccepted())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/orders/" + orderId));
        var cancelled = awaitStatus(customer, orderId, "CANCELLED");
        assertThat(cancelled.get("statusHistory").findValuesAsString("status")).containsExactly("PENDING", "CONFIRMED", "CANCELLED");
        assertThat(inventory.quantity(productId)).isEqualTo(5);
        mvc.perform(post("/api/v1/orders/{id}/cancel", orderId).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isConflict());
    }

    @Test
    void orderBeyondRemainingStockIsRejected() throws Exception {
        var first = customer();
        var second = customer();
        var productId = product("10.00", 3);
        addToCart(first, productId, 2).andExpect(status().isOk());
        addToCart(second, productId, 2).andExpect(status().isOk());

        awaitStatus(first, body(placeOrder(first, ADDRESS)).get("id").asString(), "CONFIRMED");
        var rejected = awaitStatus(second, body(placeOrder(second, ADDRESS)).get("id").asString(), "REJECTED");

        assertThat(rejected.at("/statusHistory/1/reason").asString())
                .isEqualTo("Insufficient stock: Product %s (2 requested, 1 in stock)".formatted(sku(productId)));
        assertThat(inventory.quantity(productId)).isEqualTo(1);
    }

    @Test
    void declinedPaymentRejectsOrderAndKeepsStock() throws Exception {
        var customer = customer();
        var productId = product("20000.00", 1);
        addToCart(customer, productId, 1).andExpect(status().isOk());

        var rejected = awaitStatus(customer, body(placeOrder(customer, ADDRESS)).get("id").asString(), "REJECTED");

        assertThat(rejected.at("/statusHistory/1/reason").asString())
                .isEqualTo("Payment declined: amount exceeds the limit of 10000 USD");
        assertThat(inventory.quantity(productId)).isEqualTo(1);
    }

    @Test
    void placingNeedsItemsAndAnAddress() throws Exception {
        var customer = customer();
        var productId = product("5.00", 10);

        placeOrder(customer, ADDRESS)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("cart"));
        addToCart(customer, productId, 1).andExpect(status().isOk());
        placeOrder(customer, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("shippingAddress"));
        placeOrder(customer, """
                {"shippingAddress": {"line1": "1 Main St", "city": "Springfield", "postalCode": "62701", "countryCode": "usa"}}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("shippingAddress.countryCode"));

        mvc.perform(put("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"defaultAddress": {"line1": "9 Home Rd", "city": "Lyon", "postalCode": "69001", "countryCode": "FR"}}
                                """))
                .andExpect(status().isOk());
        placeOrder(customer, null)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.shippingAddress.city").value("Lyon"));

        mvc.perform(post("/api/v1/orders")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/cart")).andExpect(status().isUnauthorized());
    }

    @Test
    void cartLinesCanBeRemovedAndCleared() throws Exception {
        var customer = customer();
        var first = product("1.00", 10);
        var second = product("2.00", 10);
        addToCart(customer, first, 1).andExpect(status().isOk());
        addToCart(customer, second, 1).andExpect(status().isOk());
        addToCart(customer, Long.MAX_VALUE, 1).andExpect(status().isNotFound());

        mvc.perform(delete("/api/v1/cart/items/{id}", first).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.total").value(2.00));
        mvc.perform(delete("/api/v1/cart/items/{id}", first).header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/v1/cart").header(HttpHeaders.AUTHORIZATION, customer))
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.total").value(0));
        mvc.perform(post("/api/v1/cart/items").header(HttpHeaders.AUTHORIZATION, customer)
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"productId": 1, "quantity": 0}
                                """))
                .andExpect(status().isBadRequest());
    }

    /// A bearer token for a new customer; the user row is provisioned on the first request.
    static String customer() {
        return "Bearer " + TestJwt.token("order-" + UUID.randomUUID(), _ -> { });
    }

    long product(String price, int stock) {
        var category = categories.create(new CategoryData(CatalogFixtures.slug(), "Orders " + UUID.randomUUID(), null));
        var data = CatalogFixtures.product(CatalogFixtures.sku(), category.id(), price, List.of(), Map.of());
        var id = products.create(data).product().id();
        products.setStock(id, stock);
        return id;
    }

    String sku(long productId) {
        return products.details(productId).product().sku();
    }

    ResultActions addToCart(String token, long productId, int quantity) throws Exception {
        return mvc.perform(post("/api/v1/cart/items").header(HttpHeaders.AUTHORIZATION, token)
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"productId": %d, "quantity": %d}
                        """.formatted(productId, quantity)));
    }

    ResultActions placeOrder(String token, String body) throws Exception {
        var request = post("/api/v1/orders").header(HttpHeaders.AUTHORIZATION, token);
        return mvc.perform(body == null ? request : request.contentType(MediaType.APPLICATION_JSON).content(body));
    }

    JsonNode awaitStatus(String token, String orderId, String expected) {
        return await().atMost(WAIT).until(
                () -> body(mvc.perform(get("/api/v1/orders/{id}", orderId).header(HttpHeaders.AUTHORIZATION, token))
                        .andExpect(status().isOk())),
                order -> order.get("status").asString().equals(expected));
    }

    JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
