package com.example.sales.common.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import java.util.HashSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/// API versioning and the generated OpenAPI document.
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiDocsIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @ParameterizedTest
    @ValueSource(strings = {"v9", "v1.1", "vx"})
    void unsupportedVersionIsProblem(String version) throws Exception {
        mvc.perform(get("/api/" + version + "/categories"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.detail").value(startsWith("Invalid API version")))
                .andExpect(jsonPath("$.correlationId").exists());
    }

    @Test
    void supportedVersionIsServed() throws Exception {
        mvc.perform(get("/api/v1/categories")).andExpect(status().isOk());
    }

    @Test
    void apiDocsListEveryV1Path() throws Exception {
        var body = mvc.perform(get("/v3/api-docs/v1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Sales Online API"))
                .andExpect(jsonPath("$.components.securitySchemes.basicAuth.scheme").value("basic"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.components.schemas.RegisterRequest.properties.username.example").value("jane.doe"))
                .andReturn().getResponse().getContentAsString();

        var paths = new HashSet<String>();
        json.readTree(body).get("paths").propertyNames().forEach(paths::add);
        assertThat(paths).containsExactlyInAnyOrder(
                "/api/v1/auth/register",
                "/api/v1/users/me",
                "/api/v1/categories",
                "/api/v1/categories/{id}",
                "/api/v1/products",
                "/api/v1/products/{id}",
                "/api/v1/products/{id}/guide",
                "/api/v1/products/{id}/inventory",
                "/api/v1/admin/stats/categories",
                "/api/v1/search",
                "/api/v1/ai/ask",
                "/api/v1/cart",
                "/api/v1/cart/items",
                "/api/v1/cart/items/{productId}",
                "/api/v1/orders",
                "/api/v1/orders/{id}",
                "/api/v1/orders/{id}/cancel");
    }

    @Test
    void swaggerUiIsPublicAndListsTheV1Group() throws Exception {
        mvc.perform(get("/swagger-ui.html")).andExpect(redirectedUrl("/swagger-ui/index.html"));
        mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        mvc.perform(get("/v3/api-docs/swagger-config"))
                .andExpect(jsonPath("$.urls[0].name").value("v1"))
                .andExpect(jsonPath("$.urls[0].url").value("/v3/api-docs/v1"));
    }
}
