package com.example.sales.cart.api;

import com.example.sales.cart.domain.Cart;
import com.example.sales.cart.domain.CartService;
import com.example.sales.user.domain.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/// The cart of the signed-in user.
@RestController
@RequestMapping("/api/v1/cart")
class CartController {

    private static final Logger log = LoggerFactory.getLogger(CartController.class);

    private final CartService carts;
    private final UserService users;

    CartController(CartService carts, UserService users) {
        this.carts = carts;
        this.users = users;
    }

    record AddItemRequest(@NotNull @Positive Long productId, @NotNull @Positive Integer quantity) {
    }

    record QuantityRequest(@NotNull @Positive Integer quantity) {
    }

    @GetMapping
    Cart get(Authentication authentication) {
        log.debug("GET /cart as {}", authentication.getName());
        return carts.get(users.current(authentication));
    }

    /// Adds the quantity to the product's line; the new total must not exceed the stock.
    @PostMapping("/items")
    Cart add(Authentication authentication, @Valid @RequestBody AddItemRequest request) {
        log.debug("POST /cart/items as {}: {}", authentication.getName(), request);
        return carts.add(users.current(authentication), request.productId(), request.quantity());
    }

    @PatchMapping("/items/{productId}")
    Cart update(Authentication authentication, @PathVariable long productId, @Valid @RequestBody QuantityRequest request) {
        log.debug("PATCH /cart/items/{} as {}: {}", productId, authentication.getName(), request);
        return carts.update(users.current(authentication), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    Cart remove(Authentication authentication, @PathVariable long productId) {
        log.debug("DELETE /cart/items/{} as {}", productId, authentication.getName());
        return carts.remove(users.current(authentication), productId);
    }

    @DeleteMapping
    ResponseEntity<Void> clear(Authentication authentication) {
        log.debug("DELETE /cart as {}", authentication.getName());
        carts.clear(users.current(authentication));
        return ResponseEntity.noContent().build();
    }
}
