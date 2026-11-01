package com.example.sales.catalog.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/// The writable fields of a [Category].
public record CategoryData(
        @NotBlank @Pattern(regexp = "[a-z0-9-]{1,60}", message = "must be 1-60 lower-case letters, digits or '-'") String slug,
        @NotBlank @Size(max = 100) String name,
        String description) {
}
