package com.example.sales.user.api;

import com.example.sales.support.TestJwt;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// Registration and profile endpoints against the test schema.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestJwt.class)
class UserApiIT {

    static final String PASSWORD = "secret-123";

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Test
    void registerCreatesCustomer() throws Exception {
        var username = unique();

        mvc.perform(register(username, username + "@example.com"))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, "/api/v1/users/me"))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.email").value(username + "@example.com"))
                .andExpect(jsonPath("$.roles[0]").value("CUSTOMER"))
                .andExpect(jsonPath("$.preferences").isEmpty())
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    void duplicateUsernameOrEmailIsConflict() throws Exception {
        var username = unique();
        mvc.perform(register(username, username + "@example.com")).andExpect(status().isCreated());

        mvc.perform(register(username, unique() + "@example.com"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Username or email is already registered"));
        // email is citext, so the check ignores case
        mvc.perform(register(unique(), username.toUpperCase() + "@EXAMPLE.COM"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidRegistrationListsErrors() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"username": "a b", "email": "not-an-email", "password": "short"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    @Test
    void profileUpdateRoundTripsAddressAndPreferences() throws Exception {
        var username = unique();
        mvc.perform(register(username, username + "@example.com")).andExpect(status().isCreated());

        mvc.perform(put("/api/v1/users/me").with(httpBasic(username, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {
                                  "defaultAddress": {"line1": "1 Main St", "city": "Springfield",
                                                     "postalCode": "12345", "countryCode": "US"},
                                  "preferences": {"currency": "EUR", "newsletter": true, "categories": ["audio", "books"]}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultAddress.city").value("Springfield"));

        mvc.perform(get("/api/v1/users/me").with(httpBasic(username, PASSWORD)))
                .andExpect(jsonPath("$.defaultAddress.line1").value("1 Main St"))
                .andExpect(jsonPath("$.defaultAddress.line2").doesNotExist())
                .andExpect(jsonPath("$.defaultAddress.countryCode").value("US"))
                .andExpect(jsonPath("$.preferences.currency").value("EUR"))
                .andExpect(jsonPath("$.preferences.newsletter").value(true))
                .andExpect(jsonPath("$.preferences.categories[1]").value("books"));

        mvc.perform(put("/api/v1/users/me").with(httpBasic(username, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultAddress").doesNotExist())
                .andExpect(jsonPath("$.preferences").isEmpty());
    }

    @Test
    void invalidAddressIsRejected() throws Exception {
        var username = unique();
        mvc.perform(register(username, username + "@example.com")).andExpect(status().isCreated());

        mvc.perform(put("/api/v1/users/me").with(httpBasic(username, PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"defaultAddress": {"line1": "1 Main St", "city": "Springfield",
                                                    "postalCode": "12345", "countryCode": "usa"}}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("defaultAddress.countryCode"));
    }

    @Test
    void bearerUserIsProvisionedOnce() throws Exception {
        var subject = unique();
        var token = "Bearer " + TestJwt.token(subject, _ -> { });

        var first = mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(subject + "@example.com"))
                .andReturn().getResponse().getContentAsString();
        var second = mvc.perform(get("/api/v1/users/me").header(HttpHeaders.AUTHORIZATION, token))
                .andReturn().getResponse().getContentAsString();

        assertThat(json.readTree(second).get("id")).isEqualTo(json.readTree(first).get("id"));
    }

    private static MockHttpServletRequestBuilder register(String username, String email) {
        return post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"username": "%s", "email": "%s", "password": "%s"}
                """.formatted(username, email, PASSWORD));
    }

    private static String unique() {
        return "user_" + System.nanoTime();
    }
}
