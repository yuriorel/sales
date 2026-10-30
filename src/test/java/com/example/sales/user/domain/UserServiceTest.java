package com.example.sales.user.domain;

import com.example.sales.common.error.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserServiceTest {

    final UserRepository repository = mock(UserRepository.class);
    final PasswordEncoder encoder = mock(PasswordEncoder.class);
    final UserService service = new UserService(repository, encoder);

    @Test
    void registerStoresEncodedPassword() {
        var user = user("bob", AuthProvider.LOCAL);
        when(encoder.encode("secret-123")).thenReturn("hash");
        when(repository.createLocal("bob", "bob@example.com", "hash")).thenReturn(user);

        assertThat(service.register("bob", "bob@example.com", "secret-123")).isEqualTo(user);
    }

    @Test
    void registerDuplicateIsConflict() {
        when(encoder.encode(any())).thenReturn("hash");
        when(repository.createLocal(any(), any(), any())).thenThrow(new DuplicateKeyException("users_username_key"));

        assertThatThrownBy(() -> service.register("bob", "bob@example.com", "secret-123"))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Username or email is already registered");
    }

    @Test
    void unknownLocalUserIsNotFound() {
        when(repository.findLocal("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("ghost")).isInstanceOf(UsernameNotFoundException.class);
    }

    @Test
    void currentLocalUserComesFromPrincipal() {
        var user = user("bob", AuthProvider.LOCAL);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(new UserPrincipal(user, "hash"), null, List.of());

        assertThat(service.current(authentication)).isEqualTo(user);
    }

    @Test
    void knownJwtUserIsNotProvisionedAgain() {
        var user = user("alice", AuthProvider.OAUTH2);
        when(repository.findByExternalId("alice")).thenReturn(Optional.of(user));

        assertThat(service.current(jwt("alice", "alice@example.com"))).isEqualTo(user);
        verify(repository, never()).createOAuth2(any(), any(), any());
    }

    @Test
    void newJwtUserIsProvisioned() {
        var user = user("alice", AuthProvider.OAUTH2);
        when(repository.findByExternalId("alice")).thenReturn(Optional.empty());
        when(repository.createOAuth2("alice", "alice", "alice@example.com")).thenReturn(user);

        assertThat(service.current(jwt("alice", "alice@example.com"))).isEqualTo(user);
    }

    @Test
    void jwtWithoutEmailGetsPlaceholderEmail() {
        when(repository.findByExternalId("carol")).thenReturn(Optional.empty());

        service.current(jwt("carol", null));

        verify(repository).createOAuth2("carol", "carol", "carol@oauth2.invalid");
    }

    @Test
    void jwtClashingWithLocalAccountIsConflict() {
        when(repository.findByExternalId("bob")).thenReturn(Optional.empty());
        when(repository.createOAuth2(any(), any(), any())).thenThrow(new DuplicateKeyException("users_username_key"));

        assertThatThrownBy(() -> service.current(jwt("bob", "bob@example.com"))).isInstanceOf(ConflictException.class);
    }

    @Test
    void missingPreferencesBecomeEmpty() {
        var user = user("bob", AuthProvider.LOCAL);

        service.updateProfile(user, null, null);

        verify(repository).updateProfile(user.id(), null, Map.of());
    }

    private static User user(String username, AuthProvider provider) {
        return new User(UUID.randomUUID(), username, username + "@example.com", provider,
                provider == AuthProvider.OAUTH2 ? username : null, Set.of(Role.CUSTOMER), null, Map.of(), Instant.now());
    }

    private static JwtAuthenticationToken jwt(String subject, String email) {
        var jwt = Jwt.withTokenValue("token").header("alg", "RS256").subject(subject);
        if (email != null) {
            jwt.claim("email", email);
        }
        return new JwtAuthenticationToken(jwt.build());
    }
}
