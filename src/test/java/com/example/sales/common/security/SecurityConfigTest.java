package com.example.sales.common.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

    static final String ISSUER = "https://localhost:8443";

    @Test
    void adminScopeAddsAdminRole() {
        assertThat(roles(jwt -> jwt.claim("scope", "openid ADMIN email")))
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    void scopeListIsSupported() {
        assertThat(roles(jwt -> jwt.claim("scope", List.of("openid", "ADMIN"))))
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "ROLE_ADMIN");
    }

    @Test
    void everyTokenIsCustomer() {
        assertThat(roles(jwt -> jwt.claim("scope", "openid profile"))).containsExactly("ROLE_CUSTOMER");
        assertThat(roles(_ -> { })).containsExactly("ROLE_CUSTOMER");
    }

    @Test
    void scopesThatAreNotExactRoleNamesAreIgnored() {
        assertThat(roles(jwt -> jwt.claim("scope", "admin SUPERUSER CUSTOMER"))).containsExactly("ROLE_CUSTOMER");
    }

    @ParameterizedTest
    @ValueSource(strings = {"at+jwt", "JWT"})
    void accessTokenAndPlainJwtTypesAreValid(String type) {
        assertThat(validate(jwt -> jwt.header("typ", type)).hasErrors()).isFalse();
    }

    @Test
    void otherTokenTypesAreRejected() {
        assertThat(validate(jwt -> jwt.header("typ", "logout+jwt")).hasErrors()).isTrue();
        assertThat(validate(_ -> { }).hasErrors()).as("no typ header").isTrue();
    }

    @Test
    void wrongIssuerAndExpiredTokensAreRejected() {
        assertThat(validate(jwt -> jwt.header("typ", "at+jwt").issuer("https://evil.example")).hasErrors()).isTrue();
        assertThat(validate(jwt -> jwt.header("typ", "at+jwt")
                .issuedAt(Instant.now().minusSeconds(3600)).expiresAt(Instant.now().minusSeconds(600))).hasErrors()).isTrue();
    }

    private static OAuth2TokenValidatorResult validate(Consumer<Jwt.Builder> customizer) {
        var jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("alice").issuer(ISSUER)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        customizer.accept(jwt);
        return SecurityConfig.jwtValidator(ISSUER).validate(jwt.build());
    }

    private static List<String> roles(Consumer<Jwt.Builder> claims) {
        var jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("alice");
        claims.accept(jwt);
        return SecurityConfig.jwtRoles().convert(jwt.build()).stream().map(GrantedAuthority::getAuthority).toList();
    }
}
