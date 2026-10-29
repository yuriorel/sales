package com.example.sales.db;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// Exercises the PostgreSQL features of the schema: domains, composite types,
/// arrays, jsonb, views and their indexes. Each test runs in a rolled-back transaction.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaConstraintsIT {

    static final String FIXTURES = """
            insert into categories (id, slug, name) values (9001, 'test-devices', 'Test Devices');

            insert into products (id, sku, category_id, name, brand, description, price, rating,
                                  tags, image_urls, dimensions, attributes, user_guide, guide_sections)
            values (9001, 'TEST-0001', 9001, 'Test Phone', 'Acme', 'A phone', 199.99, 4.5,
                    '{phone,android}', '{https://img.example/1.png}', row(7.1, 15.0, 0.8)::dimensions_cm,
                    '{"color": "black", "warranty": "1 year"}', 'Charge the phone before first use.', '{Overview,Setup}'),
                   (9002, 'TEST-0002', 9001, 'Test Tablet', 'Acme', 'A tablet', 399.00, 4.0,
                    '{tablet,android}', '{}', null,
                    '{"color": "silver"}', 'Hold the power button for three seconds.', '{Overview}');

            insert into inventory (product_id, quantity) values (9001, 10), (9002, 0);

            insert into users (id, username, email, password_hash, roles, default_address)
            values ('01900000-0000-7000-8000-000000000001', 'test-user', 'Test.User@Example.com', 'hash',
                    '{CUSTOMER}', row('1 Main St', null, 'Springfield', 'IL', '62701', 'US')::postal_address);

            insert into orders (id, user_id, total, currency, shipping_address, status_history)
            values ('01900000-0000-7000-8000-000000000002', '01900000-0000-7000-8000-000000000001', 399.98, 'USD',
                    row('1 Main St', null, 'Springfield', 'IL', '62701', 'US')::postal_address,
                    '[{"status": "PENDING", "at": "2026-10-29T10:00:00Z"}]');

            insert into order_items (order_id, product_id, product_name, unit_price, quantity)
            values ('01900000-0000-7000-8000-000000000002', 9001, 'Test Phone', row(199.99, 'USD')::price_tag, 2);
            """;

    @Autowired
    JdbcClient jdbc;

    @BeforeEach
    void insertFixtures() {
        jdbc.sql(FIXTURES).update();
    }

    @Test
    void productCatalogViewJoinsCategoryAndStock() {
        var row = jdbc.sql("""
                        select category_slug, stock, in_stock, width_cm
                        from v_product_catalog where id = 9001
                        """)
                .query().singleRow();

        assertThat(row)
                .containsEntry("category_slug", "test-devices")
                .containsEntry("stock", 10)
                .containsEntry("in_stock", true);
        assertThat(row.get("width_cm")).hasToString("7.10");
    }

    @Test
    void orderSummaryViewAggregatesItemsAsJson() {
        var row = jdbc.sql("""
                        select username, ship_city, status, items::text as items
                        from v_order_summary where id = '01900000-0000-7000-8000-000000000002'
                        """)
                .query().singleRow();

        assertThat(row)
                .containsEntry("username", "test-user")
                .containsEntry("ship_city", "Springfield")
                .containsEntry("status", "PENDING");
        assertThat((String) row.get("items")).contains("\"productName\": \"Test Phone\"", "\"quantity\": 2");
    }

    @Test
    void categoryStatsViewCollectsDistinctTags() {
        var row = jdbc.sql("""
                        select product_count, total_stock, array_to_string(tags, ',') as tags, summary::text as summary
                        from v_category_stats where category_id = 9001
                        """)
                .query().singleRow();

        assertThat(row)
                .containsEntry("product_count", 2L)
                .containsEntry("total_stock", 10L)
                .containsEntry("tags", "android,phone,tablet");
        assertThat((String) row.get("summary")).contains("\"productCount\": 2", "\"tagCount\": 3");
    }

    @ParameterizedTest(name = "{0} is rejected by {1}")
    @CsvSource(delimiter = '|', textBlock = """
            bad email           | email_address | insert into users (username, email, password_hash) values ('bad', 'not-an-email', 'hash')
            negative price      | money_amount  | update products set price = -1 where id = 9001
            lower-case currency | currency_code | update products set currency = 'usd' where id = 9001
            invalid sku         | sku_code      | update products set sku = 'bad sku' where id = 9001
            rating above five   | rating        | update products set rating = 5.5 where id = 9001
            unknown status      | order_status  | update orders set status = 'LOST'
            zero quantity       | positive_qty  | update order_items set quantity = 0
            """)
    void domainsRejectInvalidValues(String description, String domain, String sql) {
        assertThatThrownBy(() -> jdbc.sql(sql).update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("domain " + domain);
    }

    @Test
    void postalAddressCompositeRoundTrips() {
        jdbc.sql("""
                        update users
                        set default_address = row(:line1, :line2, :city, :region, :postalCode, :country)::postal_address
                        where username = 'test-user'
                        """)
                .param("line1", "221B Baker Street")
                .param("line2", "Flat B")
                .param("city", "London")
                .param("region", "Greater London")
                .param("postalCode", "NW1 6XE")
                .param("country", "GB")
                .update();

        var address = jdbc.sql("""
                        select (default_address).line1, (default_address).line2, (default_address).city,
                               (default_address).region, (default_address).postal_code, (default_address).country_code
                        from users where email = 'test.user@example.com'
                        """)
                .query((rs, _) -> new Address(rs.getString(1), rs.getString(2), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getString(6)))
                .single();

        assertThat(address).isEqualTo(
                new Address("221B Baker Street", "Flat B", "London", "Greater London", "NW1 6XE", "GB"));
    }

    @Test
    void tagFilterWithAnyOverlapUsesGinIndex() {
        var sql = "select id from products where tags && array['phone', 'tablet'] order by id";

        assertThat(jdbc.sql(sql).query(Long.class).list()).containsExactly(9001L, 9002L);
        assertThat(plan(sql)).contains("products_tags_idx");
    }

    @Test
    void tagFilterWithContainsAllUsesGinIndex() {
        var sql = "select id from products where tags @> array['android', 'tablet']";

        assertThat(jdbc.sql(sql).query(Long.class).list()).containsExactly(9002L);
        assertThat(plan(sql)).contains("products_tags_idx");
    }

    @Test
    void attributeContainmentUsesGinIndex() {
        var sql = "select id from products where attributes @> '{\"color\": \"black\"}'";

        assertThat(jdbc.sql(sql).query(Long.class).list()).containsExactly(9001L);
        assertThat(plan(sql)).contains("products_attributes_idx");
    }

    /// Returns the query plan with sequential scans disabled, so the tiny fixture
    /// tables still show which index the planner can use.
    String plan(String sql) {
        jdbc.sql("set local enable_seqscan = off").update();
        return String.join("\n", jdbc.sql("explain " + sql).query(String.class).list());
    }

    record Address(String line1, String line2, String city, String region, String postalCode, String country) {
    }
}
