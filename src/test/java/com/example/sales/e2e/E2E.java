package com.example.sales.e2e;

import com.microsoft.playwright.APIRequest;
import com.microsoft.playwright.APIRequestContext;
import com.microsoft.playwright.APIResponse;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.RequestOptions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/// Shared helpers of the end-to-end tests, which run against the running server
/// (`./mvnw -Pe2e verify`). Override the targets with `-De2e.baseUrl=...` and
/// `-De2e.brokerUrl=...`. Both servers use self-signed certificates, so HTTPS errors
/// are ignored.
final class E2E {

    static final String BASE_URL = System.getProperty("e2e.baseUrl", "https://localhost:9443");
    static final String BROKER_URL = System.getProperty("e2e.brokerUrl", "https://localhost:8443");
    static final String ADMIN_PASSWORD = System.getProperty("e2e.adminPassword", "admin123");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private E2E() {
    }

    /// A client for the sales API that sends no credentials.
    static APIRequestContext anonymous(Playwright playwright) {
        return client(playwright, Map.of());
    }

    static APIRequestContext basic(Playwright playwright, String username, String password) {
        var credentials = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        return client(playwright, Map.of("Authorization", "Basic " + credentials));
    }

    static APIRequestContext bearer(Playwright playwright, String token) {
        return client(playwright, Map.of("Authorization", "Bearer " + token));
    }

    private static APIRequestContext client(Playwright playwright, Map<String, String> headers) {
        return playwright.request().newContext(new APIRequest.NewContextOptions()
                .setBaseURL(BASE_URL)
                .setIgnoreHTTPSErrors(true)
                .setExtraHTTPHeaders(headers));
    }

    /// A JSON body for `post`, `put` and `patch`.
    static RequestOptions body(String json) {
        return RequestOptions.create().setHeader("Content-Type", "application/json").setData(json);
    }

    static JsonNode json(APIResponse response) {
        return JSON.readTree(response.text());
    }

    /// Asserts the status and returns the JSON body.
    static JsonNode expect(APIResponse response, int status) {
        assertThat(response.status()).as("%s %s: %s", status, response.url(), response.text()).isEqualTo(status);
        return response.text().isEmpty() ? null : json(response);
    }

    /// Polls `GET path` until the JSON body matches, for the effects of Kafka events.
    static JsonNode awaitJson(APIRequestContext api, String path, Predicate<JsonNode> condition) {
        return await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(500))
                .until(() -> expect(api.get(path), 200), condition::test);
    }

    /// Asserts an RFC 9457 problem response that carries a correlation id.
    static JsonNode expectProblem(APIResponse response, int status) {
        var problem = expect(response, status);
        assertThat(response.headers().get("content-type")).startsWith("application/problem+json");
        assertThat(problem.get("status").asInt()).isEqualTo(status);
        assertThat(problem.get("correlationId").asString()).isNotBlank();
        return problem;
    }
}
