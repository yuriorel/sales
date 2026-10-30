package com.example.sales.user.domain;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/// The principal of a `LOCAL` user signed in with HTTP Basic. Its authorities are the stored roles.
public record UserPrincipal(User user, String passwordHash) implements UserDetails {

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return user.roles().stream().map(role -> new SimpleGrantedAuthority(role.authority())).toList();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return user.username();
    }

    /// Keeps the password hash out of log output.
    @Override
    public String toString() {
        return "UserPrincipal[user=" + user + "]";
    }
}
