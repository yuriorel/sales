package com.example.sales.user.domain;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/// Persistence port for users.
public interface UserRepository {

    /// A `LOCAL` user together with its password hash.
    Optional<UserPrincipal> findLocal(String username);

    Optional<User> findByExternalId(String externalId);

    /// Inserts a `LOCAL` user with the default `CUSTOMER` role.
    User createLocal(String username, String email, String passwordHash);

    /// Inserts an `OAUTH2` user, or returns the existing row for `externalId`.
    User createOAuth2(String externalId, String username, String email);

    /// Replaces the default address (`null` clears it) and the preferences.
    User updateProfile(UUID id, PostalAddress defaultAddress, Map<String, Object> preferences);
}
