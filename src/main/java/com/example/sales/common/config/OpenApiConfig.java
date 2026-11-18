package com.example.sales.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;

/// Code-first OpenAPI: springdoc builds the document from the controllers and records.
/// Served at `/v3/api-docs/v1` (one group per API version) and rendered by Swagger UI at
/// `/swagger-ui.html`.
@Configuration
class OpenApiConfig {

    private static final String BASIC = "basicAuth";
    private static final String BEARER = "bearerAuth";

    @Bean
    OpenAPI salesOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Sales Online API")
                        .version("1")
                        .description("""
                                E-commerce engine: catalog, inventory, cart, asynchronous orders,
                                full-text/semantic/hybrid search and AI questions over product user guides.

                                Authenticate with HTTP Basic (register at `POST /api/v1/auth/register`)
                                or with a bearer JWT from the OAuth2 broker. Catalog and search reads are public.
                                Errors are `application/problem+json` (RFC 9457) with a `correlationId`.
                                """))
                .components(new Components()
                        .addSecuritySchemes(BASIC, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("basic"))
                        .addSecuritySchemes(BEARER, new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                // either scheme is accepted
                .addSecurityItem(new SecurityRequirement().addList(BASIC))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }

    @Bean
    GroupedOpenApi v1() {
        return GroupedOpenApi.builder()
                .group("v1")
                .addOpenApiMethodFilter(method -> declaresVersion(method, "1"))
                .build();
    }

    /// Groups match on the controller's declared `version`: springdoc filters paths
    /// before it substitutes `{version}`, so `pathsToMatch("/api/v1/**")` matches nothing.
    private static boolean declaresVersion(Method method, String version) {
        var mapping = AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), RequestMapping.class);
        return mapping != null && version.equals(mapping.version());
    }
}
