package com.example.sales.user.api;

import com.example.sales.user.domain.Role;
import com.example.sales.user.domain.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
class UserController {

    private static final Logger log = LoggerFactory.getLogger(UserController.class);

    private final UserService users;

    UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    UserResponse me(Authentication authentication) {
        log.debug("GET /users/me as {}", authentication.getName());
        return UserResponse.of(users.current(authentication), roles(authentication));
    }

    @PutMapping("/me")
    UserResponse update(Authentication authentication, @Valid @RequestBody UpdateProfileRequest request) {
        log.debug("PUT /users/me as {}: {}", authentication.getName(), request);
        var user = users.updateProfile(users.current(authentication), request.defaultAddress(), request.preferences());
        return UserResponse.of(user, roles(authentication));
    }

    private static Set<Role> roles(Authentication authentication) {
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .flatMap(Role::fromAuthority)
                .collect(Collectors.toUnmodifiableSet());
    }
}
