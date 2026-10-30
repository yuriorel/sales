package com.example.sales.user.api;

import com.example.sales.user.domain.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final UserService users;

    AuthController(UserService users) {
        this.users = users;
    }

    /// Registers a `LOCAL` user with the `CUSTOMER` role, who then signs in with HTTP Basic.
    @PostMapping("/register")
    ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.debug("POST /auth/register {}", request);
        var user = users.register(request.username(), request.email(), request.password());
        return ResponseEntity.created(URI.create("/api/v1/users/me")).body(UserResponse.of(user, user.roles()));
    }
}
