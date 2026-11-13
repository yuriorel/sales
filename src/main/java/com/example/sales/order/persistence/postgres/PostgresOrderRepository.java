package com.example.sales.order.persistence.postgres;

import com.example.sales.common.api.Page;
import com.example.sales.common.config.PostgresAdapter;
import com.example.sales.order.domain.Order;
import com.example.sales.order.domain.OrderItem;
import com.example.sales.order.domain.OrderRepository;
import com.example.sales.order.domain.OrderStatus;
import com.example.sales.order.domain.PaymentResult;
import com.example.sales.order.domain.StatusChange;
import com.example.sales.user.domain.PostalAddress;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/// [OrderRepository] on Postgres. Orders are read from `v_order_summary` (items as a
/// jsonb array, flattened shipping address); the status history is a jsonb array that
/// grows with `||`; item prices are `price_tag` composites.
@PostgresAdapter
class PostgresOrderRepository implements OrderRepository {

    private static final TypeReference<List<OrderItem>> ITEMS = new TypeReference<>() { };
    private static final TypeReference<List<StatusChange>> HISTORY = new TypeReference<>() { };

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final RowMapper<Order> orderMapper = (rs, _) -> order(rs);

    PostgresOrderRepository(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public UUID create(UUID userId, PostalAddress address, BigDecimal total, String currency, List<OrderItem> items) {
        var id = jdbc.sql("""
                        insert into orders (user_id, total, currency, shipping_address, status_history)
                        values (:userId, :total, :currency,
                                row(:line1, :line2, :city, :region, :postalCode, :countryCode)::postal_address,
                                jsonb_build_array(jsonb_build_object('status', 'PENDING', 'at', now(), 'reason', 'Order placed')))
                        returning id""")
                .param("userId", userId)
                .param("total", total)
                .param("currency", currency)
                .param("line1", address.line1())
                .param("line2", address.line2(), Types.VARCHAR)
                .param("city", address.city())
                .param("region", address.region(), Types.VARCHAR)
                .param("postalCode", address.postalCode())
                .param("countryCode", address.countryCode())
                .query(UUID.class)
                .single();
        for (var item : items) {
            jdbc.sql("""
                            insert into order_items (order_id, product_id, product_name, unit_price, quantity)
                            values (:orderId, :productId, :productName, row(:unitPrice, :currency)::price_tag, :quantity)""")
                    .param("orderId", id)
                    .param("productId", item.productId())
                    .param("productName", item.productName())
                    .param("unitPrice", item.unitPrice())
                    .param("currency", item.currency())
                    .param("quantity", item.quantity())
                    .update();
        }
        return id;
    }

    @Override
    public Optional<Order> findById(UUID id) {
        return jdbc.sql("select * from v_order_summary where id = :id")
                .param("id", id)
                .query(orderMapper)
                .optional();
    }

    @Override
    public Page<Order> find(UUID userId, int page, int size) {
        var where = userId == null ? "" : " where user_id = :userId";
        var total = jdbc.sql("select count(*) from orders" + where)
                .params(userFilter(userId))
                .query(Long.class)
                .single();
        var orders = jdbc.sql("select * from v_order_summary" + where + " order by created_at desc limit :size offset :offset")
                .params(userFilter(userId))
                .param("size", size)
                .param("offset", (long) page * size)
                .query(orderMapper)
                .list();
        return Page.of(orders, page, size, total);
    }

    @Override
    public Optional<OrderStatus> lockStatus(UUID id) {
        return jdbc.sql("select status from orders where id = :id for update")
                .param("id", id)
                .query((rs, _) -> OrderStatus.valueOf(rs.getString("status")))
                .optional();
    }

    @Override
    public void changeStatus(UUID id, OrderStatus status, String reason) {
        jdbc.sql("""
                        update orders
                           set status = :status,
                               status_history = status_history
                                   || jsonb_build_object('status', :status, 'at', now(), 'reason', :reason),
                               updated_at = now()
                         where id = :id""")
                .param("id", id)
                .param("status", status.name())
                .param("reason", reason)
                .update();
    }

    @Override
    public void savePayment(UUID orderId, BigDecimal amount, PaymentResult result) {
        var status = switch (result) {
            case PaymentResult.Approved _ -> "APPROVED";
            case PaymentResult.Declined _ -> "DECLINED";
        };
        var reference = result instanceof PaymentResult.Approved(var ref, _) ? ref : null;
        jdbc.sql("""
                        insert into payments (order_id, amount, status, reference, gateway_response)
                        values (:orderId, :amount, :status, :reference, cast(:response as jsonb))""")
                .param("orderId", orderId)
                .param("amount", amount)
                .param("status", status)
                .param("reference", reference, Types.VARCHAR)
                .param("response", json.writeValueAsString(result.gatewayResponse()))
                .update();
    }

    private static Map<String, Object> userFilter(UUID userId) {
        return userId == null ? Map.of() : Map.of("userId", userId);
    }

    private Order order(ResultSet rs) throws SQLException {
        return new Order(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("username"),
                OrderStatus.valueOf(rs.getString("status")),
                rs.getBigDecimal("total"),
                rs.getString("currency"),
                new PostalAddress(rs.getString("ship_line1"), rs.getString("ship_line2"), rs.getString("ship_city"),
                        rs.getString("ship_region"), rs.getString("ship_postal_code"), rs.getString("ship_country_code")),
                json.readValue(rs.getString("items"), ITEMS),
                json.readValue(rs.getString("status_history"), HISTORY),
                rs.getObject("created_at", OffsetDateTime.class).toInstant(),
                rs.getObject("updated_at", OffsetDateTime.class).toInstant());
    }
}
