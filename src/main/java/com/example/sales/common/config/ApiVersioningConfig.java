package com.example.sales.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.HandlerTypePredicate;
import org.springframework.web.servlet.config.annotation.ApiVersionConfigurer;
import org.springframework.web.servlet.config.annotation.PathMatchConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/// API versioning with the Spring Framework 7 built-in support.
///
/// Every controller of this application is mapped under `/api/v{version}` and declares
/// its version, for example `@RequestMapping(path = "/orders", version = "1")`. The
/// version is read from the second path segment (`v1` parses as `1`). A version that is
/// not supported, such as `/api/v9/orders`, is rejected with a `400` problem response.
///
/// A future `v2` adds controllers with `version = "2"` and lists `"2"` here, while the
/// `v1` controllers keep serving existing clients.
@Configuration
class ApiVersioningConfig implements WebMvcConfigurer {

    @Override
    public void configurePathMatch(PathMatchConfigurer configurer) {
        configurer.addPathPrefix("/api/v{version}", HandlerTypePredicate.forBasePackage("com.example.sales"));
    }

    @Override
    public void configureApiVersioning(ApiVersionConfigurer configurer) {
        configurer
                // only API paths carry a version; actuator and the OpenAPI endpoints do not
                .usePathSegment(1, path -> path.pathWithinApplication().value().startsWith("/api/"))
                .addSupportedVersions("1");
    }
}
