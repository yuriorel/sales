package com.example.sales.user.domain;

import com.example.sales.common.error.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

/// Registration, profile updates and resolution of the signed-in user for both
/// login types. Also the [UserDetailsService] behind HTTP Basic.
@Service
public class UserService implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserPrincipal loadUserByUsername(String username) {
        log.debug("Loading local user {}", username);
        return users.findLocal(username)
                .orElseThrow(() -> new UsernameNotFoundException("Unknown user " + username));
    }

    public User register(String username, String email, String password) {
        log.debug("Registering local user username={} email={}", username, email);
        try {
            var user = users.createLocal(username, email, passwordEncoder.encode(password));
            log.debug("Registered {}", user);
            return user;
        } catch (DuplicateKeyException _) {
            throw new ConflictException("Username or email is already registered");
        }
    }

    /// The user behind an authenticated request. A bearer-token user calling for the
    /// first time gets a row (`auth_provider='OAUTH2'`, `external_id=sub`).
    public User current(Authentication authentication) {
        return switch (authentication.getPrincipal()) {
            case UserPrincipal(var user, _) -> user;
            case Jwt jwt -> users.findByExternalId(jwt.getSubject()).orElseGet(() -> provision(jwt));
            case null, default -> throw new IllegalStateException("Unsupported principal in " + authentication);
        };
    }

    public User updateProfile(User user, PostalAddress defaultAddress, Map<String, Object> preferences) {
        log.debug("Updating profile of {}: address={} preferences={}", user.username(), defaultAddress, preferences);
        return users.updateProfile(user.id(), defaultAddress, Objects.requireNonNullElse(preferences, Map.of()));
    }

    private User provision(Jwt jwt) {
        var subject = jwt.getSubject();
        // the email column is required, but the broker may omit the claim
        var email = Objects.requireNonNullElse(jwt.getClaimAsString("email"), subject + "@oauth2.invalid");
        log.debug("Provisioning OAuth2 user sub={} email={}", subject, email);
        try {
            return users.createOAuth2(subject, subject, email);
        } catch (DuplicateKeyException _) {
            throw new ConflictException("A local account already uses the username or email of this token");
        }
    }
}
