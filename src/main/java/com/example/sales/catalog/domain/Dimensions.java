package com.example.sales.catalog.domain;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/// Maps the `dimensions_cm` composite type.
public record Dimensions(
        @NotNull @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal width,
        @NotNull @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal height,
        @NotNull @DecimalMin("0") @Digits(integer = 6, fraction = 2) BigDecimal depth) {
}
