package com.example.sales.cart.persistence.postgres;

import com.example.sales.cart.domain.CartItem;
import com.example.sales.cart.domain.CartRepository;
import com.example.sales.common.config.PostgresAdapter;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/// [CartRepository] on Postgres. Lines are joined with `v_product_catalog` for the current price and stock.
@PostgresAdapter
class PostgresCartRepository implements CartRepository {

    private final JdbcClient jdbc;

    PostgresCartRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<CartItem> items(UUID userId) {
        return jdbc.sql("""
                        select ci.product_id, p.name, p.price, p.currency, ci.quantity, p.stock
                        from carts c
                                 join cart_items ci on ci.cart_id = c.id
                                 join v_product_catalog p on p.id = ci.product_id
                        where c.user_id = :userId
                        order by p.name""")
                .param("userId", userId)
                .query((rs, _) -> new CartItem(
                        rs.getLong("product_id"),
                        rs.getString("name"),
                        rs.getBigDecimal("price"),
                        rs.getString("currency"),
                        rs.getInt("quantity"),
                        rs.getInt("stock")))
                .list();
    }

    @Override
    public Optional<Integer> quantity(UUID userId, long productId) {
        return jdbc.sql("""
                        select ci.quantity
                        from carts c join cart_items ci on ci.cart_id = c.id
                        where c.user_id = :userId and ci.product_id = :productId""")
                .param("userId", userId)
                .param("productId", productId)
                .query(Integer.class)
                .optional();
    }

    @Override
    public void put(UUID userId, long productId, int quantity) {
        // the no-op update makes "returning" yield the id of an existing cart
        jdbc.sql("""
                        with cart as (
                            insert into carts (user_id) values (:userId)
                            on conflict (user_id) do update set updated_at = now()
                            returning id)
                        insert into cart_items (cart_id, product_id, quantity)
                        select id, :productId, :quantity from cart
                        on conflict (cart_id, product_id) do update set quantity = excluded.quantity""")
                .param("userId", userId)
                .param("productId", productId)
                .param("quantity", quantity)
                .update();
    }

    @Override
    public boolean remove(UUID userId, long productId) {
        return jdbc.sql("""
                        delete from cart_items ci using carts c
                        where ci.cart_id = c.id and c.user_id = :userId and ci.product_id = :productId""")
                .param("userId", userId)
                .param("productId", productId)
                .update() > 0;
    }

    @Override
    public void clear(UUID userId) {
        jdbc.sql("delete from cart_items ci using carts c where ci.cart_id = c.id and c.user_id = :userId")
                .param("userId", userId)
                .update();
    }
}
