package com.example.sales.catalog.domain;

import io.swagger.v3.oas.annotations.media.Schema;
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
        @Schema(example = "AUR-SPK-001") @NotBlank @Pattern(regexp = "[A-Z0-9-]{4,32}", message = "must be 4-32 upper-case letters, digits or '-'") String sku,
        @Schema(example = "3") @NotNull @Positive Long categoryId,
        @Schema(example = "Aurora Speaker") @NotBlank @Size(max = 200) String name,
        @Schema(example = "Aurora") @Size(max = 100) String brand,
        @Schema(example = "Portable Bluetooth speaker with 20 hours of playback") @NotBlank String description,
        @Schema(example = "59.00") @NotNull @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal price,
        @Schema(example = "USD") @Pattern(regexp = "[A-Z]{3}", message = "must be an ISO 4217 code") String currency,
        @Schema(example = "4.5") @DecimalMin("0") @DecimalMax("5") @Digits(integer = 1, fraction = 1) BigDecimal rating,
        @Schema(example = "[\"audio\", \"bluetooth\"]") List<@NotBlank String> tags,
        @Schema(example = "[\"https://example.com/aurora.png\"]") List<@NotBlank String> imageUrls,
        @Valid Dimensions dimensions,
        @Schema(example = """
                {"weight": "0.6 kg", "color": "black", "warranty": "2 years"}""") Map<String, Object> attributes,
        @Schema(example = """
                # Overview
                The Aurora speaker is a portable Bluetooth speaker.

                # Pairing
                Hold the Bluetooth button for 7 seconds until the LED blinks purple.
                """) @NotBlank String userGuide,
        @Schema(example = "[\"Overview\", \"Pairing\"]") List<@NotBlank String> guideSections) {

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
