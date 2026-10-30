package com.example.sales.user.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_.]{3,50}", message = "must be 3-50 letters, digits, '_' or '.'") String username,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 8, max = 72) String password) {

    /// Keeps the password out of log output.
    @Override
    public String toString() {
        return "RegisterRequest[username=" + username + ", email=" + email + "]";
    }
}
