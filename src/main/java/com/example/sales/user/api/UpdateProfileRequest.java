package com.example.sales.user.api;

import com.example.sales.user.domain.PostalAddress;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

import java.util.Map;

/// Replaces the profile: a missing `defaultAddress` clears it, missing `preferences` become `{}`.
public record UpdateProfileRequest(
        @Valid PostalAddress defaultAddress,
        @Schema(example = """
                {"newsletter": true, "language": "en"}""") Map<String, Object> preferences) {
}
