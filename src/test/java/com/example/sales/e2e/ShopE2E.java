package com.example.sales.e2e;

import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static com.example.sales.e2e.E2E.awaitJson;
import static com.example.sales.e2e.E2E.body;
import static com.example.sales.e2e.E2E.expect;
import static com.example.sales.e2e.E2E.expectProblem;
import static org.assertj.core.api.Assertions.assertThat;

/// The shop as a client sees it, against the running server: a new customer registers,
/// browses, searches, orders and cancels, and an admin maintains the catalog.
///
/// The tests work on one fixture product (`E2E-SPEAKER` in category `e2e`). It is
/// created on the first run and reset on every run, so the dev catalog does not grow;
/// it cannot be deleted once it has orders.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ShopE2E {

    static final String SKU = "E2E-SPEAKER";
    static final String PASSWORD = "secret-e2e-1";
    static final int STOCK = 10;
    static final String ADDRESS = """
            {"line1": "1 Test Street", "city": "Springfield", "postalCode": "62701", "countryCode": "US"}""";
    static final JsonMapper JSON = JsonMapper.builder().build();

    static Playwright playwright;
    static APIRequestContext anonymous;
    static APIRequestContext admin;
    static APIRequestContext customer;
    static String username;
    /// unique per run, so the cached product pages of earlier runs are not reused
    static String runTag;
    static long categoryId;
    static long productId;

    @BeforeAll
    static void setUp() {
        playwright = Playwright.create();
        anonymous = E2E.anonymous(playwright);
        admin = E2E.basic(playwright, "admin", E2E.ADMIN_PASSWORD);
        runTag = "run-" + System.currentTimeMillis();
        username = "e2e_" + System.currentTimeMillis();
        customer = E2E.basic(playwright, username, PASSWORD);

        categoryId = fixtureCategory();
        productId = fixtureProduct();
        expect(admin.put("/api/v1/products/" + productId, body(product("Zephyrion E2E Speaker", shortGuide()))), 200);
        expect(admin.put("/api/v1/products/" + productId + "/inventory", body("""
                {"quantity": %d}""".formatted(STOCK))), 200);
    }

    @AfterAll
    static void tearDown() {
        playwright.close();
    }

    @Test
    @Order(1)
    void customerRegistersAndSignsInWithBasic() {
        var user = expect(anonymous.post("/api/v1/auth/register", body("""
                {"username": "%s", "email": "%s@example.com", "password": "%s"}""".formatted(username, username, PASSWORD))), 201);
        assertThat(user.get("roles").get(0).asString()).isEqualTo("CUSTOMER");

        var me = expect(customer.get("/api/v1/users/me"), 200);
        assertThat(me.get("username").asString()).isEqualTo(username);
        assertThat(me.get("authProvider").asString()).isEqualTo("LOCAL");
    }

    @Test
    @Order(2)
    void profileStoresTheDefaultAddress() {
        var updated = expect(customer.put("/api/v1/users/me", body("""
                {"defaultAddress": %s, "preferences": {"newsletter": false}}""".formatted(ADDRESS))), 200);
        assertThat(updated.get("defaultAddress").get("city").asString()).isEqualTo("Springfield");

        var me = expect(customer.get("/api/v1/users/me"), 200);
        assertThat(me.get("defaultAddress").get("postalCode").asString()).isEqualTo("62701");
        assertThat(me.get("preferences").get("newsletter").asBoolean()).isFalse();
    }

    @Test
    @Order(3)
    void browseFiltersByTagsAndAttributes() {
        assertThat(productIds("/api/v1/products?category=e2e&tag=" + runTag + "&attr=color:black")).containsExactly(productId);
        assertThat(productIds("/api/v1/products?tag=e2e&tag=" + runTag + "&matchAll=true")).containsExactly(productId);
        assertThat(productIds("/api/v1/products?tag=" + runTag + "&attr=color:white")).isEmpty();

        var details = expect(anonymous.get("/api/v1/products/" + productId), 200);
        assertThat(details.get("stock").asInt()).isEqualTo(STOCK);
        var guide = anonymous.get("/api/v1/products/" + productId + "/guide");
        assertThat(guide.headers().get("content-type")).startsWith("text/markdown");
        assertThat(guide.text()).contains("# Pairing");
    }

    @ParameterizedTest
    @Order(4)
    @ValueSource(strings = {"FULLTEXT", "SEMANTIC", "HYBRID"})
    void searchFindsSeedProductsInEveryMode(String mode) {
        var result = expect(anonymous.get("/api/v1/search?q=wireless+bluetooth+headphones&limit=5&mode=" + mode), 200);
        assertThat(result.get("mode").asString()).isEqualTo(mode);
        assertThat(result.get("hits").size()).isBetween(1, 5);
    }

    @Test
    @Order(5)
    void orderIsConfirmedThenCancelled() {
        expect(customer.post("/api/v1/cart/items", body("""
                {"productId": %d, "quantity": 2}""".formatted(productId))), 200);
        var cart = expect(customer.patch("/api/v1/cart/items/" + productId, body("""
                {"quantity": 3}""")), 200);
        assertThat(cart.get("total").decimalValue()).isEqualByComparingTo("120.00");

        // no body: ships to the default address of the profile
        var placed = customer.post("/api/v1/orders");
        var order = expect(placed, 202);
        var location = placed.headers().get("location");
        assertThat(location).isEqualTo("/api/v1/orders/" + order.get("id").asString());
        assertThat(order.get("status").asString()).isEqualTo("PENDING");
        assertThat(expect(customer.get("/api/v1/cart"), 200).get("items")).isEmpty();

        var confirmed = awaitJson(customer, location, body -> !body.get("status").asString().equals("PENDING"));
        assertThat(confirmed.get("status").asString()).isEqualTo("CONFIRMED");
        assertThat(confirmed.get("shippingAddress").get("line1").asString()).isEqualTo("1 Test Street");
        assertThat(stock()).isEqualTo(STOCK - 3);

        expect(customer.post(location + "/cancel"), 202);
        var cancelled = awaitJson(customer, location, body -> body.get("status").asString().equals("CANCELLED"));
        assertThat(statuses(cancelled)).containsExactly("PENDING", "CONFIRMED", "CANCELLED");
        assertThat(stock()).isEqualTo(STOCK);
    }

    @Test
    @Order(6)
    void orderIsRejectedWhenStockRunsOutBeforeProcessing() {
        expect(customer.post("/api/v1/cart/items", body("""
                {"productId": %d, "quantity": 2}""".formatted(productId))), 200);
        expect(admin.put("/api/v1/products/" + productId + "/inventory", body("""
                {"quantity": 1}""")), 200);
        try {
            var order = expect(customer.post("/api/v1/orders", body("""
                    {"shippingAddress": %s}""".formatted(ADDRESS))), 202);
            var rejected = awaitJson(customer, "/api/v1/orders/" + order.get("id").asString(),
                    body -> !body.get("status").asString().equals("PENDING"));
            assertThat(rejected.get("status").asString()).isEqualTo("REJECTED");
            assertThat(rejected.get("statusHistory").get(1).get("reason").asString()).containsIgnoringCase("stock");
            assertThat(stock()).isEqualTo(1);
        } finally {
            expect(admin.put("/api/v1/products/" + productId + "/inventory", body("""
                    {"quantity": %d}""".formatted(STOCK))), 200);
        }
    }

    @Test
    @Order(7)
    void productUpdateEvictsTheCacheAndReembedsTheGuide() {
        // the short guide from setUp is one chunk; reading the product caches its details
        awaitJson(anonymous, "/api/v1/products/" + productId, body -> body.get("guide").get("embeddedChunks").asInt() == 1);

        var guide = longGuide();
        var updated = expect(admin.put("/api/v1/products/" + productId, body(product("Zephyrion E2E Speaker Mk II", guide))), 200);
        assertThat(updated.get("product").get("name").asString()).isEqualTo("Zephyrion E2E Speaker Mk II");

        // the cached details were evicted by the update ...
        assertThat(expect(anonymous.get("/api/v1/products/" + productId), 200).get("product").get("name").asString())
                .isEqualTo("Zephyrion E2E Speaker Mk II");
        // ... and again when the Kafka listener has embedded the new guide
        var reindexed = awaitJson(anonymous, "/api/v1/products/" + productId,
                body -> body.get("guide").get("embeddedChunks").asInt() > 1);
        assertThat(reindexed.get("guide").get("wordCount").asInt()).isGreaterThan(300);
    }

    @Test
    @Order(8)
    void assistantAnswersFromTheProductGuide() {
        var answer = expect(customer.post("/api/v1/ai/ask", body("""
                {"question": "How long do I hold the Bluetooth button to pair the speaker?", "productId": %d}"""
                .formatted(productId))), 200);
        assertThat(answer.get("answer").asString()).contains("7");
        assertThat(answer.get("sources")).isNotEmpty()
                .allSatisfy(source -> assertThat(source.get("productId").asLong()).isEqualTo(productId));
    }

    @Test
    @Order(9)
    void errorsAreProblemResponses() {
        var correlated = anonymous.get("/api/v9/products",
                RequestOptions.create().setHeader("X-Correlation-Id", "e2e-correlation-1"));
        var problem = expectProblem(correlated, 400);
        assertThat(problem.get("detail").asString()).startsWith("Invalid API version");
        assertThat(problem.get("correlationId").asString()).isEqualTo("e2e-correlation-1");
        assertThat(correlated.headers().get("x-correlation-id")).isEqualTo("e2e-correlation-1");

        expectProblem(anonymous.get("/api/v1/products/999999999"), 404);
        var invalid = expectProblem(anonymous.post("/api/v1/auth/register", body("""
                {"username": "a b", "email": "not-an-email", "password": "short"}""")), 400);
        assertThat(invalid.get("errors").size()).isEqualTo(3);
        expectProblem(anonymous.post("/api/v1/auth/register", body("""
                {"username": "%s", "email": "other-%s@example.com", "password": "%s"}""".formatted(username, username, PASSWORD))), 409);
        expectProblem(customer.post("/api/v1/cart/items", body("""
                {"productId": %d, "quantity": 1000}""".formatted(productId))), 409);
        expectProblem(anonymous.get("/api/v1/products?size=1000"), 400);
    }

    @Test
    @Order(10)
    void authenticationAndAuthorizationFailures() {
        var unauthenticated = anonymous.get("/api/v1/cart");
        expectProblem(unauthenticated, 401);
        assertThat(unauthenticated.headers().get("www-authenticate")).contains("Basic").contains("Bearer");

        expectProblem(E2E.basic(playwright, username, "wrong-password").get("/api/v1/users/me"), 401);
        expectProblem(E2E.bearer(playwright, "not-a-jwt").get("/api/v1/users/me"), 401);
        expectProblem(customer.get("/api/v1/admin/stats/categories"), 403);
        expectProblem(customer.put("/api/v1/products/" + productId + "/inventory", body("""
                {"quantity": 0}""")), 403);
        expect(admin.get("/api/v1/admin/stats/categories"), 200);
    }

    private static long fixtureCategory() {
        var existing = StreamSupport.stream(expect(anonymous.get("/api/v1/categories"), 200).spliterator(), false)
                .filter(category -> category.get("slug").asString().equals("e2e"))
                .findFirst();
        return existing.orElseGet(() -> expect(admin.post("/api/v1/categories", body("""
                        {"slug": "e2e", "name": "End-to-end tests", "description": "Fixture data of the E2E suite"}""")), 201))
                .get("id").asLong();
    }

    /// Finds the fixture by full-text search (the product list is cached for 60 s).
    private static long fixtureProduct() {
        var hits = expect(anonymous.get("/api/v1/search?q=zephyrion&mode=FULLTEXT"), 200).get("hits");
        if (!hits.isEmpty()) {
            return hits.get(0).get("productId").asLong();
        }
        return expect(admin.post("/api/v1/products", body(product("Zephyrion E2E Speaker", shortGuide()))), 201)
                .get("product").get("id").asLong();
    }

    private static String product(String name, String guide) {
        return """
                {
                  "sku": "%s", "categoryId": %d, "name": "%s", "brand": "Zephyrion",
                  "description": "Portable Bluetooth speaker used by the end-to-end tests",
                  "price": 40.00, "currency": "USD", "rating": 4.0,
                  "tags": ["e2e", "%s"], "attributes": {"color": "black", "warranty": "2 years"},
                  "userGuide": %s
                }""".formatted(SKU, categoryId, name, runTag, JSON.writeValueAsString(guide));
    }

    private static String shortGuide() {
        return """
                # Overview
                The Zephyrion speaker is a portable Bluetooth speaker.

                # Pairing
                To pair the Zephyrion speaker, hold the Bluetooth button for 7 seconds until the LED blinks purple.
                """;
    }

    /// Long enough for several chunks of about 1,000 characters.
    private static String longGuide() {
        var paragraph = "Keep the speaker away from water, heat and direct sunlight, and charge it with the supplied cable. ";
        return shortGuide() + IntStream.rangeClosed(1, 6)
                .mapToObj(section -> "\n# Care, part %d\n%s\n".formatted(section, paragraph.repeat(6)))
                .collect(Collectors.joining());
    }

    private static int stock() {
        return expect(anonymous.get("/api/v1/products/" + productId), 200).get("stock").asInt();
    }

    private static List<Long> productIds(String path) {
        return StreamSupport.stream(expect(anonymous.get(path), 200).get("items").spliterator(), false)
                .map(product -> product.get("id").asLong())
                .toList();
    }

    private static List<String> statuses(JsonNode order) {
        return StreamSupport.stream(order.get("statusHistory").spliterator(), false)
                .map(change -> change.get("status").asString())
                .toList();
    }
}
