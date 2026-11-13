package com.example.sales.cart.domain;

import com.example.sales.catalog.domain.InventoryRepository;
import com.example.sales.catalog.domain.Product;
import com.example.sales.catalog.domain.ProductRepository;
import com.example.sales.common.error.ConflictException;
import com.example.sales.common.error.NotFoundException;
import com.example.sales.order.OrderFixtures;
import com.example.sales.user.domain.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CartServiceTest {

    final CartRepository carts = mock(CartRepository.class);
    final ProductRepository products = mock(ProductRepository.class);
    final InventoryRepository inventory = mock(InventoryRepository.class);
    final CartService service = new CartService(carts, products, inventory);
    final User user = OrderFixtures.user(null);

    @Test
    void addIncreasesExistingQuantityWithinStock() {
        when(products.findById(7)).thenReturn(Optional.of(mock(Product.class)));
        when(carts.quantity(user.id(), 7)).thenReturn(Optional.of(2));
        when(inventory.quantity(7)).thenReturn(5);

        service.add(user, 7, 3);

        verify(carts).put(user.id(), 7, 5);
    }

    @Test
    void addBeyondStockIsConflict() {
        when(products.findById(7)).thenReturn(Optional.of(mock(Product.class)));
        when(carts.quantity(user.id(), 7)).thenReturn(Optional.of(2));
        when(inventory.quantity(7)).thenReturn(4);

        assertThatThrownBy(() -> service.add(user, 7, 3))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Only 4 of product 7 in stock, requested 5");
        verify(carts, never()).put(any(), anyLong(), anyInt());
    }

    @Test
    void addUnknownProductIsNotFound() {
        when(products.findById(8)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.add(user, 8, 1)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void updateOfProductNotInCartIsNotFound() {
        when(carts.quantity(user.id(), 7)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(user, 7, 1))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Cart item '7' was not found");
    }

    @Test
    void removeOfProductNotInCartIsNotFound() {
        when(carts.remove(user.id(), 7)).thenReturn(false);

        assertThatThrownBy(() -> service.remove(user, 7)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void cartTotalSumsTheLines() {
        when(carts.items(user.id())).thenReturn(List.of(
                new CartItem(1, "A", new BigDecimal("10.50"), "USD", 2, 9),
                new CartItem(2, "B", new BigDecimal("0.99"), "USD", 1, 9)));

        assertThat(service.get(user).total()).isEqualByComparingTo("21.99");
    }
}
