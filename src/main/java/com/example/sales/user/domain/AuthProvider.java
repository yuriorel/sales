package com.example.sales.user.domain;

/// How a user signs in: `LOCAL` with username and password (HTTP Basic),
/// `OAUTH2` with a bearer token from the external OAuth2 broker.
public enum AuthProvider {
    LOCAL,
    OAUTH2
}
