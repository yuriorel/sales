package com.example.sales.user.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/// Maps the `postal_address` composite type.
public record PostalAddress(
        @NotBlank @Size(max = 200) String line1,
        @Size(max = 200) String line2,
        @NotBlank @Size(max = 100) String city,
        @Size(max = 100) String region,
        @NotBlank @Size(max = 20) String postalCode,
        @NotBlank @Pattern(regexp = "[A-Z]{2}", message = "must be an ISO 3166-1 alpha-2 code") String countryCode) {
}
