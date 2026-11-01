package com.example.sales.catalog.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/// The writable fields of a [Product]. Missing collections become empty and a
/// missing currency becomes `USD`.
public record ProductData(
        @NotBlank @Pattern(regexp = "[A-Z0-9-]{4,32}", message = "must be 4-32 upper-case letters, digits or '-'") String sku,
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 200) String name,
        @Size(max = 100) String brand,
        @NotBlank String description,
        @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code") String currency,
        @DecimalMin("0") @DecimalMax("5") @Digits(integer = 1, fraction = 1) BigDecimal rating,
        List<@NotBlank String> tags,
        List<@NotBlank String> imageUrls,
        @Valid Dimensions dimensions,
        Map<String, Object> attributes,
        @NotBlank String userGuide,
        List<@NotBlank String> guideSections) {

    public ProductData {
        currency = Objects.requireNonNullElse(currency, "USD");
        tags = Objects.requireNonNullElse(tags, List.of());
        imageUrls = Objects.requireNonNullElse(imageUrls, List.of());
        attributes = Objects.requireNonNullElse(attributes, Map.of());
        guideSections = Objects.requireNonNullElse(guideSections, List.of());
    }

    /// Keeps the (long) user guide out of log output.
    @Override
    public String toString() {
        return "ProductData[sku=%s, categoryId=%d, name=%s, price=%s %s, tags=%s, attributes=%s, guide=%d chars]"
                .formatted(sku, categoryId, name, price, currency, tags, attributes,
                        userGuide == null ? 0 : userGuide.length());
    }
}
