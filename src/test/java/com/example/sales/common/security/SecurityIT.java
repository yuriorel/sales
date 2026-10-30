package com.example.sales.common.security;

import com.example.sales.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import java.time.Instant;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// The security filter chain end to end: HTTP Basic against the test schema and bearer
/// tokens signed by [TestJwt]. Admin paths have no handlers before Phase 5, so a request
/// that passes authorization is checked only for "not 401/403".
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwt.class)
class SecurityIT {

    static final String ME = "/api/v1/users/me";
    static final String ADMIN_PATH = "/api/v1/admin/stats/categories";
    static final String PASSWORD = "secret-123";

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcClient jdbc;

    @Test
    void noCredentialsIsUnauthorizedProblem() throws Exception {
        mvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, containsString("Basic")))
                .andExpect(jsonPath("$.detail").value("Authentication required"))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void publicEndpointsNeedNoCredentials() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void basicLoginWithStoredRoles() throws Exception {
        var username = register();

        mvc.perform(get(ME).with(httpBasic(username, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(username))
                .andExpect(jsonPath("$.authProvider").value("LOCAL"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("CUSTOMER")));
    }

    @Test
    void wrongPasswordIsUnauthorized() throws Exception {
        var username = register();

        mvc.perform(get(ME).with(httpBasic(username, "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Invalid username or password"));
    }

    @Test
    void bearerTokenSignsInAsCustomer() throws Exception {
        var subject = unique("jwt");

        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, bearer(subject, _ -> { })))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(subject))
                .andExpect(jsonPath("$.authProvider").value("OAUTH2"))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("CUSTOMER")));
    }

    @Test
    void adminScopeGivesAdminRole() throws Exception {
        var token = bearer(unique("jwt"), claims -> claims.claim("scope", "openid ADMIN"));

        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("CUSTOMER", "ADMIN")));
        mvc.perform(get(ADMIN_PATH).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(passedSecurity());
    }

    @ParameterizedTest
    @ValueSource(strings = {"wrong-issuer", "expired", "foreign-key", "garbage"})
    void invalidBearerTokenIsUnauthorized(String kind) throws Exception {
        var subject = unique("jwt");
        var token = switch (kind) {
            case "wrong-issuer" -> bearer(subject, claims -> claims.issuer("https://evil.example.com"));
            case "expired" -> bearer(subject, claims -> claims
                    .issuedAt(Instant.now().minusSeconds(3600))
                    .expiresAt(Instant.now().minusSeconds(600)));
            case "foreign-key" -> "Bearer " + TestJwt.token(TestJwt.rsaKeyPair(), subject, _ -> { });
            default -> "Bearer not-a-jwt";
        };

        mvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Invalid or expired bearer token"));
    }

    @Test
    void customerIsForbiddenOnAdminEndpoints() throws Exception {
        var username = register();

        mvc.perform(get(ADMIN_PATH).with(httpBasic(username, PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value("Access denied"));
        mvc.perform(post("/api/v1/products").with(httpBasic(username, PASSWORD)))
                .andExpect(status().isForbidden());
        mvc.perform(get(ADMIN_PATH).header(HttpHeaders.AUTHORIZATION, bearer(unique("jwt"), _ -> { })))
                .andExpect(status().isForbidden());
    }

    @Test
    void localAdminRoleComesFromDatabase() throws Exception {
        var username = register();
        jdbc.sql("update users set roles = '{CUSTOMER,ADMIN}' where username = :username")
                .param("username", username)
                .update();

        mvc.perform(get(ADMIN_PATH).with(httpBasic(username, PASSWORD)))
                .andExpect(passedSecurity());
    }

    @Test
    void mockJwtUsesTheRoleMapping() throws Exception {
        var subject = unique("mock");

        mvc.perform(get(ME).with(jwt()
                        .jwt(token -> token.subject(subject).claim("email", subject + "@example.com").claim("scope", "ADMIN"))
                        .authorities(SecurityConfig.jwtRoles())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(subject))
                .andExpect(jsonPath("$.roles", containsInAnyOrder("CUSTOMER", "ADMIN")));
    }

    private String register() throws Exception {
        var username = unique("basic");
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "%s", "email": "%s@example.com", "password": "%s"}
                        """.formatted(username, username, PASSWORD)))
                .andExpect(status().isCreated());
        return username;
    }

    private static String bearer(String subject, Consumer<JwtClaimsSet.Builder> claims) {
        return "Bearer " + TestJwt.token(subject, claims);
    }

    private static String unique(String prefix) {
        return prefix + "_" + System.nanoTime();
    }

    private static ResultMatcher passedSecurity() {
        return result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }
}
