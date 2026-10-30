package com.example.sales.user.api;

import com.example.sales.user.domain.AuthProvider;
import com.example.sales.user.domain.PostalAddress;
import com.example.sales.user.domain.Role;
import com.example.sales.user.domain.User;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// A user profile.
///
/// @param roles the caller's effective roles: stored roles for `LOCAL` users, token scopes for `OAUTH2` users
public record UserResponse(
        UUID id,
        String username,
        String email,
        AuthProvider authProvider,
        Set<Role> roles,
        PostalAddress defaultAddress,
        Map<String, Object> preferences,
        Instant createdAt) {

    static UserResponse of(User user, Set<Role> roles) {
        return new UserResponse(user.id(), user.username(), user.email(), user.authProvider(), roles,
                user.defaultAddress(), user.preferences(), user.createdAt());
    }
}
