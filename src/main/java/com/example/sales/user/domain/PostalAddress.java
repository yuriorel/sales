package com.example.sales.user.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/// Maps the `postal_address` composite type.
public record PostalAddress(
        @Schema(example = "1 Main Street") @NotBlank @Size(max = 200) String line1,
        @Schema(example = "Apt 4") @Size(max = 200) String line2,
        @Schema(example = "Springfield") @NotBlank @Size(max = 100) String city,
        @Schema(example = "IL") @Size(max = 100) String region,
        @Schema(example = "62701") @NotBlank @Size(max = 20) String postalCode,
        @Schema(example = "US") @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO 3166-1 alpha-2 code") String countryCode) {
}
