package com.example.sales.user.domain;

import java.util.Arrays;
import java.util.stream.Stream;

/// Application roles. Spring Security sees them as `ROLE_<name>` authorities.
public enum Role {
    CUSTOMER,
    ADMIN;

    public static final String AUTHORITY_PREFIX = "ROLE_";

    public String authority() {
        return AUTHORITY_PREFIX + name();
    }

    /// The role with this exact name, or an empty stream for any other value
    /// (for example an OAuth2 scope such as `openid`).
    public static Stream<Role> named(String name) {
        return Arrays.stream(values()).filter(role -> role.name().equals(name));
    }

    /// The role behind a `ROLE_<name>` authority, or an empty stream.
    public static Stream<Role> fromAuthority(String authority) {
        return authority.startsWith(AUTHORITY_PREFIX)
                ? named(authority.substring(AUTHORITY_PREFIX.length()))
                : Stream.empty();
    }
}
