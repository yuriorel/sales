package com.example.sales.user.domain;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// A row of the `users` table, without the password hash.
///
/// @param roles stored roles. They grant authorities to `LOCAL` users only;
///              `OAUTH2` users get their roles from the token's `scope` claim
public record User(
        UUID id,
        String username,
        String email,
        AuthProvider authProvider,
        String externalId,
        Set<Role> roles,
        PostalAddress defaultAddress,
        Map<String, Object> preferences,
        Instant createdAt) {
}
