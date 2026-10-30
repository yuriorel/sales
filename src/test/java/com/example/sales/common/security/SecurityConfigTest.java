package com.example.sales.common.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityConfigTest {

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

    private static List<String> roles(Consumer<Jwt.Builder> claims) {
        var jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject("alice");
        claims.accept(jwt);
        return SecurityConfig.jwtRoles().convert(jwt.build()).stream().map(GrantedAuthority::getAuthority).toList();
    }
}
