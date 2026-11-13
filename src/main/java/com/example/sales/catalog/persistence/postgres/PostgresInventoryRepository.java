package com.example.sales.catalog.persistence.postgres;

import com.example.sales.catalog.domain.Inventory;
import com.example.sales.catalog.domain.InventoryRepository;
import com.example.sales.common.config.PostgresAdapter;
import org.springframework.jdbc.core.simple.JdbcClient;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@PostgresAdapter
class PostgresInventoryRepository implements InventoryRepository {

    private final JdbcClient jdbc;

    PostgresInventoryRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public int quantity(long productId) {
        return jdbc.sql("select quantity from inventory where product_id = :productId")
                .param("productId", productId)
                .query(Integer.class)
                .optional()
                .orElse(0);
    }

    @Override
    public Optional<Inventory> set(long productId, int quantity) {
        // selecting from products makes an unknown product yield no row instead of a foreign key error
        return jdbc.sql("""
                        insert into inventory (product_id, quantity)
                        select id, :quantity from products where id = :productId
                        on conflict (product_id) do update set quantity = excluded.quantity, updated_at = now()
                        returning product_id, quantity, updated_at""")
                .param("productId", productId)
                .param("quantity", quantity)
                .query((rs, _) -> new Inventory(
                        rs.getLong("product_id"),
                        rs.getInt("quantity"),
                        rs.getObject("updated_at", OffsetDateTime.class).toInstant()))
                .optional();
    }

    @Override
    public Map<Long, Integer> lock(Collection<Long> productIds) {
        // a fixed lock order keeps two orders with the same products from deadlocking
        return jdbc.sql("""
                        select product_id, quantity from inventory
                        where product_id = any(:productIds)
                        order by product_id
                        for update""")
                .param("productIds", productIds.toArray(Long[]::new))
                .query((rs, _) -> Map.entry(rs.getLong("product_id"), rs.getInt("quantity")))
                .list().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public void adjust(long productId, int delta) {
        jdbc.sql("update inventory set quantity = quantity + :delta, updated_at = now() where product_id = :productId")
                .param("productId", productId)
                .param("delta", delta)
                .update();
    }
}
