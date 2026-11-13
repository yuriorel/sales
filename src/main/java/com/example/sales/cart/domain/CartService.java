package com.example.sales.cart.domain;

import com.example.sales.catalog.domain.InventoryRepository;
import com.example.sales.catalog.domain.ProductRepository;
import com.example.sales.common.error.ConflictException;
import com.example.sales.common.error.NotFoundException;
import com.example.sales.user.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/// The cart of the signed-in user. Adding or changing a line checks the current stock;
/// stock is reserved only when an order is processed.
@Service
public class CartService {

    private static final Logger log = LoggerFactory.getLogger(CartService.class);

    private final CartRepository carts;
    private final ProductRepository products;
    private final InventoryRepository inventory;

    public CartService(CartRepository carts, ProductRepository products, InventoryRepository inventory) {
        this.carts = carts;
        this.products = products;
        this.inventory = inventory;
    }

    public Cart get(User user) {
        var cart = Cart.of(carts.items(user.id()));
        log.debug("Cart of {}: {}", user.username(), cart);
        return cart;
    }

    /// Adds `quantity` to the product's line, creating the line when it is missing.
    public Cart add(User user, long productId, int quantity) {
        log.debug("Adding {} x product {} to the cart of {}", quantity, productId, user.username());
        if (products.findById(productId).isEmpty()) {
            throw new NotFoundException("Product", productId);
        }
        put(user, productId, carts.quantity(user.id(), productId).orElse(0) + quantity);
        return get(user);
    }

    public Cart update(User user, long productId, int quantity) {
        log.debug("Setting product {} to {} in the cart of {}", productId, quantity, user.username());
        if (carts.quantity(user.id(), productId).isEmpty()) {
            throw new NotFoundException("Cart item", productId);
        }
        put(user, productId, quantity);
        return get(user);
    }

    public Cart remove(User user, long productId) {
        log.debug("Removing product {} from the cart of {}", productId, user.username());
        if (!carts.remove(user.id(), productId)) {
            throw new NotFoundException("Cart item", productId);
        }
        return get(user);
    }

    public void clear(User user) {
        log.debug("Clearing the cart of {}", user.username());
        carts.clear(user.id());
    }

    private void put(User user, long productId, int quantity) {
        var stock = inventory.quantity(productId);
        if (quantity > stock) {
            throw new ConflictException("Only %d of product %d in stock, requested %d".formatted(stock, productId, quantity));
        }
        carts.put(user.id(), productId, quantity);
    }
}
