package com.example.sales.catalog.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/// Maps the `dimensions_cm` composite type.
public record Dimensions(
        @Schema(example = "18.5") @NotNull @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal width,
        @Schema(example = "7.2") @NotNull @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal height,
        @Schema(example = "7.2") @NotNull @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal depth) {
}
