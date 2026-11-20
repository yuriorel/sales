package com.example.sales.e2e;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.Route;
import com.microsoft.playwright.options.AriaRole;
import com.microsoft.playwright.options.FormData;
import com.microsoft.playwright.options.RequestOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashSet;
import java.util.concurrent.atomic.AtomicReference;

import static com.example.sales.e2e.E2E.expect;
import static com.example.sales.e2e.E2E.expectProblem;
import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;
import static org.assertj.core.api.Assertions.assertThat;

/// Browser tests in headless Chromium: Swagger UI, and a real OAuth2 login at the
/// broker whose access token is then used against the sales API.
class BrowserE2E {

    /// The broker's seeded user and confidential client (see the oauth2broker README).
    static final String CLIENT_ID = "web-app";
    static final String CLIENT_SECRET = "web-app-secret";
    static final String REDIRECT_URI = "http://localhost:8080/callback";

    static Playwright playwright;
    static Browser browser;
    BrowserContext context;
    Page page;

    @BeforeAll
    static void launch() {
        playwright = Playwright.create();
        browser = playwright.chromium().launch();
    }

    @AfterAll
    static void close() {
        playwright.close();
    }

    @BeforeEach
    void openPage() {
        context = browser.newContext(new Browser.NewContextOptions().setIgnoreHTTPSErrors(true));
        page = context.newPage();
    }

    @AfterEach
    void closePage() {
        context.close();
    }

    @Test
    void swaggerUiListsEveryV1Endpoint() {
        page.navigate(E2E.BASE_URL + "/swagger-ui.html");

        assertThat(page).hasURL(E2E.BASE_URL + "/swagger-ui/index.html");
        assertThat(page.locator(".info .title")).containsText("Sales Online API");
        var operations = page.locator(".opblock-summary-path");
        assertThat(operations.first()).isVisible();
        var paths = new HashSet<>(operations.allTextContents().stream().map(String::strip).toList());
        assertThat(paths).hasSize(17).allMatch(path -> path.startsWith("/api/v1/"))
                .contains("/api/v1/products/{id}", "/api/v1/orders/{id}/cancel", "/api/v1/ai/ask");
        assertThat(page.locator(".btn.authorize")).isVisible();
    }

    @Test
    void brokerTokenWorksAgainstTheApi() {
        var token = signInAtBroker("alice", "wonderland");

        var api = E2E.bearer(playwright, token);
        var me = expect(api.get("/api/v1/users/me"), 200);
        assertThat(me.get("authProvider").asString()).isEqualTo("OAUTH2");
        assertThat(me.get("roles").toString()).contains("CUSTOMER").doesNotContain("ADMIN");
        expect(api.get("/api/v1/cart"), 200);
        expectProblem(api.get("/api/v1/admin/stats/categories"), 403);
    }

    /// Authorization code flow with PKCE: signs in on the broker's login page, reads the
    /// redirect to the client's callback and redeems the code.
    ///
    /// The login form posts over HTTPS and is redirected to the plain-HTTP callback, which
    /// Chromium blocks with an insecure-form interstitial. So the test fetches the login
    /// POST itself, without following the redirect, and reads its `Location` header.
    private String signInAtBroker(String username, String password) {
        var verifier = base64Url(randomBytes());
        var callback = new AtomicReference<String>();
        page.route(url -> url.startsWith(E2E.BROKER_URL + "/authorize"), route -> {
            if (!route.request().method().equals("POST")) {
                route.resume();
                return;
            }
            var redirect = route.fetch(new Route.FetchOptions().setMaxRedirects(0));
            callback.set(redirect.headers().get("location"));
            route.fulfill(new Route.FulfillOptions().setBody("signed in"));
        });

        page.navigate(E2E.BROKER_URL + "/authorize?response_type=code&client_id=" + CLIENT_ID
                + "&redirect_uri=" + REDIRECT_URI + "&scope=openid%20profile%20email&state=e2e"
                + "&code_challenge=" + base64Url(sha256(verifier)) + "&code_challenge_method=S256");
        page.locator("#username").fill(username);
        page.locator("#password").fill(password);
        page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName("Sign in")).click();
        page.waitForCondition(() -> callback.get() != null);

        assertThat(callback.get()).startsWith(REDIRECT_URI);
        var query = URI.create(callback.get()).getQuery();
        assertThat(query).contains("state=e2e");
        var code = query.replaceAll(".*code=([^&]+).*", "$1");

        var basic = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes(StandardCharsets.UTF_8));
        var tokens = expect(context.request().post(E2E.BROKER_URL + "/token", RequestOptions.create()
                .setHeader("Authorization", "Basic " + basic)
                .setForm(FormData.create()
                        .set("grant_type", "authorization_code")
                        .set("code", code)
                        .set("redirect_uri", REDIRECT_URI)
                        .set("code_verifier", verifier))), 200);
        return tokens.get("access_token").asString();
    }

    private static byte[] randomBytes() {
        var bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return bytes;
    }

    private static byte[] sha256(String text) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
