package com.example.sales.catalog.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/// The writable fields of a [Category].
public record CategoryData(
        @Schema(example = "smart-home") @NotBlank @Pattern(regexp = "[a-z0-9-]{1,60}", message = "must be 1-60 lower-case letters, digits or '-'") String slug,
        @Schema(example = "Smart Home") @NotBlank @Size(max = 100) String name,
        @Schema(example = "Connected lights, plugs and sensors") String description) {
}
